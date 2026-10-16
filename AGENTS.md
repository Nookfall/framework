# AGENTS.md

Guidance for anyone, human or agent, changing this repository. Work autonomously: read the code, make the
change, prove it works. No planning documents are needed before coding.

## Principles

- Annotations declare structure, Java implements behaviour, YAML controls presentation, Avaje constructs objects,
  Nookfall integrates with Paper.
- Compile-time generation over runtime scanning. Validate declarations in the processor with precise diagnostics.
- Everything a plugin creates belongs to that plugin and is released by `NookfallRuntime.close()`.
- Reloads build a new state, validate it and swap it in atomically; failures keep the previous state.
- Never parse player-provided text as MiniMessage. Never touch Paper state from async threads.
- Public API lives in `framework-api` (and `framework-utils`, `framework-storage`); `..internal..` may change
  freely. Keep the public surface small and the APIs plain.
- No NMS, no reflection into Minecraft internals, no custom DI container, no modifying Avaje internals.

## Build and test

```sh
./gradlew build                     # compile, Spotless check, all unit/processor tests
./gradlew spotlessApply             # format (Palantir Java Format)
./gradlew :framework-core:test --tests '*GuiRuntimeTest'
./gradlew :examples:dual-runtime-test:harness:paperTest -PpaperVersions=1.21.1,26.3 -PpaperScenarios=standalone-pair
./gradlew publishAllToMavenLocal && (cd starter && ./gradlew build)   # starter against published artifacts
```

`paperTest` downloads Paper builds and needs JDK 21 and 25 toolchains; the `gui-client` scenario also needs
`npm ci` in `examples/dual-runtime-test/gui-bot`. Storage tests use Docker for MariaDB when available.

## Compatibility

- Framework bytecode targets Java 21 (`--release 21`); compile against the oldest supported Paper API
  (`paper` in `gradle/libs.versions.toml`).
- Supported: Paper 1.21.1 and newer. Do not drop a version from `docs/compatibility.md` without saying so in the
  changelog. Use small version adapters when Paper APIs differ.
- Shared-mode plugins depend on class names in `com.nookfall.framework` and `com.nookfall.framework.libs`; changing
  relocations or public signatures breaks them, so treat that as a version bump.

## Rules for changes

- Every behaviour change needs a test that would fail without it: processor fixture tests for diagnostics and
  generation, MockBukkit tests for runtime behaviour, and a probe check in `examples/dual-runtime-test` for
  anything that depends on real Paper (classloading, Brigadier, schedulers, inventories).
- Do not claim something works because it compiles. Run the relevant tests and report actual results.
- Keep docs in `docs/` in sync with behaviour; code examples must match the real API.
- Do not touch unrelated files or revert changes you did not make. Refactor freely where it helps the change.
- Pin dependency versions in `gradle/libs.versions.toml`. Dependencies are checked against
  `gradle/verification-metadata.xml` (SHA-256); after adding or upgrading one, run
  `./gradlew --write-verification-metadata sha256 build publishAllToMavenLocal` and review the diff.
