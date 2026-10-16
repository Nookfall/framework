---
title: Nookfall Framework
description: A Paper plugin framework where annotations declare structure, Java implements behaviour and YAML controls presentation.
---

Nookfall Framework removes the repetitive parts of writing a Paper plugin. You declare commands, listeners,
configuration, messages, menus and scheduled jobs with annotations; an annotation processor turns those
declarations into generated registration code at compile time, and [Avaje Inject](https://avaje.io/inject/)
constructs your classes. Server owners control presentation (menu layouts, item looks, messages) through YAML.

> **Annotations declare structure. Java implements behaviour. YAML controls presentation. Avaje constructs
> objects. Nookfall integrates everything with Paper.**

## What it does

| Area | What you write | What the framework does |
|---|---|---|
| [Commands](commands.md) | `@Command` classes with `@Subcommand` methods | Builds and registers a Paper Brigadier tree with typed arguments, permissions, suggestions, cooldowns, help and localized errors |
| [Listeners](listeners.md) | `@ListenerComponent` classes with `@EventHandler` | Constructs and registers them, unregisters on disable |
| [Configuration](configuration.md) | Plain classes with fields and defaults | Generates commented YAML, validates, reloads atomically, keeps the last good snapshot |
| [Localization](localization.md) | `lang/<locale>.yml` and `@Messages` interfaces | Per-player locales, fallback chain, MiniMessage with safe placeholders, generated message methods |
| [GUIs](guis.md) | `@Gui` classes with providers, actions, conditions | YAML layouts with pagination, async loading, click types, protection and hot reload |
| [Scheduling](scheduling.md) | `@Scheduled` methods, `Tasks` | Region/entity/global/async contexts, `Duration`-based API, config-driven intervals, cleanup |
| [Storage](storage.md) | SQL and migrations | Pooled SQLite/MySQL/MariaDB, bounded async executor, transactions, migrations |
| [Utilities](utilities.md) | — | Items, locations, durations, players, persistent data |

Everything a plugin creates through the framework belongs to that plugin and is released when it disables.

## Two jars from one source tree

The Gradle plugin builds both from the same code:

- **Standalone** `MyPlugin-1.0.0.jar` bundles and relocates the framework. Nothing else to install.
- **Shared** `MyPlugin-1.0.0-shared.jar` is small and uses the installed `NookfallFramework` plugin.

See [Packaging](packaging.md).

## Requirements

- Paper 1.21.1 or newer (tested up to 26.3; see [Compatibility](compatibility.md))
- Java 21 or newer at runtime; plugins compile to Java 21 bytecode

## Where to go next

1. [Quick start](quick-start.md): build your first plugin from the starter template.
2. [Modules](modules.md) and [Dependency injection](dependency-injection.md): how the pieces fit.
3. The feature guides above.
4. [Testing](testing.md) and [Troubleshooting](troubleshooting.md).
