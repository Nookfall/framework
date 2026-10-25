package com.nookfall.framework.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabasesTest {

    @TempDir
    Path directory;

    @Test
    void opensAndMigrates() throws Exception {
        try (Database database = Databases.open(
                "test",
                directory,
                new DatabaseSettings(),
                List.of(Migration.sql(1, "create", "CREATE TABLE t (id INT)")))) {
            int rows = database.query(
                    connection -> connection.createStatement().executeUpdate("INSERT INTO t VALUES (1)"));
            assertThat(rows).isEqualTo(1);
        }
    }

    @Test
    void aFailingMigrationClosesTheDatabaseAndRethrows() {
        assertThatThrownBy(() -> Databases.open(
                        "test", directory, new DatabaseSettings(), List.of(Migration.sql(1, "bad", "NOT SQL AT ALL"))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void settingsCompareByValueSoReloadsCanTellWhetherARestartIsNeeded() throws Exception {
        DatabaseSettings a = new DatabaseSettings();
        DatabaseSettings b = new DatabaseSettings();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        var host = DatabaseSettings.class.getDeclaredField("host");
        host.setAccessible(true);
        host.set(b, "db.example.com");
        assertThat(b).isNotEqualTo(a);
    }
}
