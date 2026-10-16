package com.nookfall.framework.plugin;

import com.nookfall.framework.Nookfall;
import com.nookfall.framework.NookfallRuntime;
import org.bukkit.plugin.java.JavaPlugin;

/** The NookfallFramework plugin: provides the framework classes to shared-mode plugins. */
public final class NookfallFrameworkPlugin extends JavaPlugin {

    private NookfallRuntime runtime;

    @Override
    public void onEnable() {
        runtime = Nookfall.start(this);
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.close();
        }
    }
}
