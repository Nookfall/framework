# Changelog

All notable changes to Nookfall Framework are documented here. The project follows
[Semantic Versioning](https://semver.org); while the version is `0.x`, minor versions may contain breaking changes
and shared-mode plugins require the same minor version of the `NookfallFramework` plugin.

## Unreleased

## 0.1.0

First functional release.

### Added

- Bootstrap: `Nookfall.start(plugin)` / `NookfallRuntime.close()`, per-plugin Avaje Inject scopes, framework-provided
  beans, cleanup of partially started runtimes, `NookfallStartupException` with readable causes.
- Annotation processor generating command, listener, GUI, configuration, message and scheduling registrations,
  with compile-time diagnostics for invalid declarations, translations and bundled GUI layouts.
- Commands on Paper's Brigadier API: `@Command`, `@Subcommand`, `@Execute`, `@Aliases`, `@Permission`, `@Arg`,
  `@Default`, `@Greedy`, `@Range`, `@Suggestions`, `@Cooldown`, custom `ArgumentResolver`s, generated help and
  localized errors (English, German, Polish).
- `@ListenerComponent` listeners, including `requiresPlugins` for optional integrations.
- Class-based YAML configuration: comments, defaults, validation (`@Range`, `@Size`, `@NotBlank`, `@Pattern`),
  nested sections, lists, maps, enums, durations, items, locations, custom codecs (on fields or types), unknown-key
  warnings, atomic saves and reloads through `ConfigRef`.
- Localization: per-player and client locales, fallback chain, MiniMessage with plain-text placeholders, number
  and duration formatting, `@Messages` typed interfaces, server-owner-editable files with missing-key merging.
- YAML-configurable GUIs: layouts, items, collections with pagination, async providers with loading items and
  stale-result protection, actions, click-type actions, conditions, placeholders, renderers, per-session state,
  item-movement protection, hot reload that keeps the last valid layout.
- Tasks: global, entity, region and async scopes with `Duration` APIs; `@Scheduled` with constant or configured
  intervals and rescheduling on reload.
- Storage: pooled SQLite, MySQL and MariaDB via HikariCP, bounded async executor, transactions, versioned SQL and
  code migrations with checksum checks, graceful shutdown.
- Utilities: `ItemBuilder`, `Items`, `Materials`, `PersistentData`, `StoredLocation`, `Locations`, `Cuboid`,
  `SafeLocations`, `Durations`, `Players`.
- Packaging: the `com.nookfall.framework` Gradle plugin builds a relocated standalone jar and a shared jar for the
  `NookfallFramework` plugin, with generated `plugin.yml` files and framework version checks.
- `framework-testing`: `ConfigTesting`, `TestLocalization`, `CommandTester`.
- Examples (hello-world, homes-demo, gui-showcase), a starter template, and a real-server test harness covering
  standalone, shared and mixed packaging, startup failures and a headless-client GUI test.

### Known limitations

- Not published to a public Maven repository yet; use `./gradlew publishAllToMavenLocal`.
- Folia is not supported.
- The real-client GUI test cannot run on Paper 26.3 because the Mineflayer client does not support that protocol
  yet. On 26.3, server-side probes verify that layouts load, but click handling is only verified on 1.21.1 to
  26.1.2 and by MockBukkit unit tests.
- Re-enabling a disabled plugin instance (plugin managers) is unsupported by Paper; Nookfall fails cleanly.
- Configuration rewrites go through Bukkit's YAML library: comments above keys are kept, but quoting and blank
  lines may change.
