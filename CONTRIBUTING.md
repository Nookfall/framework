# Contributing

Thanks for helping. Bug reports, fixes, tests and documentation improvements are all welcome.

## Reporting bugs

Open an issue with the Paper version, Java version, framework version, whether the plugin uses the standalone or
shared jar, the full startup log and the smallest code or YAML that shows the problem. Report security problems
privately as described at <https://nookfall.com/docs/contributing/security/> instead of in a public issue.

## Development setup

- JDK 21 or newer. Gradle provisions the JDK 21 and 25 toolchains the build uses.
- Optional: Docker (MariaDB storage tests), Node.js 22 (real-client GUI tests).

```sh
./gradlew build
```

See [AGENTS.md](AGENTS.md) for build commands, principles and compatibility rules. They apply to all
contributors.

## Pull requests

- Keep each pull request focused on one change and explain the reason for it.
- Add tests that fail without your change. Features that depend on Paper behaviour need a real-server check in
  `examples/dual-runtime-test` as well as unit tests.
- Run `./gradlew spotlessApply build` before pushing. CI also runs the real-server matrix.
- Update `docs/` and add an entry under "Unreleased" in [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
- By contributing you agree that your contribution is licensed under the Apache License 2.0.

## Releasing (maintainers)

1. Set `version` in `gradle.properties`, move the "Unreleased" changelog entries under the new version, and
   update `docs/compatibility.md` with the matrix results from CI.
2. Commit, then tag and push: `git tag v0.1.0 && git push origin v0.1.0`.
3. The `Release` workflow builds and tests the tag and creates a **draft** GitHub release with the
   `NookfallFramework` plugin jar, module jars (with sources and Javadoc) and `SHA256SUMS`. Review it and publish.
4. Maven artifacts are uploaded only when the repository secrets `NOOKFALL_MAVEN_URL`,
   `NOOKFALL_MAVEN_USERNAME` and `NOOKFALL_MAVEN_PASSWORD` are set. To publish by hand instead:

   ```sh
   NOOKFALL_MAVEN_URL=... NOOKFALL_MAVEN_USERNAME=... NOOKFALL_MAVEN_PASSWORD=... \
     ./gradlew publish :framework-gradle-plugin:publish
   ```

   The Gradle plugin can additionally be published to the Gradle Plugin Portal with
   `com.gradle.plugin-publish`, which is not configured yet.
