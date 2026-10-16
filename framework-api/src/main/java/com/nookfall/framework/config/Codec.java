package com.nookfall.framework.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Uses a custom codec for a field. The codec class needs a public no-argument constructor. */
@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Codec {
    @SuppressWarnings("rawtypes")
    Class<? extends ConfigCodec> value();
}
