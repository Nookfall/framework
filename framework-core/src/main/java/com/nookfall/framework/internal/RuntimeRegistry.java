package com.nookfall.framework.internal;

import com.nookfall.framework.NookfallRuntime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtimes started with this copy of the framework. In shared mode this lists every consumer plugin; in standalone mode
 * it only contains the owning plugin.
 */
public final class RuntimeRegistry {

    private static final List<NookfallRuntime> RUNTIMES = new CopyOnWriteArrayList<>();

    private RuntimeRegistry() {}

    static void add(NookfallRuntime runtime) {
        RUNTIMES.add(runtime);
    }

    static void remove(NookfallRuntime runtime) {
        RUNTIMES.remove(runtime);
    }

    public static List<NookfallRuntime> runtimes() {
        return List.copyOf(RUNTIMES);
    }
}
