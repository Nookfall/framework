plugins {
    id("nookfall.java-conventions")
    id("nookfall.publishing")
}

description = "Nookfall Framework public API: annotations and runtime contracts."

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.jspecify)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("META-INF/nookfall/framework.properties") {
        expand("version" to version)
    }
}
