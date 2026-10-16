---
title: Scheduling
description: Duration-based tasks in explicit execution contexts, @Scheduled methods with configurable intervals, and automatic cleanup.
---

## The `Tasks` API

Inject `Tasks`. Every task is owned by the plugin and cancelled when its runtime closes. Pick the execution
context explicitly:

| Scope | Runs on | Use for |
|---|---|---|
| `tasks.global()` | The global region thread (the main thread on Paper) | World-independent server state |
| `tasks.entity(entity)` | The thread that owns the entity | Anything touching that player or entity |
| `tasks.region(location)` / `region(world, chunkX, chunkZ)` | The thread that owns that region | Blocks and chunks |
| `tasks.async()` | Background threads | I/O and computation; **no** world, entity or inventory access |

Each scope offers:

```java
TaskHandle run(Runnable task);
TaskHandle later(Duration delay, Runnable task);
TaskHandle timer(Duration initialDelay, Duration period, Runnable task);
TaskHandle every(Duration period, Runnable task);
<T> CompletableFuture<T> supply(Supplier<T> supplier);
Executor executor();
```

```java
tasks.entity(player).later(Duration.ofSeconds(3), () -> finishTeleport(player));
```

Durations are rounded up to whole ticks (50 ms) for tick-based contexts. Exceptions thrown by tasks are logged and
do not stop repeating tasks. `TaskHandle.cancel()` stops a task; `tasks.activeTasks()` counts live ones.

### Returning to the right thread

`executor()` lets `CompletableFuture` chains hop back to a server thread after asynchronous work:

```java
visits.recordVisit(player.getUniqueId(), player.getName())
        .thenAcceptAsync(count -> messages.welcome(player, count), tasks.entity(player).executor());
```

### Players leaving and entities disappearing

Entity tasks follow Paper's entity scheduler: they stop silently when the entity is removed, which includes a
player disconnecting. Use `tasks.entity(entity, retired)` to run `retired` instead when that happens, for example
to clean up state. The framework never schedules Bukkit API calls on asynchronous threads for you.

## `@Scheduled` methods

A no-argument method on any bean runs repeatedly while the plugin is enabled:

```java
public final class CleanupJob {

    @Scheduled(every = "${cleanup.interval}", execution = Execution.ASYNC)
    void cleanup() {
        // ...
    }
}
```

| Attribute | Meaning |
|---|---|
| `every` | Period: `30s`, `5m`, `1h30m`, `250ms`, `1d`, or a configuration reference |
| `initialDelay` | Delay before the first run; defaults to the period |
| `execution` | `GLOBAL` (default) or `ASYNC` |

The class does not need `@Singleton`; having a `@Scheduled` method makes it a bean.

### Configured intervals

`${path}` reads a duration string from configuration (`${file.yml:path}` when the plugin has several
configuration files). When that configuration is reloaded, the task is rescheduled with the new period. If the new
value is invalid, the error is logged and the previous schedule is kept. An invalid value at startup fails the
startup with a message naming the method and key.

### Checks

The processor rejects `@Scheduled` methods that are private, static, generic or take parameters, and constant
durations that do not parse or are not positive. Configuration references are validated at runtime.

## Folia

Execution contexts map to Paper's region, entity, global and async schedulers, so code written against them is
structured the way Folia requires. Folia itself is **not** supported or tested in 0.1.x.
