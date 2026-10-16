package com.nookfall.framework.constraint;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts a numeric configuration value or command argument to an inclusive range. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {
    double min() default Double.NEGATIVE_INFINITY;

    double max() default Double.POSITIVE_INFINITY;
}
