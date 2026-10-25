package com.nookfall.framework.internal.inject;

import io.avaje.inject.BeanScopeBuilder;
import io.avaje.inject.spi.InjectPlugin;
import java.lang.reflect.Type;

/**
 * Tells Avaje's annotation processor which beans the Nookfall runtime supplies, so injecting them does not fail
 * compile-time wiring validation. It is registered as a service by the Nookfall processor jar; Avaje then lists it in
 * each plugin's generated service file, so it must also exist at runtime, where {@link #apply} does nothing because
 * the runtime supplies the instances through {@code BeanScopeBuilder.bean(...)}.
 */
public final class NookfallInjectPlugin implements InjectPlugin {

    private static final String[] PROVIDED = {
        "org.bukkit.plugin.Plugin",
        "org.bukkit.plugin.java.JavaPlugin",
        "org.bukkit.Server",
        "java.util.logging.Logger",
        "com.nookfall.framework.NookfallRuntime",
        "com.nookfall.framework.task.Tasks",
        "com.nookfall.framework.message.Localization",
        "com.nookfall.framework.config.ConfigService",
        "com.nookfall.framework.gui.GuiManager",
        "com.nookfall.framework.integration.Integrations",
    };

    @Override
    public void apply(BeanScopeBuilder builder) {
        // Instances are supplied by the runtime.
    }

    @Override
    public Type[] provides() {
        Type[] types = new Type[PROVIDED.length];
        for (int i = 0; i < PROVIDED.length; i++) {
            String name = PROVIDED[i];
            // Names only: the processor path does not contain the Paper API.
            types[i] = new Type() {
                @Override
                public String getTypeName() {
                    return name;
                }
            };
        }
        return types;
    }
}
