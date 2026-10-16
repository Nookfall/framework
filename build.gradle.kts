plugins {
    base
}

// Examples apply the com.nookfall.framework Gradle plugin, which adds published coordinates. Inside this build
// those coordinates resolve to the local projects.
subprojects {
    if (path.startsWith(":examples")) {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                for (module in listOf("framework-api", "framework-utils", "framework-core", "framework-processor",
                        "framework-storage", "framework-testing")) {
                    if (rootProject.findProject(":$module") != null) {
                        substitute(module("com.nookfall:$module")).using(project(":$module"))
                    }
                }
            }
        }
    }
}

val publishedModules = listOf(
    "framework-api", "framework-utils", "framework-core", "framework-processor", "framework-storage", "framework-testing")

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Installs every framework artifact and the Gradle plugin into the local Maven repository."
    dependsOn(publishedModules.map { ":$it:publishToMavenLocal" })
    dependsOn(gradle.includedBuild("framework-gradle-plugin").task(":publishToMavenLocal"))
}

// The Gradle plugin is an included build; run its tests as part of the main build.
tasks.named("check") {
    dependsOn(gradle.includedBuild("framework-gradle-plugin").task(":check"))
}
