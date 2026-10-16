package __PKG__;

import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ProbeState {
    final AtomicInteger listenerHits = new AtomicInteger();
    final AtomicInteger ticks = new AtomicInteger();
    final AtomicInteger asyncRuns = new AtomicInteger();
    final AtomicBoolean asyncOffMainThread = new AtomicBoolean(true);
    final AtomicBoolean ticksOnMainThread = new AtomicBoolean(true);
    volatile String lastEcho;
    volatile int sum;
    volatile ProbeCommand.Mode mode;
    volatile boolean secretRan;
    volatile AtomicBoolean entityRan;
    volatile AtomicBoolean retired;
    volatile AtomicBoolean regionRan;
    volatile org.bukkit.entity.ArmorStand stand;
}
