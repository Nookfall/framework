package com.nookfall.framework.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Uses a custom codec. On a field it applies to that field's value; on a class it applies wherever the class is used
 * in configuration, including inside lists and maps. The codec class needs a no-argument constructor.
 */
@Documented
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Codec {
    @SuppressWarnings("rawtypes")
    Class<? extends ConfigCodec> value();
}
