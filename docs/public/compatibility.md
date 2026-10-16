---
title: Compatibility
description: Which Paper and Java versions the Nookfall Framework is tested on, and how compatibility is decided.
sidebar:
  order: 4
---

## Tested versions

The framework's automated test suite boots real Paper servers and checks commands, listeners, configuration,
translations, menus (including clicks from a headless Minecraft client), scheduling, storage and both packaging
modes. Version 0.1.1 passed it on:

| Paper | Java | Notes |
|---|---|---|
| 1.21.1 | 21 | |
| 1.21.4 | 21 | |
| 1.21.11 | 21 | |
| 26.1.2 | 25 | |
| 26.3 | 25 | Menu clicks are verified by unit tests only: the headless client has no 26.3 protocol support yet |

Tested on 16 October 2026 against the newest Paper build of each version.

## Not supported

- **Folia.** The framework's scheduling is structured the way Folia needs, but Folia is not tested or supported.
- **Spigot and other non-Paper servers.** The framework relies on Paper's command, text and scheduler APIs.

## How support is decided

A version is listed only after the full suite has passed on it. A server that merely starts is not counted as
tested. Plugins built on the framework publish their own tested versions, because their own features are tested
separately; see, for example, [Nookfall Homes compatibility](/docs/plugins/homes/compatibility/).

## Versioning

The framework follows [Semantic Versioning](https://semver.org/). Before 1.0, a minor version may contain breaking
changes, so plugins using the shared framework plugin need the same minor version and the same or a newer patch.
