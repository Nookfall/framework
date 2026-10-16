# Nookfall Framework

A framework for [Paper](https://papermc.io) plugins. **Annotations declare structure, Java implements behaviour,
YAML controls presentation, [Avaje Inject](https://avaje.io/inject/) constructs objects, and Nookfall
integrates everything with Paper.**

```java
@Command(value = "homes", description = "Manage your homes")
@Permission("nookfall.homes")
public final class HomesCommand {

    private final HomeService homes;

    public HomesCommand(HomeService homes) {
        this.homes = homes;
    }

    @Execute
    void root(Player player) {
        homes.openMenu(player);
    }

    @Subcommand("set")
    @Permission("nookfall.homes.set")
    void set(Player player, @Arg("name") String name) {
        homes.create(player, name);
    }
}
```

No registration code, no `plugin.yml` command entries. The processor validates the class at compile time and
generates its registration; the runtime builds a Brigadier tree and registers it with Paper.

## Features

- **Commands** on Paper's Brigadier API: subcommands, aliases, typed and custom arguments, permissions,
  suggestions, cooldowns, generated help, localized errors.
- **Listeners** constructed by Avaje and registered/unregistered automatically, with optional-plugin guards.
- **Configuration** from plain classes: generated commented YAML, validation, codecs, unknown-key warnings, atomic
  reloads that keep the last good snapshot.
- **Localization**: per-player and client locales, fallback chain, MiniMessage with injection-safe placeholders,
  generated typed message interfaces.
- **GUIs** configured in YAML: ASCII layouts, localized items, collections with pagination, async loading,
  actions, conditions, placeholders, renderers, click types, item protection and hot reload.
- **Scheduling** with explicit global/entity/region/async contexts, `Duration` APIs and `@Scheduled` methods whose
  intervals can come from configuration.
- **Storage**: pooled SQLite/MySQL/MariaDB, bounded async executor, transactions, migrations.
- **Utilities** for items, locations, durations and players.
- **Two packaging modes** from one source tree: a standalone jar with the framework relocated inside, or a small
  shared jar that uses the `NookfallFramework` plugin.

## Status

Version **0.1.0**, the first functional release. Tested on real Paper servers 1.21.1, 1.21.4, 1.21.11, 26.1.2 and
26.3 (see [compatibility](docs/compatibility.md)). Artifacts are not yet on a public Maven repository; install
them locally with `./gradlew publishAllToMavenLocal`. Folia is not supported yet.

## Getting started

```sh
./gradlew publishAllToMavenLocal   # install the framework and Gradle plugin into ~/.m2
cp -r starter ~/my-plugin           # a complete template project
cd ~/my-plugin && ./gradlew build   # build/libs/StarterPlugin-1.0.0.jar and -shared.jar
```

Then read the [quick start](docs/quick-start.md).

## Documentation

| | |
|---|---|
| Basics | [Overview](docs/index.md) · [Quick start](docs/quick-start.md) · [Modules](docs/modules.md) · [Dependency injection](docs/dependency-injection.md) |
| Features | [Commands](docs/commands.md) · [Listeners](docs/listeners.md) · [Configuration](docs/configuration.md) · [Localization](docs/localization.md) · [GUIs](docs/guis.md) · [Scheduling](docs/scheduling.md) · [Storage](docs/storage.md) · [Utilities](docs/utilities.md) |
| Shipping | [Packaging](docs/packaging.md) · [Compatibility](docs/compatibility.md) · [Testing](docs/testing.md) · [Troubleshooting](docs/troubleshooting.md) |
| Project | [Contributing](CONTRIBUTING.md) · [Changelog](CHANGELOG.md) · [AGENTS.md](AGENTS.md) |

The docs use Starlight-compatible front matter so they can be published on [nookfall.com](https://nookfall.com).

## Repository layout

```text
framework-api/          public annotations and interfaces
framework-core/         runtime: bootstrap, commands, config, i18n, GUIs, tasks
framework-utils/        item, location, time and player helpers
framework-processor/    annotation processor
framework-storage/      SQL helpers
framework-testing/      unit-test helpers for plugin authors
framework-plugin/       the NookfallFramework plugin (shared runtime)
framework-gradle-plugin/ the com.nookfall.framework Gradle plugin
examples/               hello-world, homes-demo, gui-showcase, dual-runtime-test (real-server harness)
starter/                template project for new plugins
docs/                   documentation
```

## Building

Requires JDK 21+ (Gradle provisions toolchains).

```sh
./gradlew build                                       # everything, with unit and processor tests
./gradlew :examples:dual-runtime-test:harness:paperTest   # real Paper servers (needs network; see docs/testing.md)
```

## License

[Apache License 2.0](LICENSE). Nookfall Framework is an independent open-source project.
