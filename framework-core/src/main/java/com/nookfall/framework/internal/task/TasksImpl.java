package com.nookfall.framework.internal.task;

import com.nookfall.framework.task.TaskHandle;
import com.nookfall.framework.task.TaskScope;
import com.nookfall.framework.task.Tasks;
import com.nookfall.framework.util.time.Durations;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Plugin-owned scheduling on Paper's region-aware schedulers. Every task is tracked until it finishes so that
 * {@link #cancelAll()} leaves nothing behind.
 */
public final class TasksImpl implements Tasks {

    private final Plugin plugin;
    private final Logger logger;
    private final Set<Handle> active = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public TasksImpl(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public TaskScope global() {
        return new Scope("global", null) {
            @Override
            ScheduledTask schedule(Consumer<ScheduledTask> body, Runnable onRetired, long delayMs, long periodMs) {
                var scheduler = Bukkit.getGlobalRegionScheduler();
                if (periodMs > 0) {
                    return scheduler.runAtFixedRate(plugin, body, ticks(delayMs), ticks(periodMs));
                }
                return delayMs <= 0 ? scheduler.run(plugin, body) : scheduler.runDelayed(plugin, body, ticks(delayMs));
            }
        };
    }

    @Override
    public TaskScope async() {
        return new Scope("async", null) {
            @Override
            ScheduledTask schedule(Consumer<ScheduledTask> body, Runnable onRetired, long delayMs, long periodMs) {
                var scheduler = Bukkit.getAsyncScheduler();
                if (periodMs > 0) {
                    return scheduler.runAtFixedRate(
                            plugin, body, Math.max(1, delayMs), Math.max(1, periodMs), TimeUnit.MILLISECONDS);
                }
                return delayMs <= 0
                        ? scheduler.runNow(plugin, body)
                        : scheduler.runDelayed(plugin, body, delayMs, TimeUnit.MILLISECONDS);
            }
        };
    }

    @Override
    public TaskScope entity(Entity entity) {
        return entity(entity, null);
    }

    @Override
    public TaskScope entity(Entity entity, Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        return new Scope("entity " + entity.getUniqueId(), retired) {
            @Override
            ScheduledTask schedule(Consumer<ScheduledTask> body, Runnable onRetired, long delayMs, long periodMs) {
                var scheduler = entity.getScheduler();
                if (periodMs > 0) {
                    return scheduler.runAtFixedRate(plugin, body, onRetired, ticks(delayMs), ticks(periodMs));
                }
                return delayMs <= 0
                        ? scheduler.run(plugin, body, onRetired)
                        : scheduler.runDelayed(plugin, body, onRetired, ticks(delayMs));
            }
        };
    }

    @Override
    public TaskScope region(Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return region(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public TaskScope region(World world, int chunkX, int chunkZ) {
        return new Scope("region " + world.getName() + " " + chunkX + "," + chunkZ, null) {
            @Override
            ScheduledTask schedule(Consumer<ScheduledTask> body, Runnable onRetired, long delayMs, long periodMs) {
                var scheduler = Bukkit.getRegionScheduler();
                if (periodMs > 0) {
                    return scheduler.runAtFixedRate(
                            plugin, world, chunkX, chunkZ, body, ticks(delayMs), ticks(periodMs));
                }
                return delayMs <= 0
                        ? scheduler.run(plugin, world, chunkX, chunkZ, body)
                        : scheduler.runDelayed(plugin, world, chunkX, chunkZ, body, ticks(delayMs));
            }
        };
    }

    @Override
    public int activeTasks() {
        return active.size();
    }

    @Override
    public void cancelAll() {
        closed = true;
        for (Handle handle : Set.copyOf(active)) {
            handle.cancel();
        }
        active.clear();
    }

    private static long ticks(long millis) {
        return Durations.toTicks(Duration.ofMillis(millis), 1);
    }

    /** Shared scheduling logic; subclasses map onto one Paper scheduler. */
    private abstract class Scope implements TaskScope {

        private final String name;
        private final Runnable retiredCallback;

        Scope(String name, Runnable retiredCallback) {
            this.name = name;
            this.retiredCallback = retiredCallback;
        }

        /** Returns null if the task could not be scheduled (for example, a removed entity). */
        abstract ScheduledTask schedule(Consumer<ScheduledTask> body, Runnable onRetired, long delayMs, long periodMs);

        void retired() {
            if (retiredCallback != null) {
                try {
                    retiredCallback.run();
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Retired callback of " + name + " task failed", t);
                }
            }
        }

        @Override
        public TaskHandle run(Runnable task) {
            return submit(task, 0, 0);
        }

        @Override
        public TaskHandle later(Duration delay, Runnable task) {
            return submit(task, nonNegative(delay), 0);
        }

        @Override
        public TaskHandle timer(Duration initialDelay, Duration period, Runnable task) {
            long periodMs = period.toMillis();
            if (periodMs <= 0) {
                throw new IllegalArgumentException("period must be positive");
            }
            return submit(task, nonNegative(initialDelay), periodMs);
        }

        @Override
        public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
            CompletableFuture<T> future = new CompletableFuture<>();
            Handle handle = submit(
                    () -> {
                        try {
                            future.complete(supplier.get());
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    },
                    0,
                    0);
            handle.onAbandoned =
                    () -> future.completeExceptionally(new CancellationException(name + " task was cancelled"));
            if (handle.isDone() && !future.isDone()) {
                future.completeExceptionally(new CancellationException(name + " task could not be scheduled"));
            }
            return future;
        }

        @Override
        public Executor executor() {
            return command -> submit(command, 0, 0);
        }

        private Handle submit(Runnable task, long delayMs, long periodMs) {
            Objects.requireNonNull(task, "task");
            Handle handle = new Handle(periodMs > 0);
            if (closed || !plugin.isEnabled()) {
                handle.finish(true);
                return handle;
            }
            active.add(handle);
            ScheduledTask scheduled;
            try {
                scheduled = schedule(
                        st -> {
                            if (handle.cancelled) {
                                st.cancel();
                                return;
                            }
                            try {
                                task.run();
                            } catch (Throwable t) {
                                logger.log(Level.SEVERE, "Task in " + name + " context failed", t);
                            } finally {
                                if (!handle.repeating) {
                                    handle.finish(false);
                                }
                            }
                        },
                        () -> {
                            handle.finish(true);
                            retired();
                        },
                        delayMs,
                        periodMs);
            } catch (RuntimeException e) {
                handle.finish(true);
                throw e;
            }
            if (scheduled == null) {
                // Entity already removed.
                handle.finish(true);
                retired();
            } else {
                handle.task = scheduled;
                if (handle.cancelled) {
                    scheduled.cancel();
                }
            }
            return handle;
        }

        private static long nonNegative(Duration duration) {
            long ms = duration.toMillis();
            if (ms < 0) {
                throw new IllegalArgumentException("delay must not be negative");
            }
            return ms;
        }
    }

    private final class Handle implements TaskHandle {

        final boolean repeating;
        volatile ScheduledTask task;
        volatile boolean cancelled;
        volatile boolean done;
        volatile Runnable onAbandoned;

        Handle(boolean repeating) {
            this.repeating = repeating;
        }

        void finish(boolean abandoned) {
            done = true;
            active.remove(this);
            Runnable callback = onAbandoned;
            if (abandoned && callback != null) {
                callback.run();
            }
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            ScheduledTask current = task;
            if (current != null) {
                current.cancel();
            }
            finish(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            if (!done && task != null && !repeating) {
                var state = task.getExecutionState();
                if (state == ScheduledTask.ExecutionState.CANCELLED || state == ScheduledTask.ExecutionState.FINISHED) {
                    finish(state == ScheduledTask.ExecutionState.CANCELLED);
                }
            }
            return done;
        }
    }
}
