---
title: Integrations
description: Optional support for other plugins, declared once and constructed only when those plugins are enabled.
---

A plugin often works better with another plugin, but must keep working without it. Referencing the other plugin's
classes from ordinary code makes that fragile: if the plugin is missing, the first use throws `NoClassDefFoundError`. And
sprinkling `isPluginEnabled("Name")` checks around hides which code needs what.

`@Integration` puts the dependency in one class:

```java
// Your own interface: the rest of your plugin only knows this.
public interface SellHook {
    SellHook NONE = ...; // what to do without the integration

    Sale sell(Player player, Map<Material, Integer> items);
}

// The only class that touches the other plugin.
@Integration(value = SellHook.class, plugins = "NookfallSellwand")
public final class SellwandHook implements SellHook {

    private final EconomyFormatter economy; // beans can be injected as usual

    public SellwandHook(EconomyFormatter economy) {
        this.economy = economy;
    }

    @Override
    public Sale sell(Player player, Map<Material, Integer> items) {
        return SellApi.find().orElseThrow().sellVirtual(...); // SellApi is Sellwand's class
    }
}
```

Anywhere else, inject `Integrations`:

```java
public SpawnerMenu(Integrations integrations) {
    this.sell = integrations.get(SellHook.class, SellHook.NONE);
}
```

| Method | Returns |
|---|---|
| `find(Class<T>)` | `Optional<T>`: the integration offered as that type, if available |
| `get(Class<T>, fallback)` | the integration, or the fallback |
| `available(Class<?>)` | whether it is available |

## Rules

- The annotated class must implement or extend `value()` and name at least one plugin in `plugins`; the processor checks
  both.
- It is constructed by Avaje **on first use**, never at startup, and only when every named plugin is enabled. If a plugin
  is not enabled yet, the next call checks again.
- Constructing it can fail (the other plugin is a different version, a class is missing). That is logged once and the
  integration is treated as unavailable: the rest of your plugin keeps working.
- The result is created once and shared. It is a prototype bean for Avaje, so do not rely on `@Singleton` semantics.
- Add the plugin to `softDepend` in the `nookfall { }` block so it enables before yours.
- The interface and the fallback must not mention the other plugin's classes.

`@ListenerComponent(requiresPlugins = ...)` is the same idea for event listeners.
