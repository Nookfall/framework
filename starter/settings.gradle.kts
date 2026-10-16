pluginManagement {
    repositories {
        // Until Nookfall Framework is published to a public repository, install it locally with
        // ./gradlew publishAllToMavenLocal in the framework repository.
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "starter-plugin"
