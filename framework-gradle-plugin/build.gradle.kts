import java.util.Properties

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

val rootProperties = Properties().apply { file("../gradle.properties").inputStream().use { load(it) } }
group = rootProperties.getProperty("group")
version = rootProperties.getProperty("version")
description = "Gradle plugin that builds standalone and shared Nookfall plugin jars."

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

dependencies {
    implementation(libs.shadow.gradle)
}

gradlePlugin {
    plugins {
        create("nookfall") {
            id = "com.nookfall.framework"
            implementationClass = "com.nookfall.framework.gradle.NookfallGradlePlugin"
            displayName = "Nookfall Framework"
            description = project.description
        }
    }
}

val versions = mapOf(
    "framework" to version.toString(),
    "avaje" to libs.versions.avaje.get(),
    "paper" to libs.versions.paper.get(),
)

tasks.processResources {
    inputs.properties(versions)
    filesMatching("META-INF/nookfall/gradle-plugin.properties") {
        expand(versions)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

publishing {
    repositories {
        // Same repository as the framework modules; see build-logic/src/main/kotlin/nookfall.publishing.gradle.kts.
        val releaseUrl = providers.environmentVariable("NOOKFALL_MAVEN_URL")
        if (releaseUrl.isPresent) {
            maven {
                name = "nookfall"
                url = uri(releaseUrl.get())
                credentials {
                    username = providers.environmentVariable("NOOKFALL_MAVEN_USERNAME").orNull
                    password = providers.environmentVariable("NOOKFALL_MAVEN_PASSWORD").orNull
                }
            }
        }
    }
}
