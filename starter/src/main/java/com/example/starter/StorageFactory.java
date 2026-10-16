package com.example.starter;

import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.storage.Database;
import com.nookfall.framework.storage.Migration;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import java.util.List;
import org.bukkit.plugin.Plugin;

@Factory
public final class StorageFactory {

    @Bean(destroyMethod = "close")
    Database database(Plugin plugin, ConfigRef<StarterConfig> config) {
        Database database = Database.create(plugin, config.get().database());
        database.migrate(List.of(Migration.resource(1, "create visits", StarterPlugin.class, "db/V1__visits.sql")));
        return database;
    }
}
