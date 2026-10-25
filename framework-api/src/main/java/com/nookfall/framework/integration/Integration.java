package com.nookfall.framework.integration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares support for another plugin. The annotated class implements an interface of <em>your</em> plugin, which the rest
 * of the code uses, and is the only place that touches the other plugin's classes. It is constructed (by Avaje, so it can
 * inject beans) only when all named plugins are enabled, and only on first use, so the other plugin's classes are never
 * loaded when it is missing.
 *
 * <pre>{@code
 * public interface SellHook { Sale sell(Player player, Map<Material, Integer> items); }
 *
 * @Integration(value = SellHook.class, plugins = "NookfallSellwand")
 * public final class SellwandHook implements SellHook { ... uses SellApi ... }
 *
 * // elsewhere
 * SellHook sell = integrations.get(SellHook.class, SellHook.NONE);
 * }</pre>
 *
 * <p>Add the plugin to {@code softDepend} so it enables first. A class that fails to link or construct is logged once and
 * treated as unavailable.
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Integration {

    /** The type the integration is offered as; the annotated class must implement or extend it. */
    Class<?> value();

    /** Plugins that must all be enabled. */
    String[] plugins();
}
