package com.nookfall.framework.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Comment lines written above a configuration key. On a field it comments that key. On a class it is the default comment
 * for every field of that type that has no comment of its own, so a reusable section (such as the storage settings) can
 * carry its own documentation.
 */
@Documented
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Comment {
    String[] value();
}
