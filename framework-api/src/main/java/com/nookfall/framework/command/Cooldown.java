package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applies a per-sender cooldown to a command handler. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Cooldown {
    /** Cooldown duration, for example {@code \"5s\"} or {@code \"1m30s\"}. */
    String value();

    /** Permission that skips the cooldown. Empty for none. */
    String bypassPermission() default "";
}
