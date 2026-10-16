---
title: Modules
description: The Gradle modules of Nookfall Framework, what each contains and which ones a plugin depends on.
---

All artifacts use the group `com.nookfall` and version `0.1.0`. The Gradle plugin adds the ones a plugin needs,
so most projects never list them by hand.

| Module | Contents | Plugin dependency |
|---|---|---|
| `framework-api` | Annotations and interfaces you program against: `@Command`, `@Config`, `@Gui`, `Tasks`, `Localization`, `ConfigRef`, ... | compile |
| `framework-core` | The runtime: bootstrap (`Nookfall.start`), Brigadier commands, configuration, localization, GUIs, tasks | compile (bundled in standalone jars) |
| `framework-utils` | Item, location, time and player helpers | compile (bundled in standalone jars) |
| `framework-processor` | The annotation processor: validates declarations and generates registrations | `annotationProcessor` only |
| `framework-storage` | Pooled SQL databases, migrations, async queries | optional: `nookfall { storage = true }` |
| `framework-testing` | Unit-test helpers for configs, translations and commands | `testImplementation` |
| `framework-plugin` | The `NookfallFramework` Paper plugin that provides the shared runtime | installed on the server for shared jars |
| `framework-gradle-plugin` | The `com.nookfall.framework` Gradle plugin | `plugins { }` |

The repository also contains:

- `examples/hello-world`, `examples/homes-demo`, `examples/gui-showcase`: example plugins built in this
  repository.
- `examples/dual-runtime-test`: probe plugins and a harness that boots real Paper servers to test packaging,
  classloading and features (see [Testing](testing.md#real-server-tests)).
- `starter/`: an independent template project that consumes the published artifacts.

## Dependencies between modules

```text
framework-api  <-  framework-utils
      ^                 ^
      +--- framework-core ---+
                ^
      framework-storage   framework-testing   framework-plugin
```

`framework-processor` depends on none of them at runtime; it only reads annotation names. There are no cycles.

## Public and internal packages

Everything under `com.nookfall.framework.internal` is implementation detail and may change in any release.
`com.nookfall.framework.spi` contains the types that generated code uses; do not implement them by hand.
Everything else is public API.

## What each plugin gets

A plugin built with the Gradle plugin contains, besides your classes:

- `<MainClass>_NookfallIndex`: the generated entry that `Nookfall.start` looks up.
- `Nookfall_Components` per package: generated command, listener, GUI and scheduling registrations.
- `Nookfall_ConfigRefs` per package: an Avaje factory that provides `ConfigRef<T>` for each `@Config` class.
- Avaje's generated module and its `META-INF/services` entry.
