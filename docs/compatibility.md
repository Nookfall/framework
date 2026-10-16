---
title: Compatibility
description: Supported Paper and Java versions for Nookfall Framework 0.1.1 and the tested compatibility matrix.
---

## Supported

| | Supported |
|---|---|
| Paper | 1.21.1 and newer |
| Java at runtime | 21 or newer (whatever your Paper version requires) |
| Plugin bytecode | Java 21 (`--release 21`) |
| Paper API compiled against | 1.21.1 (the oldest supported) |
| Folia | **Not supported** in 0.1.x |
| Spigot / Bukkit without Paper | Not supported (the framework uses Paper's Brigadier, scheduler and Adventure APIs) |

The framework uses only Paper API: no NMS and no reflection into server internals.

## Tested matrix

Results of `./gradlew :examples:dual-runtime-test:harness:paperTest` for 0.1.1, run on 2026-10-16. Each cell
boots a real Paper server with the listed plugins (see [Testing](testing.md#real-server-tests) for what every
scenario checks). The number is how many probe checks passed; scenarios without a count pass on log and
plugin-state assertions.

| Scenario | 1.21.1 | 1.21.4 | 1.21.11 | 26.1.2 | 26.3 |
|---|---|---|---|---|---|
| Paper build | 133 | 232 | 132 | 74 | 194 (main) |
| JVM | 21 | 21 | 21 | 25 | 25 |
| `standalone-pair` | ✅ 110 | ✅ 110 | ✅ 110 | ✅ 110 | ✅ 110 |
| `shared-pair` | ✅ 112 | ✅ 112 | ✅ 112 | ✅ 112 | ✅ 112 |
| `mixed` | ✅ 111 | ✅ 111 | ✅ 111 | ✅ 111 | ✅ 111 |
| `shared-missing-runtime` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `shared-incompatible` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `startup-failure` | ✅ 55 | ✅ 55 | ✅ 55 | ✅ 55 | ✅ 55 |
| `gui-client` (real client) | ✅ 8 | ✅ 8 | ✅ 8 | ✅ 8 | ⏭️ skipped¹ |
| `examples-standalone` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `examples-shared` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `starter` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `starter-shared` | ✅ | ✅ | ✅ | ✅ | ✅ |

All jars passed the packaging checks. 54 scenarios passed, 0 failed, 1 skipped.

Test servers run in peaceful mode with fresh worlds, so mobs cannot interfere with the headless client.
[Nookfall Homes](https://nookfall.com/plugins/homes/) runs its own real-server suite on the same versions; see its
repository.

¹ The headless client (Mineflayer with `minecraft-data`) has no protocol data for 26.3 yet. On 26.3, the
server-side probes verify that GUI layouts load and their files are installed; click handling is covered on the
other versions and by unit tests.

CI runs the same matrix, one job per Paper version, on every push to `main` and on pull requests.

## Version notes

- **1.21.1 – 1.21.11** run on Java 21. **26.x** requires Java 25; plugins still compile to Java 21 bytecode and
  run unchanged.
- Integer custom model data (`custom-model-data` in layouts, `ItemBuilder.customModelData`) is deprecated in newer
  Paper APIs but still works on every tested version.
- The 26.3 build tested is from Paper's `main` branch, the newest build available on the test date.
