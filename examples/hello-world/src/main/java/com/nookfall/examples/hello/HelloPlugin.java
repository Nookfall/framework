package com.nookfall.examples.hello;

import com.nookfall.framework.Nookfall;
import com.nookfall.framework.NookfallRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public final class HelloPlugin extends JavaPlugin {

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
