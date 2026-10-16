package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a subcommand handler. Use spaces for nested literals, for example {@code "admin reload"}. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Subcommand {
    String value();

    /** Short description shown in help listings. */
    String description() default "";
}
