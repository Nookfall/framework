package com.nookfall.framework.message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a typed message interface. An implementation is generated and can be injected. */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Messages {
    /** Key prefix, for example {@code \"homes\"} makes method {@code created} use key {@code homes.created}. Empty for none. */
    String value() default "";
}
