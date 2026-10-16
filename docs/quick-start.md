---
title: Quick start
description: Install Nookfall Framework, create a plugin from the starter template and build standalone and shared jars.
---

## 1. Install the framework locally

Nookfall Framework 0.1.1 is not published to a public Maven repository yet. Install it into your local Maven
repository from a checkout of the framework:

```sh
git clone https://github.com/nookfall/framework.git
cd framework
./gradlew publishAllToMavenLocal
```

This installs every library module and the `com.nookfall.framework` Gradle plugin under `com.nookfall`,
version `0.1.1`. You need JDK 21 or newer; Gradle downloads a JDK 21 toolchain when needed.

## 2. Copy the starter

The `starter/` directory is a complete, independent Gradle project. Copy it somewhere else and:

1. Rename the package `com.example.starter` and the class `StarterPlugin`.
2. Edit the `nookfall { }` block in `build.gradle.kts` (`pluginName`, `mainClass`, `description`, `authors`).
3. Change `group` and `version` in `gradle.properties`.

The starter contains a command, a listener, a configuration class, translations, a YAML menu, a scheduled job,
SQLite persistence and unit tests that run without a server.

## 3. Build

```sh
./gradlew build
```

`build/libs/` now contains:

| Jar | Install when |
|---|---|
| `StarterPlugin-1.0.0.jar` | You want a single self-contained plugin |
| `StarterPlugin-1.0.0-shared.jar` | The server runs the `NookfallFramework` plugin |

Install **one** of them in the server's `plugins/` folder.

## Starting from an empty project

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.nookfall.framework") version "0.1.1"
}

nookfall {
    pluginName = "MyPlugin"
    mainClass = "com.example.myplugin.MyPlugin"
}
```

The Gradle plugin adds the Paper API, the framework, both annotation processors (Nookfall and Avaje), the Java
21 toolchain, generated `plugin.yml` files and the two jar tasks. The full list of `nookfall { }` settings is in
[Packaging](packaging.md#gradle-settings).

The main class only starts and stops the framework:

```java
public final class MyPlugin extends JavaPlugin {

    private NookfallRuntime runtime;

    @Override
    public void onEnable() {
        runtime = Nookfall.start(this);
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.close();
        }
    }
}
```

`Nookfall.start` loads configuration and translations, constructs your classes, registers commands, listeners
and GUIs and schedules `@Scheduled` methods. If anything fails it releases what it already created and throws
`NookfallStartupException` with a readable message; Paper then disables the plugin.

Add a command:

```java
@Command(value = "hello", description = "Say hello")
public final class HelloCommand {

    @Execute
    void hello(Player player) {
        player.sendMessage(Component.text("Hello, " + player.getName()));
    }
}
```

There is no registration code: the class is found at compile time, constructed by Avaje and registered with
Paper's Brigadier command API. Do not declare commands in `plugin.yml`.
