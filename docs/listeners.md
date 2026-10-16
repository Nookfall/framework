---
title: Listeners
description: Bukkit listeners constructed by Avaje and registered and unregistered with the plugin automatically.
---

Annotate a `Listener` with `@ListenerComponent`. It is constructed by Avaje (so it can inject services) and
registered when the runtime starts. Handlers are ordinary `@EventHandler` methods with the usual priority and
`ignoreCancelled` options.

```java
@ListenerComponent
public final class JoinListener implements Listener {

    private final VisitRepository visits;
    private final StarterMessages messages;
    private final Tasks tasks;

    public JoinListener(VisitRepository visits, StarterMessages messages, Tasks tasks) {
        this.visits = visits;
        this.messages = messages;
        this.tasks = tasks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        visits.recordVisit(player.getUniqueId(), player.getName())
                .thenAcceptAsync(count -> messages.welcome(player, count), tasks.entity(player).executor());
    }
}
```

(From the starter template, slightly shortened.)

Each listener is registered exactly once and unregistered with `HandlerList.unregisterAll` when the runtime
closes. A plugin can have any number of listeners. The processor rejects a `@ListenerComponent` class that does
not implement `Listener`.

## Optional integrations

A listener that references another plugin's classes must not be constructed when that plugin is missing.
Declare the requirement:

```java
@ListenerComponent(requiresPlugins = "PlaceholderAPI")
public final class PlaceholderListener implements Listener {
    // ...
}
```

Such listeners are created lazily, only when every listed plugin is enabled; otherwise the runtime logs that it
skipped them. Add the plugin to `softDepend` in the `nookfall { }` block so it enables first.
