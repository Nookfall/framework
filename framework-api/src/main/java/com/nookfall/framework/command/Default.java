package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Makes an argument optional with the given default value, parsed like user input. */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Default {
    String value();
}
