package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks the method run when the command is used without a subcommand. */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Execute {}
