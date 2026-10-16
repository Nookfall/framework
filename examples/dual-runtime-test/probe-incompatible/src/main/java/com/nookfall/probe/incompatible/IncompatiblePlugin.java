package com.nookfall.probe.incompatible;

import com.nookfall.framework.Nookfall;
import com.nookfall.framework.NookfallRuntime;
import org.bukkit.plugin.java.JavaPlugin;

/** Built against a newer framework version than installed; must refuse to start with a clear message. */
public final class IncompatiblePlugin extends JavaPlugin {

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
