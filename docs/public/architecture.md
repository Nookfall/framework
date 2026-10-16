---
title: Architecture
description: How the Nookfall Framework turns annotated code into a running plugin, and its standalone and shared packaging modes.
sidebar:
  order: 2
---

:::note[Private development]
This page describes the design of a framework that is not published yet. It is meant to explain how Nookfall
plugins work, not how to use the framework.
:::

## From annotations to a running plugin

1. **Compile time.** An annotation processor reads the plugin's commands, listeners, configuration classes, message
   interfaces, menus and scheduled jobs. It rejects invalid declarations with precise errors, checks bundled
   translations and menu layouts, and generates registration code. Nothing is discovered by scanning at runtime.
2. **Startup.** The plugin starts its own framework runtime. Configuration and translations are loaded first, then
   Avaje Inject constructs the plugin's objects, then commands (through Paper's Brigadier API), listeners, menus and
   scheduled tasks are registered. If anything fails, everything created so far is released and the plugin reports
   a readable error.
3. **Running.** Reloads build the new configuration, translations and menus, validate them and swap them in at once;
   a failed reload keeps the previous working state.
4. **Shutdown.** Everything the plugin created (commands, listeners, tasks, open menus, database connections) belongs
   to that plugin and is released when it disables.

## Principles

- **Compile-time checks over runtime surprises.** Problems are found when the plugin is built.
- **Each plugin owns its resources.** Plugins never share objects, configuration or tasks, even when they share the
  framework's classes.
- **Safe by default.** Player text is never parsed as formatting, and game state is only touched on the thread
  that owns it; database work runs on its own bounded threads.
- **Paper-native.** It uses Paper's Brigadier commands, Adventure text and schedulers directly, without NMS or
  reflection into server internals.
- **Small public surface.** Plain annotations and interfaces; implementation details stay internal.

## Standalone and shared packaging

Every plugin is built in two forms from the same code:

| | Standalone jar | Shared jar |
|---|---|---|
| Contains | The plugin plus a private, relocated copy of the framework | Only the plugin |
| Needs | Nothing else | The `NookfallFramework` plugin on the server |
| Size | Larger | Small |
| Best for | Most servers, single plugins | Servers running several Nookfall plugins |

Relocation means two standalone plugins can carry different framework versions without conflict. In shared mode,
all plugins use one set of framework classes, but each still gets its own isolated runtime. Before starting, a shared
plugin checks that the installed framework is compatible (same minor version, same or newer patch) and stops with
a clear message if it is not.

## Further reading

- [Capabilities](/docs/framework/capabilities/)
- [Compatibility](/docs/framework/compatibility/)
