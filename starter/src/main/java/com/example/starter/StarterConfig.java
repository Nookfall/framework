package com.example.starter;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.Config;
import com.nookfall.framework.constraint.Range;
import com.nookfall.framework.storage.DatabaseSettings;

@Config(value = "config.yml", header = "Starter plugin configuration")
public class StarterConfig {

    @Comment("Greet players when they join.")
    private boolean greetOnJoin = true;

    @Comment("How many recent visitors /starter visitors shows.")
    @Range(min = 1, max = 50)
    private int visitorsShown = 10;

    @Comment("How often the visit summary is logged, for example 30m.")
    private Summary summary = new Summary();

    @Comment("Database connection.")
    private DatabaseSettings database = new DatabaseSettings();

    public static class Summary {
        private String interval = "30m";
    }

    public boolean greetOnJoin() {
        return greetOnJoin;
    }

    public int visitorsShown() {
        return visitorsShown;
    }

    public DatabaseSettings database() {
        return database;
    }
}
