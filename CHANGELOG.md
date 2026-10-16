# Changelog

All notable changes to Nookfall Framework are documented here. The project follows
[Semantic Versioning](https://semver.org); while the version is `0.x`, minor versions may contain breaking changes
and shared-mode plugins require the same minor version of the `NookfallFramework` plugin.

## Unreleased

## 0.1.1

Fixes and additions found while building Nookfall Homes, the first plugin on the framework. Shared-mode plugins built
against 0.1.1 need the 0.1.1 `NookfallFramework` plugin; 0.1.0 plugins keep working on it.

### Added

- `GuiEntry.variant(name)` and a collection's `variants` section: entries can be drawn with another template
  (for example unavailable items), styled entirely in YAML. Validated at load and compile time.
- `SafeLocations.check(location)` returning a `Safety` reason (`OUT_OF_BOUNDS`, `OBSTRUCTED`, `NO_GROUND`,
  `HAZARD`, ...); `isSafe` now also checks the world border.
- `Migration.discover(owner, "db/migration")` finds `V<n>__<description>.sql` files in the plugin jar.
- `com.nookfall.framework.util.time.Cooldowns`, in-memory per-UUID cooldowns.
- Gradle plugin: `nookfall { permissions { ... } }` writes permission descriptions, defaults and children to
  `plugin.yml`. Previously every permission was operator-only by default.

### Fixed

- Migration history is now kept per plugin (`nookfall_migrations.owner`). Two plugins sharing one MySQL/MariaDB
  database no longer see each other's versions. 0.1.0 tables are upgraded automatically.
- `SafeLocations` treated slabs, carpets, farmland, paths and similar blocks as unsafe because it only looked at
  whole blocks; it now uses collision shapes.
- Transactions that fail with a deadlock or serialization failure are rolled back and retried (three attempts).
- Commands, listeners, GUIs and argument resolvers declared as nested classes produced uncompilable Avaje code; the
  processor now reports them with a clear error.
- The processor checks that `item-template` names exist under `templates`.
- The real-server harness passes the framework version to its log checks (they matched any version before).
- `GeneratePluginYml` is annotated for Gradle plugin validation, and the Gradle plugin's tests run with `./gradlew build`.
- Docs: `StoredLocation` text format is `world;x;y;z;yaw;pitch`.

### Changed

- The `homes-demo` example plugin is now called `NookfallHomesDemo` so it cannot be confused with Nookfall Homes.

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
