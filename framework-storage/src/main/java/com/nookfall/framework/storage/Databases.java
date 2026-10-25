package com.nookfall.framework.storage;

import java.nio.file.Path;
import java.util.List;
import org.bukkit.plugin.Plugin;

/** The usual way to start the storage of a plugin: open the configured database and bring its schema up to date. */
public final class Databases {

    private Databases() {}

    /**
     * Opens the database described by the settings and applies the migrations. If a migration fails the database is
     * closed again before the exception is thrown, so a failed start leaves nothing open.
     *
     * <p>Typically used in a bean factory with {@code @Bean(destroyMethod = "close")}:
     *
     * <pre>{@code
     * Database database(Plugin plugin, ConfigRef<MyConfig> config) {
     *     return Databases.open(plugin, config.get().database(), Migration.discover(MyPlugin.class, "db/migration"));
     * }
     * }</pre>
     */
    public static Database open(Plugin plugin, DatabaseSettings settings, List<Migration> migrations) {
        return migrated(Database.create(plugin, settings), migrations);
    }

    /** Like {@link #open(Plugin, DatabaseSettings, List)} for code without a plugin, such as tests. */
    public static Database open(
            String name, Path baseDirectory, DatabaseSettings settings, List<Migration> migrations) {
        return migrated(Database.create(name, baseDirectory, settings), migrations);
    }

    private static Database migrated(Database database, List<Migration> migrations) {
        try {
            database.migrate(migrations);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
        return database;
    }
}
