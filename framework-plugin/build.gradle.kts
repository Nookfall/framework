plugins {
    id("nookfall.java-conventions")
    id("nookfall.shadow")
}

description = "The NookfallFramework Paper plugin that provides the shared runtime."

dependencies {
    implementation(project(":framework-core"))
    implementation(project(":framework-storage"))
    compileOnly(libs.paper.api)
    annotationProcessor(project(":framework-processor"))
    annotationProcessor(libs.avaje.inject.generator)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.shadowJar {
    archiveBaseName = "NookfallFramework"
    archiveClassifier = ""
    // Consumer plugins built in shared mode reference these relocated names.
    relocate("io.avaje", "com.nookfall.framework.libs.avaje")
    relocate("jakarta.inject", "com.nookfall.framework.libs.jakarta.inject")
    relocate("com.zaxxer.hikari", "com.nookfall.framework.libs.hikari")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
    dependencies {
        exclude(dependency("org.jspecify:.*:.*"))
        exclude(dependency("org.slf4j:.*:.*"))
    }
    exclude("META-INF/maven/**", "module-info.class", "META-INF/versions/*/module-info.class")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
