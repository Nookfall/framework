package com.nookfall.framework.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * On a list, set or map field: entries that cannot be read (a material or entity type this Minecraft version does not
 * have, say) are skipped with a warning instead of making the whole file invalid. Use it where files are shared between
 * servers on different versions, or generated from one version and run on another. Mistakes inside an entry that was read
 * are still errors.
 */
@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Lenient {}
