package com.nookfall.framework.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;

/**
 * A pooled database with a bounded executor. Asynchronous methods run on database threads and complete their futures
 * there; hop back to a server thread with {@code future.thenAcceptAsync(..., tasks.entity(player).executor())}.
 *
 * <p>Blocking methods must not be called from a server thread.
 */
public interface Database extends AutoCloseable {

    /** Creates a database from configuration. SQLite paths are relative to the plugin data folder. */
    static Database create(Plugin plugin, DatabaseSettings settings) {
        return HikariDatabase.create(plugin.getName(), plugin.getDataFolder().toPath(), settings);
    }

    /** Creates a database without a plugin, for tests and tools. */
    static Database create(String name, Path baseDirectory, DatabaseSettings settings) {
        return HikariDatabase.create(name, baseDirectory, settings);
    }

    /** Runs work with a connection from the pool, in auto-commit mode. */
    <T> CompletableFuture<T> queryAsync(SqlFunction<Connection, T> work);

    /**
     * Runs work in a transaction that is committed on success and rolled back on any exception. A transaction that
     * fails with a deadlock or serialization failure is rolled back and run again (up to three attempts in total), so
     * the work must not have side effects outside the connection.
     */
    <T> CompletableFuture<T> transactionAsync(SqlFunction<Connection, T> work);

    /** Executes an update statement with positional parameters and returns the affected row count. */
    CompletableFuture<Integer> updateAsync(String sql, Object... params);

    <T> CompletableFuture<List<T>> listAsync(String sql, RowMapper<T> mapper, Object... params);

    <T> CompletableFuture<Optional<T>> findAsync(String sql, RowMapper<T> mapper, Object... params);

    /** Blocking variant of {@link #queryAsync}. */
    <T> T query(SqlFunction<Connection, T> work);

    /** Blocking variant of {@link #transactionAsync}. */
    <T> T transaction(SqlFunction<Connection, T> work);

    /**
     * Applies pending migrations, blocking. Intended for plugin startup, so unlike {@link #query} it may run on the
     * server thread. Already applied SQL migrations whose text changed are reported as errors.
     *
     * @return the versions that were applied
     */
    List<Integer> migrate(List<Migration> migrations);

    DatabaseType type();

    /** The executor that runs database work. */
    Executor executor();

    /** Stops accepting work, waits for running work (up to a timeout) and closes the pool. */
    @Override
    void close();

    boolean isClosed();
}
