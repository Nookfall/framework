package com.nookfall.framework.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class WriteBehindTest {

    /** A little owner: a set of dirty keys and a "database" that can fail or be held up. */
    private static final class Owner {
        final Set<String> dirty = new LinkedHashSet<>();
        final List<Set<String>> saved = new ArrayList<>();
        boolean failing;
        CompletableFuture<Void> hold;
        final Logger logger = Logger.getLogger("write-behind-test-" + System.nanoTime());

        Owner() {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.OFF);
        }

        synchronized Set<String> drain() {
            if (dirty.isEmpty()) {
                return null;
            }
            Set<String> batch = new LinkedHashSet<>(dirty);
            dirty.clear();
            return batch;
        }

        CompletableFuture<?> write(Set<String> batch) {
            if (failing) {
                return CompletableFuture.failedFuture(new IllegalStateException("down"));
            }
            CompletableFuture<Void> done = hold == null ? CompletableFuture.completedFuture(null) : hold;
            return done.thenRun(() -> saved.add(batch));
        }

        synchronized void restore(Set<String> batch) {
            dirty.addAll(batch);
        }

        synchronized boolean pending() {
            return !dirty.isEmpty();
        }

        WriteBehind<Set<String>> writer() {
            return new WriteBehind<>("test data", logger, this::drain, this::write, this::restore, this::pending);
        }
    }

    @Test
    void changesAreWrittenRightAway() {
        Owner owner = new Owner();
        var writer = owner.writer();
        owner.dirty.add("a");
        writer.request();
        assertThat(owner.saved).containsExactly(Set.of("a"));
        assertThat(writer.busy()).isFalse();
    }

    @Test
    void changesDuringAWriteJoinTheNextBatch() {
        Owner owner = new Owner();
        owner.hold = new CompletableFuture<>();
        var writer = owner.writer();
        owner.dirty.add("a");
        writer.request();
        owner.dirty.add("b");
        writer.request(); // a write is running: nothing new starts
        assertThat(owner.saved).isEmpty();
        CompletableFuture<Void> release = owner.hold;
        owner.hold = null;
        release.complete(null);
        assertThat(owner.saved).containsExactly(Set.of("a"), Set.of("b"));
    }

    @Test
    void failedBatchesAreKeptAndRetried() {
        Owner owner = new Owner();
        var writer = owner.writer();
        owner.failing = true;
        owner.dirty.add("a");
        writer.request();
        assertThat(owner.saved).isEmpty();
        assertThat(owner.dirty).containsExactly("a");
        owner.dirty.add("b");
        owner.failing = false;
        writer.retry();
        assertThat(owner.saved).containsExactly(Set.of("a", "b"));
    }

    @Test
    void closeWaitsForTheWriteInProgress() throws Exception {
        Owner owner = new Owner();
        owner.hold = new CompletableFuture<>();
        var writer = owner.writer();
        owner.dirty.add("a");
        writer.request();
        CompletableFuture<Void> closing = CompletableFuture.runAsync(writer::close);
        Thread.sleep(100);
        assertThat(closing).isNotDone();
        owner.hold.complete(null);
        closing.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(owner.saved).containsExactly(Set.of("a"));
    }

    @Test
    void aWriteThatThrowsCountsAsFailed() {
        Owner owner = new Owner();
        var writer = new WriteBehind<Set<String>>(
                "boom",
                owner.logger,
                owner::drain,
                batch -> {
                    throw new IllegalStateException("bug");
                },
                owner::restore,
                owner::pending);
        owner.dirty.add("a");
        writer.request();
        assertThat(owner.dirty).containsExactly("a");
        assertThat(writer.busy()).isTrue(); // still pending, not writing
    }
}
