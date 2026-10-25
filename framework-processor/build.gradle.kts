plugins {
    id("nookfall.java-conventions")
    id("nookfall.publishing")
}

description = "Nookfall Framework annotation processor: generates registrations and validates declarations."

dependencies {
    implementation(project(":framework-utils")) {
        isTransitive = false
    }
    // Only for NookfallInjectPlugin, which needs nothing beyond Avaje and the JDK.
    implementation(project(":framework-core")) {
        isTransitive = false
    }
    // InjectPlugin SPI, so Avaje's generator knows which beans the framework provides at runtime.
    implementation(libs.avaje.inject)
    implementation(libs.snakeyaml)
    // Placeholders are extracted with the same MiniMessage parser the runtime uses.
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.serializer.plain)

    testImplementation(project(":framework-core"))
    testImplementation(libs.paper.api)
    testImplementation(libs.avaje.inject.generator)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("META-INF/nookfall/processor.properties") {
        expand("version" to version)
    }
}


tasks.register("printTestClasspath") {
    val cp = sourceSets.test.get().runtimeClasspath
    doLast { println("CP=" + cp.asPath) }
}
