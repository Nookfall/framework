---
title: Testing
description: Unit-testing Nookfall plugins without a server, and how the framework itself is tested on real Paper servers.
---

## Testing your plugin

Add `framework-testing` and, for anything that needs Bukkit objects, [MockBukkit](https://mockbukkit.org):

```kotlin
dependencies {
    testImplementation("com.nookfall:framework-testing:0.1.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

(MockBukkit tracks the newest 1.21.x API, hence the newer `paper-api` on the test classpath only.)

### Configuration: `ConfigTesting`

```java
@Test
void defaultsAreDocumented() {
    String yaml = ConfigTesting.defaults(StarterConfig.class);
    assertThat(yaml).contains("# Greet players when they join.\ngreet-on-join: true");
}

@Test
void rejectsOutOfRangeValues() {
    assertThatThrownBy(() -> ConfigTesting.load(StarterConfig.class, "visitors-shown: 500\n"))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("visitors-shown: 500 is above the maximum 50");
}
```

`defaults(type)` returns the YAML a fresh install would get. `load(type, yaml)` returns the loaded value, the
warnings (such as unknown keys) and the YAML that would be written back.

### Translations: `TestLocalization`

```java
private final Localization localization = TestLocalization.fromResources(Path.of("src/main/resources"));

@Test
void playerNamesAreNotParsedAsMarkup() {
    assertThat(TestLocalization.plain(localization.message(Locale.US, "starter.visitors", Arg.text("names", "<red>Eve"))))
            .endsWith("Recent visitors: <red>Eve");
}
```

`fromResources` loads `lang/*.yml` from a resources directory with the same fallback rules as the server.
`plain(component)` flattens a component to text.

### Commands: `CommandTester`

`CommandTester` builds the same Brigadier tree the server would register from the generated registration, so
permissions, sender checks, argument parsing, suggestions and framework error messages behave as on a server:

```java
CommandTester tester = CommandTester.of(new FixtureCommand(), localization);
PlayerMock player = server.addPlayer("Alex");

tester.execute(player, "fixture add 2 5");                  // returns Brigadier's result
assertThat(tester.suggest(player, "fixture paint ")).contains("red", "green");
```

Pass `new CommandTester.Resolver<>(Type.class, resolver)` for custom argument types. Arguments that need server
internals (`Player`, `World` and entity selectors) cannot be parsed here; cover those on a server.

## How the framework is tested

```sh
./gradlew build
```

runs formatting checks, the processor tests (successful and failing compilations of fixture sources, checking
generated code and diagnostics), runtime unit tests on MockBukkit, storage tests against SQLite, and against a
MariaDB container when Docker is available.

### Real-server tests

`examples/dual-runtime-test` contains probe plugins and a harness that downloads Paper builds, boots real
servers and checks the results the probes write:

```sh
./gradlew :examples:dual-runtime-test:harness:paperTest
./gradlew :examples:dual-runtime-test:harness:paperTest -PpaperVersions=1.21.1,26.3 -PpaperScenarios=standalone-pair,gui-client
```

It needs JDK 21 and 25 toolchains (Gradle provisions them), network access to `fill.papermc.io`, and, for the
`gui-client` scenario, Node.js with `npm ci` run in `examples/dual-runtime-test/gui-bot`.

| Scenario | Checks |
|---|---|
| `standalone-pair` | Two standalone plugins with their own relocated frameworks: commands, suggestions, listeners, config, translations, tasks, storage, utilities, disable cleanup |
| `shared-pair` | Two shared plugins on one `NookfallFramework`: class identity, isolated scopes, per-plugin cleanup |
| `mixed` | One shared and one standalone plugin side by side |
| `shared-missing-runtime` | A shared plugin without the framework fails clearly |
| `shared-incompatible` | A shared plugin built for another framework version fails with the version message |
| `startup-failure` | A broken config fails startup and leaves nothing registered |
| `gui-client` | A headless Minecraft client ([Mineflayer](https://github.com/PrismarineJS/mineflayer)) clicks, shift-clicks, number-key swaps, drags, pages and reloads real menus |
| `examples-standalone`, `examples-shared` | The three example plugins boot and work |
| `starter`, `starter-shared` | The starter template, built from published artifacts |

Before booting, the harness inspects every jar: `plugin.yml` declares the framework dependency exactly for shared
jars, shared jars contain no framework or Avaje classes, standalone jars bundle the framework only in relocated
form, and every `META-INF/services` entry (including Avaje's generated module) names a class that is available. Results go to
`examples/dual-runtime-test/harness/build/paper-test-report.md`.
