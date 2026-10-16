package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Makes the last {@code String} argument consume the rest of the input. */
@Documented
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Greedy {}
