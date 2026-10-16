package com.nookfall.framework.spi;

import com.nookfall.framework.task.Execution;

/** A generated {@code @Scheduled} method registration. */
public record ScheduledSpec<T>(
        Class<T> type, String method, String every, String initialDelay, Execution execution, Invoker<T> invoker) {

    @FunctionalInterface
    public interface Invoker<T> {
        void run(T target) throws Exception;
    }
}
