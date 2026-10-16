package com.nookfall.framework.listener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Registers a Bukkit {@code Listener} constructed by Avaje Inject. Handlers use standard {@code @EventHandler} methods. */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface ListenerComponent {
    /**
     * Plugins that must be enabled for this listener to be constructed and registered. Use this for listeners that
     * reference optional integrations.
     */
    String[] requiresPlugins() default {};
}
