package com.nookfall.framework.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes one constant of an enum used in a configuration. The generated file lists the options of every enum field
 * (and list of enums) above its key, and adds this text for each option that has it:
 *
 * <pre>
 * # Where the symbol goes.
 * # Options:
 * #   PREFIX: before the number ($10)
 * #   SUFFIX: after the number (10$)
 * symbol-position: PREFIX
 * </pre>
 */
@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Describe {
    String value();
}
