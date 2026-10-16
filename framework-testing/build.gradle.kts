plugins {
    id("nookfall.java-conventions")
    id("nookfall.publishing")
}

description = "Nookfall Framework test helpers for configuration, translations and commands."

dependencies {
    api(project(":framework-core"))
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
    testAnnotationProcessor(project(":framework-processor"))
    testAnnotationProcessor(libs.avaje.inject.generator)
}
