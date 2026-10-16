package com.nookfall.framework.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Behaviour every supported database must show. */
abstract class DatabaseContract {

    protected Database database;

    protected abstract Database open() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        database = open();
        database.migrate(List.of(Migration.sql(1, "homes", """
                -- homes table
                CREATE TABLE homes (
                  owner VARCHAR(36) NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  x INTEGER NOT NULL,
                  PRIMARY KEY (owner, name)
                );
                CREATE TABLE counters (id INTEGER PRIMARY KEY, value INTEGER NOT NULL);
                """)));
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void insertsAndQueries() throws Exception {
        database.updateAsync("INSERT INTO homes (owner, name, x) VALUES (?, ?, ?)", "a", "base", 10)
                .get(10, TimeUnit.SECONDS);
        List<String> names = database.listAsync("SELECT name FROM homes WHERE owner = ?", r -> r.getString(1), "a")
                .get(10, TimeUnit.SECONDS);
        assertThat(names).containsExactly("base");
        assertThat(database.findAsync("SELECT x FROM homes WHERE owner = ? AND name = ?", r -> r.getInt(1), "a", "base")
                        .get(10, TimeUnit.SECONDS))
                .contains(10);
        assertThat(database.findAsync("SELECT x FROM homes WHERE owner = ?", r -> r.getInt(1), "nobody")
                        .get(10, TimeUnit.SECONDS))
                .isEmpty();
    }

    @Test
    void transactionRollsBackOnFailure() throws Exception {
        CompletableFuture<Object> failing = database.transactionAsync(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO homes (owner, name, x) VALUES ('t', 'one', 1)");
            }
            throw new SQLException("boom");
        });
        assertThatThrownBy(() -> failing.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(DatabaseException.class)
                .hasMessageContaining("boom");
        int count = database.query(
                c -> HikariDatabase.list(c, "SELECT COUNT(*) FROM homes WHERE owner = 't'", r -> r.getInt(1))
                        .getFirst());
        assertThat(count).isZero();

        database.transaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO homes (owner, name, x) VALUES ('t', 'two', 2)");
            }
            return null;
        });
        int committed = database.query(
                c -> HikariDatabase.list(c, "SELECT COUNT(*) FROM homes WHERE owner = 't'", r -> r.getInt(1))
                        .getFirst());
        assertThat(committed).isEqualTo(1);
    }

    @Test
    void concurrentOperationsAllComplete() throws Exception {
        database.updateAsync("INSERT INTO counters (id, value) VALUES (1, 0)").get(10, TimeUnit.SECONDS);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            futures.add(database.transactionAsync(connection -> {
                try (Statement statement = connection.createStatement()) {
                    return statement.executeUpdate("UPDATE counters SET value = value + 1 WHERE id = 1");
                }
            }));
            futures.add(database.updateAsync("INSERT INTO homes (owner, name, x) VALUES (?, ?, ?)", "c", "h" + i, i));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        assertThat(database.findAsync("SELECT value FROM counters WHERE id = 1", r -> r.getInt(1))
                        .get(10, TimeUnit.SECONDS))
                .contains(200);
        assertThat(database.findAsync("SELECT COUNT(*) FROM homes WHERE owner = 'c'", r -> r.getInt(1))
                        .get(10, TimeUnit.SECONDS))
                .contains(200);
    }

    @Test
    void appliedMigrationsAreSkippedAndChecksumsVerified() {
        Migration homes = Migration.sql(1, "homes", """
                -- homes table
                CREATE TABLE homes (
                  owner VARCHAR(36) NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  x INTEGER NOT NULL,
                  PRIMARY KEY (owner, name)
                );
                CREATE TABLE counters (id INTEGER PRIMARY KEY, value INTEGER NOT NULL);
                """);
        Migration second = Migration.sql(2, "add y", "ALTER TABLE homes ADD COLUMN y INTEGER DEFAULT 0;");
        assertThat(database.migrate(List.of(homes, second))).containsExactly(2);
        assertThat(database.migrate(List.of(homes, second))).isEmpty();
        Migration edited = Migration.sql(2, "add y", "ALTER TABLE homes ADD COLUMN z INTEGER DEFAULT 0;");
        assertThatThrownBy(() -> database.migrate(List.of(homes, edited)))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("was changed after it was applied");
    }

    @Test
    void failedMigrationIsNotRecorded() {
        AtomicInteger runs = new AtomicInteger();
        Migration broken = Migration.code(5, "broken", connection -> {
            runs.incrementAndGet();
            throw new SQLException("bad migration");
        });
        assertThatThrownBy(() -> database.migrate(List.of(broken))).hasMessageContaining("bad migration");
        assertThatThrownBy(() -> database.migrate(List.of(broken))).hasMessageContaining("bad migration");
        assertThat(runs).hasValue(2);
    }

    @Test
    void closeWaitsForQueuedWorkThenRejects() throws Exception {
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(database.updateAsync("INSERT INTO homes (owner, name, x) VALUES (?, ?, ?)", "s", "h" + i, i));
        }
        database.close();
        for (CompletableFuture<Integer> future : futures) {
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(database.isClosed()).isTrue();
        assertThatThrownBy(() -> database.updateAsync("SELECT 1").get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(DatabaseException.class)
                .hasMessageContaining("closed");
    }
}
