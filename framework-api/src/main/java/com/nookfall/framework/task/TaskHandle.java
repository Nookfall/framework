package com.nookfall.framework.task;

/** A scheduled task owned by a plugin. */
public interface TaskHandle {

    void cancel();

    boolean isCancelled();

    /** True once a one-shot task has run, or the task was cancelled or retired. */
    boolean isDone();
}
