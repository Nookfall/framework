---
title: Introduction
description: What the Nookfall Framework is, who it is for and where it stands.
sidebar:
  order: 1
---

:::note[Private development]
The Nookfall Framework is in private development and powers upcoming Nookfall plugins. It is not published yet:
there is no public repository, artifact or developer documentation. This section explains what it is and how it
works at a high level.
:::

The Nookfall Framework is the Java framework Nookfall builds its Paper plugins on. It takes care of the parts every
plugin needs (commands, configuration, translations, menus, scheduling and storage) so that each plugin can focus on
its own features, and so that every Nookfall plugin behaves the same way for server owners.

Its guiding idea:

> **Annotations declare structure. Java implements behaviour. YAML controls presentation.**

A plugin declares its commands, listeners, configuration, messages and menus with annotations. At compile time the
framework checks those declarations and generates the code that registers them, so mistakes such as a missing
translation or a menu button pointing at an action that does not exist fail the build instead of failing on a
server. Objects are wired with [Avaje Inject](https://avaje.io/inject/), and server owners control what players see,
including menu layouts, items and every message, through YAML files.

## What server owners notice

- **Commented configuration files** that are generated with defaults, validated on load and reloaded safely: a
  broken edit is reported with the exact path and the previous settings stay active.
- **Every message translatable**, with each player seeing their own client language when a translation exists.
- **Menus you can rearrange** by editing a character grid in YAML.
- **No surprises from player input:** names and other player text are never interpreted as formatting.
- **Two ways to install** plugins built on it: a self-contained jar, or a smaller jar that shares one framework
  plugin. See [Architecture](/docs/framework/architecture/).

## Status

| | |
|---|---|
| Version | 0.1.x, internal |
| Availability | Private. Not published as source or as a library |
| Tested on | Paper 1.21.1 to 26.3 (see [Compatibility](/docs/framework/compatibility/)) |
| First plugin | [Nookfall Homes](/plugins/homes/), release candidate |

Continue with [Architecture](/docs/framework/architecture/) or [Capabilities](/docs/framework/capabilities/).
