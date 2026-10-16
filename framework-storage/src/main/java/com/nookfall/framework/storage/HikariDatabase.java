package com.nookfall.framework.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** {@link Database} backed by HikariCP and a bounded thread pool. */
final class HikariDatabase implements Database {

    private static final Logger LOGGER = Logger.getLogger("Nookfall/Database");
    private static final int QUEUE_CAPACITY = 2048;
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final String name;
    private final DatabaseType type;
    private final HikariDataSource dataSource;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    private HikariDatabase(String name, DatabaseType type, HikariDataSource dataSource, int threads) {
        this.name = name;
        this.type = type;
        this.dataSource = dataSource;
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, name + "-db-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    static HikariDatabase create(String name, Path baseDirectory, DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name + "-pool");
        int poolSize = settings.poolSize();
        switch (settings.type()) {
            case SQLITE -> {
                Path file = baseDirectory.resolve(settings.file()).toAbsolutePath();
                try {
                    Files.createDirectories(file.getParent());
                } catch (java.io.IOException e) {
                    throw new DatabaseException("Cannot create directory for " + file, e);
                }
                config.setJdbcUrl("jdbc:sqlite:" + file);
                config.setDriverClassName(driver("org.sqlite.JDBC", "SQLite"));
                // SQLite allows one writer; a single connection serializes access without lock errors.
                poolSize = 1;
                config.setConnectionInitSql("PRAGMA foreign_keys = ON");
                config.addDataSourceProperty("journal_mode", "WAL");
                config.addDataSourceProperty("busy_timeout", "5000");
            }
            case MYSQL -> {
                config.setJdbcUrl(
                        "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database());
                config.setDriverClassName(driver("com.mysql.cj.jdbc.Driver", "MySQL"));
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("useServerPrepStmts", "true");
            }
            case MARIADB -> {
                if (present("org.mariadb.jdbc.Driver")) {
                    config.setJdbcUrl(
                            "jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database());
                    config.setDriverClassName("org.mariadb.jdbc.Driver");
                } else {
                    config.setJdbcUrl(
                            "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database());
                    config.setDriverClassName(driver("com.mysql.cj.jdbc.Driver", "MySQL/MariaDB"));
                }
            }
        }
        if (settings.type() != DatabaseType.SQLITE) {
            config.setUsername(settings.username());
            config.setPassword(settings.password());
        }
        settings.properties().forEach(config::addDataSourceProperty);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(1, poolSize));
        config.setConnectionTimeout(10_000);
        // Fail fast at startup if the database is unreachable.
        config.setInitializationFailTimeout(10_000);
        HikariDataSource dataSource;
        try {
            dataSource = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new DatabaseException(
                    "Cannot connect to the " + settings.type().name().toLowerCase(java.util.Locale.ROOT) + " database: "
                            + rootMessage(e),
                    e);
        }
        return new HikariDatabase(name, settings.type(), dataSource, poolSize);
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, HikariDatabase.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String driver(String className, String label) {
        if (!present(className)) {
            throw new DatabaseException("The " + label + " JDBC driver (" + className + ") is not available");
        }
        return className;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.toString() : root.getMessage();
    }

    // ---- async ----

    private <T> CompletableFuture<T> submit(SqlFunction<Connection, T> work, boolean transaction) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new DatabaseException(name + " database is closed"));
        }
        Job<T> job = new Job<>(() -> run(work, transaction));
        try {
            executor.execute(job);
            return job.future;
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new DatabaseException(
                    closed.get() ? name + " database is closed" : name + " database queue is full", e));
        }
    }

    /** Work whose future is completed even if the executor drops it during shutdown. */
    private final class Job<T> implements Runnable {
        final CompletableFuture<T> future = new CompletableFuture<>();
        private final java.util.function.Supplier<T> body;

        Job(java.util.function.Supplier<T> body) {
            this.body = body;
        }

        @Override
        public void run() {
            try {
                future.complete(body.get());
            } catch (CompletionException e) {
                future.completeExceptionally(e.getCause() != null ? e.getCause() : e);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }

        void abandon() {
            future.completeExceptionally(new DatabaseException(name + " database closed before the work ran"));
        }
    }

    private <T> T run(SqlFunction<Connection, T> work, boolean transaction) {
        try (Connection connection = dataSource.getConnection()) {
            if (!transaction) {
                return work.apply(connection);
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Throwable t) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    t.addSuppressed(rollback);
                }
                throw t;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new CompletionException(new DatabaseException(e.getMessage(), e));
        }
    }

    @Override
    public <T> CompletableFuture<T> queryAsync(SqlFunction<Connection, T> work) {
        return submit(work, false);
    }

    @Override
    public <T> CompletableFuture<T> transactionAsync(SqlFunction<Connection, T> work) {
        return submit(work, true);
    }

    @Override
    public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
        return submit(
                connection -> {
                    try (PreparedStatement statement = prepare(connection, sql, params)) {
                        return statement.executeUpdate();
                    }
                },
                false);
    }

    @Override
    public <T> CompletableFuture<List<T>> listAsync(String sql, RowMapper<T> mapper, Object... params) {
        return submit(connection -> list(connection, sql, mapper, params), false);
    }

    @Override
    public <T> CompletableFuture<Optional<T>> findAsync(String sql, RowMapper<T> mapper, Object... params) {
        return submit(
                connection -> {
                    List<T> rows = list(connection, sql, mapper, params);
                    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
                },
                false);
    }

    static <T> List<T> list(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, params);
                ResultSet rows = statement.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rows.next()) {
                out.add(mapper.map(rows));
            }
            return out;
        }
    }

    static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                Object value = params[i];
                if (value instanceof java.util.UUID uuid) {
                    value = uuid.toString();
                } else if (value instanceof Enum<?> e) {
                    value = e.name();
                } else if (value instanceof java.time.Instant instant) {
                    value = instant.toEpochMilli();
                }
                statement.setObject(i + 1, value);
            }
            return statement;
        } catch (SQLException e) {
            statement.close();
            throw e;
        }
    }

    // ---- blocking ----

    private void checkThread() {
        if (onServerThread()) {
            throw new IllegalStateException(
                    "Blocking database calls are not allowed on a server thread; use the async methods");
        }
    }

    private static boolean onServerThread() {
        try {
            return org.bukkit.Bukkit.getServer() != null && org.bukkit.Bukkit.isPrimaryThread();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    @Override
    public <T> T query(SqlFunction<Connection, T> work) {
        checkThread();
        return unwrap(() -> run(work, false));
    }

    @Override
    public <T> T transaction(SqlFunction<Connection, T> work) {
        checkThread();
        return unwrap(() -> run(work, true));
    }

    private static <T> T unwrap(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    @Override
    public List<Integer> migrate(List<Migration> migrations) {
        List<Migration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(Migration::version));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).version() == sorted.get(i - 1).version()) {
                throw new IllegalArgumentException(
                        "Duplicate migration version " + sorted.get(i).version());
            }
        }
        return unwrap(() -> run(
                connection -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE IF NOT EXISTS nookfall_migrations ("
                                + "version INTEGER PRIMARY KEY, description VARCHAR(255) NOT NULL, "
                                + "checksum VARCHAR(64), applied_at BIGINT NOT NULL)");
                    }
                    Map<Integer, String> applied = new HashMap<>();
                    for (Object[] row : list(connection, "SELECT version, checksum FROM nookfall_migrations", r ->
                            new Object[] {r.getInt(1), r.getString(2)})) {
                        applied.put((Integer) row[0], (String) row[1]);
                    }
                    List<Integer> ran = new ArrayList<>();
                    boolean autoCommit = connection.getAutoCommit();
                    for (Migration migration : sorted) {
                        if (applied.containsKey(migration.version())) {
                            String recorded = applied.get(migration.version());
                            if (migration.checksum() != null
                                    && recorded != null
                                    && !recorded.equals(migration.checksum())) {
                                throw new SQLException("Migration " + migration.version() + " ("
                                        + migration.description() + ") was changed after it was applied");
                            }
                            continue;
                        }
                        connection.setAutoCommit(false);
                        try {
                            migration.apply(connection);
                            try (PreparedStatement insert = prepare(
                                    connection,
                                    "INSERT INTO nookfall_migrations (version, description, checksum, applied_at) VALUES (?, ?, ?, ?)",
                                    migration.version(),
                                    migration.description(),
                                    migration.checksum(),
                                    System.currentTimeMillis())) {
                                insert.executeUpdate();
                            }
                            connection.commit();
                            ran.add(migration.version());
                        } catch (SQLException | RuntimeException e) {
                            connection.rollback();
                            throw new SQLException(
                                    "Migration " + migration.version() + " (" + migration.description() + ") failed: "
                                            + e.getMessage(),
                                    e);
                        } finally {
                            connection.setAutoCommit(autoCommit);
                        }
                    }
                    if (!ran.isEmpty()) {
                        LOGGER.info(name + ": applied database migrations " + ran);
                    }
                    return ran;
                },
                false));
    }

    @Override
    public DatabaseType type() {
        return type;
    }

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                for (Runnable runnable : dropped) {
                    if (runnable instanceof Job<?> job) {
                        job.abandon();
                    }
                }
                LOGGER.warning(name + ": database work did not finish within " + SHUTDOWN_WAIT_SECONDS + "s; cancelled "
                        + dropped.size() + " queued task(s)");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }
}
