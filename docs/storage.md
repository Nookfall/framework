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
| `transactionAsync(connection -> ...)` | Arbitrary work committed on success, rolled back on any exception; retried after deadlocks (below) |
| `query(...)`, `transaction(...)` | Blocking variants, **not** allowed on server threads (they throw there) |

Parameters are bound positionally with `PreparedStatement`; `UUID`s are bound as strings. SQL errors complete
the future exceptionally with a `DatabaseException` whose cause is the original `SQLException`.

A transaction that fails with a deadlock or serialization failure (SQL state `40xxx`, MySQL/MariaDB error 1213) has
been rolled back completely, so it is run again, up to three attempts in total. Keep transaction work free of side
effects outside the connection. To avoid deadlocks in the first place, take exclusive locks with a single statement
(for example `INSERT ... ON DUPLICATE KEY UPDATE`) rather than inserting and then selecting `FOR UPDATE`.

Futures complete on database threads. Return to a server thread before touching the game:

```java
repository.homes(player.getUniqueId())
        .thenAcceptAsync(homes -> showHomes(player, homes), tasks.entity(player).executor());
```

## Migrations

The simplest way is one SQL file per version in your resources, named `V<version>__<description>.sql`:

```text
src/main/resources/db/migration/
├── V1__create_homes.sql
└── V2__index_owner.sql
```

```java
database.migrate(Migration.discover(HomesPlugin.class, "db/migration"));
```

`discover` lists the directory inside the plugin jar (and on the test classpath), orders the files by version and
rejects files that do not follow the naming scheme or share a version. Adding a migration means adding a file.

Migrations can also be listed explicitly, including ones written in Java:

```java
database.migrate(List.of(
        Migration.resource(1, "create homes", HomesPlugin.class, "db/V1__homes.sql"),
        Migration.sql(2, "index owner", "CREATE INDEX homes_owner ON homes (owner);"),
        Migration.code(3, "backfill", connection -> { /* JDBC */ })));
```

Each migration runs once, in version order, in its own transaction, and is recorded in `nookfall_migrations` under
the plugin's name, so several plugins can share one MySQL/MariaDB database with their own version numbers. (A table
created by 0.1.0, which had no plugin column, is upgraded automatically; its rows are assigned to the first plugin
that migrates afterwards.)
Re-running `migrate` skips applied versions. Editing the SQL of an already applied migration is reported as an
error instead of being silently ignored. `migrate` is blocking and, unlike `query`, may run on the server thread,
because it is meant for startup.

## Executor and shutdown

Work runs on a bounded pool sized to the connection pool, with a bounded queue; when the queue is full, new work
fails fast with a `DatabaseException` instead of piling up. `close()` stops accepting work, waits (with a
timeout) for running work, fails the futures of work that never started, and closes the pool. Nothing is left
hanging.
