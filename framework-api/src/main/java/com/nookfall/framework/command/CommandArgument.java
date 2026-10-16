package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Registers an {@link ArgumentResolver} implementation so its value type can be used as a command argument. */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface CommandArgument {}
