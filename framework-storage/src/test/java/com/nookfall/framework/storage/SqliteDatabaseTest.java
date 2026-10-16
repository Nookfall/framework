package com.nookfall.framework.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDatabaseTest extends DatabaseContract {

    @TempDir
    Path directory;

    @Override
    protected Database open() {
        return Database.create("test", directory, new DatabaseSettings());
    }

    @Override
    protected Database openShared(String name) {
        return Database.create(name, directory, new DatabaseSettings());
    }

    @Test
    void unreachableServerFailsFastWithReadableError() throws Exception {
        DatabaseSettings settings = new DatabaseSettings();
        var type = DatabaseSettings.class.getDeclaredField("type");
        type.setAccessible(true);
        type.set(settings, DatabaseType.MYSQL);
        var port = DatabaseSettings.class.getDeclaredField("port");
        port.setAccessible(true);
        port.set(settings, 1);
        assertThatThrownBy(() -> Database.create("test", directory, settings))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Cannot connect to the mysql database");
    }

    @Test
    void duplicateMigrationVersionsAreRejected() {
        assertThatThrownBy(() ->
                        database.migrate(List.of(Migration.sql(9, "a", "SELECT 1"), Migration.sql(9, "b", "SELECT 2"))))
                .hasMessageContaining("Duplicate migration version 9");
    }
}
