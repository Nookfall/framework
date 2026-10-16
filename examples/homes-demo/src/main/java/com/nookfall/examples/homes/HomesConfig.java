package com.nookfall.examples.homes;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.Config;
import com.nookfall.framework.constraint.Range;
import com.nookfall.framework.storage.DatabaseSettings;
import java.time.Duration;

@Config(value = "config.yml", header = "NookfallHomes configuration")
public class HomesConfig {

    @Comment({"Maximum number of homes per player.", "Players with homes.limit.<n> may have up to <n> homes."})
    @Range(min = 1, max = 100)
    private int maxHomes = 3;

    @Comment("Delay before teleporting. Moving cancels the teleport when cancel-on-move is true.")
    private Duration teleportDelay = Duration.ofSeconds(3);

    private boolean cancelOnMove = true;

    @Comment("Look for a safe spot near the home before teleporting.")
    private boolean safeTeleport = true;

    @Comment("Database connection.")
    private DatabaseSettings database = new DatabaseSettings();

    private Cleanup cleanup = new Cleanup();

    public static class Cleanup {
        @Comment("How often homes in deleted worlds are removed, for example 10m or 1h.")
        private String interval = "10m";
    }

    public int maxHomes() {
        return maxHomes;
    }

    public Duration teleportDelay() {
        return teleportDelay;
    }

    public boolean cancelOnMove() {
        return cancelOnMove;
    }

    public boolean safeTeleport() {
        return safeTeleport;
    }

    public DatabaseSettings database() {
        return database;
    }
}
