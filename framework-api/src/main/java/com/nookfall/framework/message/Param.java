package com.nookfall.framework.message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Overrides the placeholder name of a {@link Messages} method parameter. By default {@code homeName} becomes {@code <home_name>}. */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Param {
    String value();
}
