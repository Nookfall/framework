package com.nookfall.framework.task;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Plugin-owned task scheduling with explicit execution contexts. All tasks are cancelled when the plugin's runtime
 * closes.
 */
public interface Tasks {

    /** The global region (the main thread on Paper). */
    TaskScope global();

    /** Background threads. */
    TaskScope async();

    /**
     * The thread that owns the entity. Tasks stop silently when the entity is removed, including when a player
     * disconnects.
     */
    TaskScope entity(Entity entity);

    /** Like {@link #entity(Entity)} but runs {@code retired} if the entity is removed before a task runs. */
    TaskScope entity(Entity entity, Runnable retired);

    /** The thread that owns the region containing the location. */
    TaskScope region(Location location);

    TaskScope region(World world, int chunkX, int chunkZ);

    /** Number of tasks currently scheduled and not finished. */
    int activeTasks();

    void cancelAll();
}
