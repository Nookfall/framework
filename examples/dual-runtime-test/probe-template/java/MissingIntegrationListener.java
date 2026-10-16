package __PKG__;

import com.nookfall.framework.listener.ListenerComponent;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@ListenerComponent(requiresPlugins = "DefinitelyMissingPlugin")
public final class MissingIntegrationListener implements Listener {
    static final AtomicInteger CONSTRUCTED = new AtomicInteger();

    public MissingIntegrationListener() {
        CONSTRUCTED.incrementAndGet();
    }

    @EventHandler
    public void onProbe(ProbeEvent event) {}
}
