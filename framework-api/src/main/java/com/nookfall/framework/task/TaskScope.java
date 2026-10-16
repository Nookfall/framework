package com.nookfall.framework.task;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Schedules work in one execution context. Durations are rounded up to whole ticks for tick-based contexts. Exceptions
 * thrown by tasks are logged and do not cancel repeating tasks.
 */
public interface TaskScope {

    TaskHandle run(Runnable task);

    TaskHandle later(Duration delay, Runnable task);

    TaskHandle timer(Duration initialDelay, Duration period, Runnable task);

    default TaskHandle every(Duration period, Runnable task) {
        return timer(period, period, task);
    }

    /** Runs the supplier in this context and completes the future with its result. */
    <T> CompletableFuture<T> supply(Supplier<T> supplier);

    /** An executor that runs work in this context, for use with {@code CompletableFuture} stages. */
    Executor executor();
}
