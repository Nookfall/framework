package com.nookfall.framework.task;

/** Where a {@link Scheduled} method runs. */
public enum Execution {
    /** The global region thread (the main thread on Paper). Safe for most Bukkit API calls. */
    GLOBAL,
    /** A background thread. Do not touch worlds, entities or inventories from here. */
    ASYNC
}
