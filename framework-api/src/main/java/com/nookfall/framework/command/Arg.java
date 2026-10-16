package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a command argument. Supported types are documented in the commands guide; other types need an {@link ArgumentResolver}. */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Arg {
    /** Argument name shown in usage. */
    String value();

    /** Name of a {@link Suggestions} method in the same class that provides tab completions. */
    String suggestions() default "";

    /** Whether the argument may be omitted. Optional arguments must come after required ones; omitted values are {@code null}. */
    boolean optional() default false;
}
