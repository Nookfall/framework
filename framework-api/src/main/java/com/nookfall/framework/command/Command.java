package com.nookfall.framework.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a Brigadier command. The class is constructed by Avaje Inject and registered when the plugin starts. */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Command {
    /** Command label, lower case, without a slash. */
    String value();

    /** Short description shown in help listings. */
    String description() default "";
}
