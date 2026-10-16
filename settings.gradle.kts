pluginManagement {
    includeBuild("build-logic")
    includeBuild("framework-gradle-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

rootProject.name = "nookfall-framework"

include(
    "framework-api",
    "framework-utils",
    "framework-core",
    "framework-processor",
    "framework-storage",
    "framework-plugin",
)


include(
    "examples:hello-world",
    "examples:homes-demo",
    "examples:dual-runtime-test:probe-alpha",
    "examples:dual-runtime-test:probe-beta",
    "examples:dual-runtime-test:probe-incompatible",
    "examples:dual-runtime-test:harness",
)
