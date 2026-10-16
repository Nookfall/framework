package com.nookfall.framework;

import com.nookfall.framework.internal.FrameworkVersion;
import com.nookfall.framework.internal.NookfallRuntimeImpl;
import com.nookfall.framework.spi.PluginIndex;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point. Call {@link #start(JavaPlugin)} from {@code onEnable} and close the returned runtime in
 * {@code onDisable}.
 */
public final class Nookfall {

    private Nookfall() {}

    /**
     * Starts the framework for a plugin: loads configuration and translations, constructs beans, registers commands,
     * listeners and GUIs and schedules {@code @Scheduled} methods.
     *
     * @throws NookfallStartupException if anything fails; everything started so far is released first
     */
    public static NookfallRuntime start(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PluginIndex index = index(plugin);
        if (!FrameworkVersion.compatible(index.frameworkVersion(), FrameworkVersion.CURRENT)) {
            throw new NookfallStartupException(plugin.getName() + " was built for Nookfall Framework "
                    + index.frameworkVersion() + " but version " + FrameworkVersion.CURRENT
                    + " is installed. Install a NookfallFramework plugin compatible with "
                    + index.frameworkVersion() + " (same minor version, same or newer patch).");
        }
        NookfallRuntimeImpl runtime = new NookfallRuntimeImpl(plugin, index);
        try {
            runtime.start();
            return runtime;
        } catch (Throwable t) {
            runtime.close();
            if (t instanceof NookfallStartupException startup) {
                throw startup;
            }
            String reason = t.getMessage() == null ? t.toString() : t.getMessage();
            throw new NookfallStartupException(plugin.getName() + " could not start: " + reason, t);
        }
    }

    /** Version of the framework classes that are currently loaded. */
    public static String version() {
        return FrameworkVersion.CURRENT;
    }

    private static PluginIndex index(JavaPlugin plugin) {
        String name = plugin.getClass().getName() + "_NookfallIndex";
        try {
            Class<?> type = Class.forName(name, true, plugin.getClass().getClassLoader());
            return (PluginIndex) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new NookfallStartupException("No generated " + name + " was found. Make sure the Nookfall annotation "
                    + "processor (com.nookfall:framework-processor) is on the annotationProcessor path.");
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new NookfallStartupException("Cannot load " + name, e);
        }
    }
}
