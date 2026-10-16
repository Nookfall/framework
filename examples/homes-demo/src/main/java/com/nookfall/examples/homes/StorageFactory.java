package com.nookfall.examples.homes;

import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.storage.Database;
import com.nookfall.framework.storage.Migration;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import java.util.List;
import org.bukkit.plugin.Plugin;

/** Opens the database at startup; Avaje closes it when the plugin is disabled. */
@Factory
public final class StorageFactory {

    @Bean(destroyMethod = "close")
    Database database(Plugin plugin, ConfigRef<HomesConfig> config) {
        Database database = Database.create(plugin, config.get().database());
        database.migrate(List.of(Migration.resource(1, "create homes", HomesPlugin.class, "db/V1__homes.sql")));
        return database;
    }
}
