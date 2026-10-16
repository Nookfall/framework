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
