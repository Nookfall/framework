plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

val probes = ":examples:dual-runtime-test"
val jarTasks = listOf(
    ":framework-plugin:shadowJar",
    "$probes:probe-alpha:standaloneJar",
    "$probes:probe-alpha:sharedJar",
    "$probes:probe-beta:standaloneJar",
    "$probes:probe-beta:sharedJar",
    "$probes:probe-incompatible:sharedJar",
    "$probes:probe-gui:standaloneJar",
    ":examples:hello-world:assemble",
    ":examples:homes-demo:assemble",
    ":examples:gui-showcase:assemble",
)

val toolchains = project.extensions.getByType<JavaToolchainService>()
val java21 = toolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
val java25 = toolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
val root = rootProject.layout.projectDirectory
val version = project.version.toString()

/**
 * Runs the probe plugins on real Paper servers. Select versions with -PpaperVersions=1.21.1,26.3 and scenarios with
 * -PpaperScenarios=standalone-pair,shared-pair.
 */
tasks.register<JavaExec>("paperTest") {
    group = "verification"
    description = "Boots Paper servers with the probe plugins in standalone and shared modes."
    dependsOn(jarTasks)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.nookfall.harness.Harness"
    javaLauncher = java21
    val versions = providers.gradleProperty("paperVersions").orElse("1.21.1,1.21.4,1.21.11,26.1.2,26.3")
    val scenarios = providers.gradleProperty("paperScenarios").orElse("all")
    val j21 = java21.map { it.executablePath.asFile.absolutePath }
    val j25 = java25.map { it.executablePath.asFile.absolutePath }
    val work = layout.projectDirectory.dir("../servers").asFile.absolutePath
    val report = layout.buildDirectory.file("paper-test-report.md").map { it.asFile.absolutePath }
    val jars = mapOf(
        "framework" to root.file("framework-plugin/build/libs/NookfallFramework-$version.jar"),
        "alpha.standalone" to root.file("examples/dual-runtime-test/probe-alpha/build/libs/ProbeAlpha-$version.jar"),
        "alpha.shared" to root.file("examples/dual-runtime-test/probe-alpha/build/libs/ProbeAlpha-$version-shared.jar"),
        "beta.standalone" to root.file("examples/dual-runtime-test/probe-beta/build/libs/ProbeBeta-$version.jar"),
        "beta.shared" to root.file("examples/dual-runtime-test/probe-beta/build/libs/ProbeBeta-$version-shared.jar"),
        "incompatible.shared" to root.file(
            "examples/dual-runtime-test/probe-incompatible/build/libs/ProbeIncompatible-$version-shared.jar"),
        "gui.standalone" to root.file("examples/dual-runtime-test/probe-gui/build/libs/ProbeGui-$version.jar"),
        "hello.standalone" to root.file("examples/hello-world/build/libs/NookfallHello-$version.jar"),
        "hello.shared" to root.file("examples/hello-world/build/libs/NookfallHello-$version-shared.jar"),
        "homes.standalone" to root.file("examples/homes-demo/build/libs/NookfallHomes-$version.jar"),
        "homes.shared" to root.file("examples/homes-demo/build/libs/NookfallHomes-$version-shared.jar"),
        "showcase.standalone" to root.file("examples/gui-showcase/build/libs/NookfallGuiShowcase-$version.jar"),
        "showcase.shared" to root.file("examples/gui-showcase/build/libs/NookfallGuiShowcase-$version-shared.jar"),
        // Built separately from published artifacts: ./gradlew publishAllToMavenLocal, then build starter/.
        "starter.standalone" to root.file("starter/build/libs/StarterPlugin-1.0.0.jar"),
        "starter.shared" to root.file("starter/build/libs/StarterPlugin-1.0.0-shared.jar"),
    ).mapValues { it.value.asFile.absolutePath }
    val bot = root.dir("examples/dual-runtime-test/gui-bot").asFile.absolutePath
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--versions=" + versions.get(),
            "--scenarios=" + scenarios.get(),
            "--java21=" + j21.get(),
            "--java25=" + j25.get(),
            "--work=" + work,
            "--report=" + report.get(),
            "--bot=" + bot,
        ) + jars.map { "--jar." + it.key + "=" + it.value }
    })
    outputs.upToDateWhen { false }
}
