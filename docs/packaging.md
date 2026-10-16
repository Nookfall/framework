---
title: Packaging
description: Building standalone and shared plugin jars from the same source, the NookfallFramework plugin, version checks and the Gradle plugin settings.
---

The `com.nookfall.framework` Gradle plugin builds two jars from the same compiled classes. Install exactly one of
them on a server.

## Standalone: `MyPlugin-1.0.0.jar`

- Contains your plugin, the framework and its libraries (Avaje Inject, `jakarta.inject`, HikariCP with storage).
- Everything bundled is **relocated** under `<your main package>.libs` (configurable with `relocationPrefix`),
  so two standalone plugins, even on different framework versions, never share or clash on classes.
- Needs nothing else on the server and no network access at startup.
- Built by `./gradlew standaloneJar` (part of `assemble`).

## Shared: `MyPlugin-1.0.0-shared.jar`

- Contains only your classes and generated code. Typically a few tens of kilobytes.
- `plugin.yml` declares `depend: [NookfallFramework]`, so Paper loads the framework first and gives your plugin
  access to its classes.
- Requires the `NookfallFramework` plugin (`framework-plugin/build/libs/NookfallFramework-0.1.1.jar`).
- Built by `./gradlew sharedJar` (part of `assemble`).

The framework classes keep their names (`com.nookfall.framework.*`) and the framework plugin relocates its
libraries to `com.nookfall.framework.libs.*`; shared jars are compiled against those names. This uses Paper's
documented plugin dependency class loading, not Paper's Maven library loader, so every shared plugin sees the
same framework classes.

Each plugin still gets its own runtime: its own beans, configuration, translations, commands, listeners, tasks
and GUI sessions. Disabling one plugin releases only what it owns.

### Version compatibility

Every plugin records the framework version it was compiled against. On startup, a shared plugin checks the
installed `NookfallFramework` version:

- While the framework is `0.x`, the minor version must match and the installed patch must be the same or newer
  (`0.1.0` plugins run on `0.1.x`, not on `0.2.0`).
- From `1.0` on, the installed version must have the same major version and not be older.

An incompatible combination fails that plugin's startup with a message such as
`ProbeIncompatible was built for Nookfall Framework 0.9.0 but version 0.1.0 is installed. Install a
NookfallFramework plugin compatible with 0.9.0 (same minor version, same or newer patch).` Other plugins keep
working.

If `NookfallFramework` is missing entirely, Paper refuses to load the shared plugin and names the missing
dependency `NookfallFramework` in its log.

### The `NookfallFramework` plugin

Besides providing the classes, it offers `/nookfall` (permission `nookfall.admin`):

| Command | Does |
|---|---|
| `/nookfall` | Shows the installed framework version |
| `/nookfall plugins` | Lists plugins using the shared runtime |
| `/nookfall reload <plugin>` | Reloads a plugin's configuration, translations and GUIs |

## Which one to ship

| Ship | When |
|---|---|
| Standalone | Public plugins, single-plugin servers, anything you want to "just work" |
| Shared | Networks running several Nookfall plugins that want smaller jars and one framework update point |

You can publish both; their contents differ only in what is bundled.

## Gradle settings

```kotlin
plugins {
    id("com.nookfall.framework") version "0.1.1"
}

nookfall {
    pluginName = "NookfallHomes"                         // plugin.yml name and jar base name (default: project name)
    mainClass = "com.nookfall.examples.homes.HomesPlugin" // required
    pluginVersion = "1.0.0"                              // default: project version
    description = "Player homes built with Nookfall Framework."
    authors = listOf("Nookfall")
    website = "https://nookfall.com"
    apiVersion = "1.21"                                  // plugin.yml api-version (default 1.21)
    depend = listOf()                                    // NookfallFramework is added to the shared jar automatically
    softDepend = listOf("PlaceholderAPI")
    loadBefore = listOf()
    storage = true                                       // adds framework-storage
    relocationPrefix = "com.nookfall.examples.homes.libs" // default: <main package>.libs
    frameworkVersion = "0.1.1"                           // default: the Gradle plugin's version
    paperVersion = "1.21.1-R0.1-SNAPSHOT"                // Paper API to compile against
    addDependencies = true                               // set false to declare dependencies yourself
    permissions {                                        // written to plugin.yml
        register("homes.use") {
            description = "Use /home"
            default = "true"                             // true, false, op or "not op" (default: op)
        }
        register("homes.admin") {
            children.put("homes.use", true)
        }
    }
}
```

The plugin generates `plugin.yml` for both jars; do not add your own. Declare permissions in `permissions { }`:
Paper treats undeclared permissions as operator-only, so nodes every player should have need `default = "true"`. It also:

- applies the Java 21 toolchain and `--release 21`;
- adds the Paper API (`compileOnly`), the framework, and the Nookfall and Avaje annotation processors;
- passes your resources directory to the processor so translations and bundled GUI layouts are checked at
  compile time;
- disables Shadow's default `shadowJar` task in favour of the two named jars.

## Reproducible jars

Builds use Gradle 9's reproducible archive defaults (fixed timestamps and file order), so building the same
commit twice gives byte-identical jars.

## Checking a jar

The real-server harness inspects every jar it runs. You can do the same by hand:

```sh
unzip -l build/libs/MyPlugin-1.0.0-shared.jar | grep -c com/nookfall/framework/internal   # 0: no framework copy
unzip -p build/libs/MyPlugin-1.0.0-shared.jar plugin.yml                                 # depend: NookfallFramework
unzip -l build/libs/MyPlugin-1.0.0.jar | grep libs/nookfall                              # relocated framework
```
