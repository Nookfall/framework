---
title: Storage
description: Pooled SQLite, MySQL and MariaDB access with a bounded async executor, transactions and migrations.
---

`framework-storage` is a thin, explicit layer over JDBC: connection pooling (HikariCP), a bounded executor that
keeps SQL off server threads, transactions, and versioned migrations. It is not an ORM.

Enable it in `build.gradle.kts`:

```kotlin
nookfall {
    storage = true
}
```

SQLite and MySQL drivers ship with Paper, so plugins do not bundle drivers. MariaDB uses the MariaDB driver if
your plugin provides it (for example through `plugin.yml` `libraries`) and otherwise the MySQL driver, which also
talks to MariaDB.

## Settings

`DatabaseSettings` is designed to be embedded in a `@Config` class:

```java
@Comment("Database connection.")
private DatabaseSettings database = new DatabaseSettings();
```

```yaml
database:
  # sqlite, mysql or mariadb
  type: SQLITE
  # SQLite file, relative to the plugin folder.
  file: data.db
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: ''
  # Maximum pooled connections (SQLite always uses one).
  pool-size: 4
  # Extra JDBC driver properties.
  properties: {}
```

## Creating the database

Create it once and close it with the plugin. With Avaje, a factory does both:

```java
@Factory
public final class StorageFactory {

    @Bean(destroyMethod = "close")
    Database database(Plugin plugin, ConfigRef<StarterConfig> config) {
        Database database = Database.create(plugin, config.get().database());
        database.migrate(List.of(Migration.resource(1, "create visits", StarterPlugin.class, "db/V1__visits.sql")));
        return database;
    }
}
```

## Queries

```java
public CompletableFuture<List<String>> recentVisitors(int limit) {
    return database.listAsync("SELECT name FROM visits ORDER BY last_seen DESC LIMIT ?", r -> r.getString(1), limit);
}

public CompletableFuture<Integer> total() {
    return database.findAsync("SELECT COUNT(*) FROM visits", r -> r.getInt(1)).thenApply(c -> c.orElse(0));
}
```

| Method | Does |
|---|---|
| `updateAsync(sql, params...)` | `INSERT`/`UPDATE`/`DELETE`, returns the affected row count |
| `listAsync(sql, mapper, params...)` | All rows mapped |
| `findAsync(sql, mapper, params...)` | The first row, if any |
| `queryAsync(connection -> ...)` | Arbitrary JDBC work in auto-commit mode |
| `transactionAsync(connection -> ...)` | Arbitrary work committed on success, rolled back on any exception |
| `query(...)`, `transaction(...)` | Blocking variants, **not** allowed on server threads (they throw there) |

Parameters are bound positionally with `PreparedStatement`; `UUID`s are bound as strings. SQL errors complete
the future exceptionally with a `DatabaseException` whose cause is the original `SQLException`.

Futures complete on database threads. Return to a server thread before touching the game:

```java
repository.homes(player.getUniqueId())
        .thenAcceptAsync(homes -> showHomes(player, homes), tasks.entity(player).executor());
```

## Migrations

```java
database.migrate(List.of(
        Migration.resource(1, "create homes", HomesPlugin.class, "db/V1__homes.sql"),
        Migration.sql(2, "index owner", "CREATE INDEX homes_owner ON homes (owner);"),
        Migration.code(3, "backfill", connection -> { /* JDBC */ })));
```

Each migration runs once, in version order, in its own transaction, and is recorded in `nookfall_migrations`.
Re-running `migrate` skips applied versions. Editing the SQL of an already applied migration is reported as an
error instead of being silently ignored. `migrate` is blocking and, unlike `query`, may run on the server thread,
because it is meant for startup.

## Executor and shutdown

Work runs on a bounded pool sized to the connection pool, with a bounded queue; when the queue is full, new work
fails fast with a `DatabaseException` instead of piling up. `close()` stops accepting work, waits (with a
timeout) for running work, fails the futures of work that never started, and closes the pool. Nothing is left
hanging.
