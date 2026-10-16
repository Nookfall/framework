package com.nookfall.framework.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a no-argument method of a bean repeatedly while the plugin is enabled.
 *
 * <p>Durations use the form {@code 30s}, {@code 5m}, {@code 1h30m} or {@code 250ms}. A value such as
 * {@code ${cleanup.interval}} is read from configuration and the task is rescheduled when that configuration is
 * reloaded. Use {@code ${file.yml:path}} when several configuration files exist.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Scheduled {

    /** Period between runs. */
    String every();

    /** Delay before the first run. Defaults to the period. */
    String initialDelay() default "";

    Execution execution() default Execution.GLOBAL;
}
