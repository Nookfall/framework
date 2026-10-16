package __PKG__;

import com.nookfall.framework.task.Execution;
import com.nookfall.framework.task.Scheduled;
import org.bukkit.Bukkit;

public final class TickJob {
    private final ProbeState state;

    public TickJob(ProbeState state) {
        this.state = state;
    }

    @Scheduled(every = "${tick.interval}")
    void tick() {
        state.ticks.incrementAndGet();
        if (!Bukkit.isPrimaryThread()) {
            state.ticksOnMainThread.set(false);
        }
    }

    @Scheduled(every = "500ms", initialDelay = "0s", execution = Execution.ASYNC)
    void async() {
        state.asyncRuns.incrementAndGet();
        if (Bukkit.isPrimaryThread()) {
            state.asyncOffMainThread.set(false);
        }
    }
}
