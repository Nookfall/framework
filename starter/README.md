# Nookfall starter plugin

A Paper plugin template built with [Nookfall Framework](https://nookfall.com). It shows a command, a listener,
a configuration class, translations, a YAML-configurable menu, a scheduled task and SQLite persistence.

## Requirements

- JDK 21 or newer (Gradle downloads a JDK 21 toolchain if needed)
- Paper 1.21 or newer

Nookfall Framework 0.1.0 is not yet published to a public Maven repository. Until it is, install it into your
local Maven repository from a checkout of the framework:

```sh
./gradlew publishAllToMavenLocal
```

## Make it yours

1. Rename the package `com.example.starter` and the class `StarterPlugin`.
2. Update the `nookfall { }` block in `build.gradle.kts`: `pluginName`, `mainClass`, `description`, `authors`.
3. Change `group` and `version` in `gradle.properties`.

## Build

```sh
./gradlew build
```

This produces two jars in `build/libs/`:

| Jar | Use it when |
|---|---|
| `StarterPlugin-1.0.0.jar` | Standalone: the framework is bundled and relocated. Works on its own. |
| `StarterPlugin-1.0.0-shared.jar` | Shared: requires the `NookfallFramework` plugin on the server. Smaller. |

Install only one of them.

## Layout

```text
src/main/java/.../StarterPlugin.java     starts and stops the framework
src/main/java/.../StarterCommand.java    /starter, /starter visitors, /starter reload
src/main/java/.../JoinListener.java      records visits and greets players
src/main/java/.../StarterConfig.java     config.yml, generated with comments
src/main/java/.../StarterMessages.java   typed messages from lang/*.yml
src/main/java/.../MenuGui.java           behaviour of guis/menu/layout.yml
src/main/java/.../SummaryJob.java        scheduled task; interval from config.yml
src/main/java/.../VisitRepository.java   asynchronous SQL
src/main/resources/                      translations, menu layout, database migration
src/test/java/                           tests that run without a server
```
