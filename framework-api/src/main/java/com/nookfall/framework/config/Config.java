package com.nookfall.framework.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a YAML configuration class. Field initializers are the defaults. Inject {@code ConfigRef<YourConfig>} to read the current snapshot. */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Config {
    /** File path relative to the plugin data folder, for example {@code \"config.yml\"}. */
    String value();

    /** Comment lines written at the top of the file. */
    String[] header() default {};
}
