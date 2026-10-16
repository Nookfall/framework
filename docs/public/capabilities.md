---
title: Capabilities
description: What the Nookfall Framework provides to plugins built on it.
sidebar:
  order: 3
---

:::note[Private development]
These are the capabilities of the current internal version, verified by its automated tests. They are listed to
explain what Nookfall plugins can rely on, not as a public API.
:::

| Area | What the framework provides |
|---|---|
| **Commands** | Brigadier commands from annotated classes: subcommands, aliases, typed and custom argument types, permissions, tab completion, cooldowns, generated help and translated errors |
| **Listeners** | Event listeners registered and removed with the plugin, optionally only when another plugin is present |
| **Configuration** | YAML files generated from plain Java classes with comments and defaults, validation, custom value types, unknown-key warnings and atomic reloads |
| **Localization** | Per-player languages with fallbacks, MiniMessage formatting with injection-safe placeholders, typed message methods, and server-owner edits kept across updates |
| **Menus** | Inventory menus defined in YAML: character-grid layouts, translated items, paged lists, entry variants, asynchronous loading, click actions, conditions, protection against taking items, and hot reload |
| **Scheduling** | Tasks in explicit global, entity, region and background contexts, `Duration`-based timing, configurable intervals, cleanup on disable |
| **Storage** | Pooled SQLite, MySQL and MariaDB, a bounded background executor, transactions with deadlock retries, and versioned migrations kept per plugin |
| **Utilities** | Item building and serialization, locations, destination safety checks, durations, cooldowns and player helpers |
| **Packaging** | Standalone and shared jars from one build, generated `plugin.yml` with permissions, and framework version checks |
| **Testing** | Helpers for testing configuration, translations and commands without a server, and a harness that runs plugins on real Paper servers |

Folia is not supported yet.
