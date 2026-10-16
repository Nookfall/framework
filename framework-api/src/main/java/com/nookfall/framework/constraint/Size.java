package com.nookfall.framework.constraint;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts the length of a string or the size of a list or map. */
@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Size {
    int min() default 0;

    int max() default Integer.MAX_VALUE;
}
