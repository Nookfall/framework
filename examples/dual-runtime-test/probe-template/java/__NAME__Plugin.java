package __PKG__;

import com.nookfall.framework.Nookfall;
import com.nookfall.framework.NookfallRuntime;
import java.time.Duration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class __NAME__Plugin extends JavaPlugin {

    private NookfallRuntime runtime;
    private int runs;

    @Override
    public void onEnable() {
        runs++;
        runtime = Nookfall.start(this);
        int run = runs;
        runtime.tasks().global().later(Duration.ofSeconds(3), () -> runtime.get(ProbeRunner.class).run(run));
    }

    @Override
    public void onDisable() {
        String tasks = "n/a";
        if (runtime != null) {
            runtime.close();
            tasks = Integer.toString(runtime.tasks().activeTasks());
        }
        Results.append(this, "DISABLE runtime=" + (runtime != null) + " listeners="
                + HandlerList.getRegisteredListeners(this).size() + " tasks=" + tasks);
        runtime = null;
    }
}
