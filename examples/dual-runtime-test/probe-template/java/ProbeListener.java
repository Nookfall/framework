package __PKG__;

import com.nookfall.framework.listener.ListenerComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@ListenerComponent
public final class ProbeListener implements Listener {
    private final ProbeState state;

    public ProbeListener(ProbeState state) {
        this.state = state;
    }

    @EventHandler
    public void onProbe(ProbeEvent event) {
        state.listenerHits.incrementAndGet();
    }
}
