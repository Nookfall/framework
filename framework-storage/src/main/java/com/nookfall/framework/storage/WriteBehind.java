package com.nookfall.framework.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps data that lives in memory saved in the background: changes are collected into batches and written as soon as
 * possible, one write at a time. Writes that happen while another is running join the next batch, a failed batch is put
 * back and retried, and {@link #close()} waits for everything to be written. The state itself (and its locking) stays with
 * the owner; this class only runs the loop.
 *
 * <p>Usage: after changing something, mark it dirty in your own structure and call {@link #request()}. Call
 * {@link #retry()} now and then (for example from a {@code @Scheduled} method) so failed writes are tried again.
 *
 * @param <B> a batch of changes, produced by the owner and consumed by the write
 */
public final class WriteBehind<B> implements AutoCloseable {

    private final String name;
    private final Logger logger;
    private final Supplier<B> drain;
    private final Function<B, CompletableFuture<?>> write;
    private final Consumer<B> restore;
    private final BooleanSupplier pending;

    private final AtomicBoolean writing = new AtomicBoolean();
    private volatile CompletableFuture<Void> inFlight = CompletableFuture.completedFuture(null);

    /**
     * @param name what is being saved, for log messages
     * @param drain takes everything that changed out of the owner's state as a batch, or returns null if nothing did; may
     *     run on any thread
     * @param write saves a batch; may complete on any thread
     * @param restore marks a batch that could not be saved as changed again
     * @param pending whether anything changed and has not been drained
     */
    public WriteBehind(
            String name,
            Logger logger,
            Supplier<B> drain,
            Function<B, CompletableFuture<?>> write,
            Consumer<B> restore,
            BooleanSupplier pending) {
        this.name = name;
        this.logger = logger;
        this.drain = drain;
        this.write = write;
        this.restore = restore;
        this.pending = pending;
    }

    /** Starts writing what changed, unless a write is running; that one continues with the rest when it is done. */
    public void request() {
        if (!writing.compareAndSet(false, true)) {
            return;
        }
        B batch = drain.get();
        if (batch == null) {
            writing.set(false);
            if (pending.getAsBoolean()) {
                request();
            }
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        inFlight = done;
        CompletableFuture<?> result;
        try {
            result = write.apply(batch);
        } catch (RuntimeException e) {
            result = CompletableFuture.failedFuture(e);
        }
        result.whenComplete((ignored, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not save " + name + "; it will be retried", error);
                restore.accept(batch);
            }
            writing.set(false);
            done.complete(null);
            if (error == null && pending.getAsBoolean()) {
                request();
            }
        });
    }

    /** Tries again if something is still unsaved, for example after a failed write. */
    public void retry() {
        if (pending.getAsBoolean()) {
            request();
        }
    }

    /** Whether a write is running or something is waiting to be written. */
    public boolean busy() {
        return writing.get() || pending.getAsBoolean();
    }

    /** Waits (up to about a minute) until everything is written. Safe to call from a server thread. */
    @Override
    public void close() {
        try {
            for (int attempt = 0; attempt < 12 && busy(); attempt++) {
                request();
                inFlight.get(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not wait for " + name + " to be saved", e);
        }
        if (pending.getAsBoolean()) {
            logger.severe("Some changes to " + name + " could not be saved.");
        }
    }
}
