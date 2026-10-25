---
title: Dependency injection
description: How Nookfall uses Avaje Inject, which beans the framework provides and how plugin scopes stay isolated.
---

Nookfall does not have its own container. Objects are constructed by
[Avaje Inject](https://avaje.io/inject/), which generates its wiring at compile time. Missing or ambiguous
dependencies are compile errors reported by Avaje's processor.

## Beans

Classes annotated with `@Command`, `@ListenerComponent`, `@Gui`, `@CommandArgument`, or that contain a
`@Scheduled` method, are registered as singletons automatically; you do not add `@Singleton` to them. Any other
class you want injected uses Avaje's annotations as usual:

```java
@Singleton
public final class VisitRepository {

    private final Database database;

    public VisitRepository(Database database) {
        this.database = database;
    }
}
```

Use constructor injection. Avaje's `@Factory`/`@Bean` work as documented by Avaje; the starter builds its
database that way and closes it with the runtime:

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

## Provided beans

These can be injected into any bean:

| Type | Value |
|---|---|
| `Plugin`, `JavaPlugin` | The owning plugin |
| `Server` | `plugin.getServer()` |
| `java.util.logging.Logger` | The plugin's logger |
| `NookfallRuntime` | The plugin's runtime (for `reload()`, `mode()`, ...) |
| `Tasks` | Scheduling owned by this plugin |
| `Localization` | This plugin's translations |
| `ConfigService` | This plugin's configuration files |
| `ConfigRef<T>` | One per `@Config` class |
| `GuiManager` | This plugin's menus |
| `Integrations` | Optional support for other plugins, see [Integrations](integrations.md) |
| Your `@Messages` interfaces | Generated implementations |

Inject the concrete main class only through `Plugin`/`JavaPlugin`; the main class itself is not a bean.

## Scopes and lifecycle

Each plugin gets its own Avaje `BeanScope`, created by `Nookfall.start` and closed by `runtime.close()`. Two
plugins never share beans, even in shared mode where they use the same framework classes. Avaje lifecycle
methods (`@PreDestroy`, `@Bean(destroyMethod = ...)`) run when the scope closes, after commands, listeners,
tasks and GUI sessions have been released.

Startup order:

1. Configuration files and translations are loaded (so `ConfigRef.get()` works in constructors).
2. The bean scope is built.
3. Listeners, commands and GUIs are registered; `@Scheduled` methods start.

If any step fails, everything from earlier steps is released before `NookfallStartupException` is thrown.

## Looking up beans without injection

`runtime.get(Type.class)` and `runtime.find(Type.class)` return beans from the plugin's scope. Prefer
constructor injection; lookups are meant for the main class and integration code.
