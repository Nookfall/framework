plugins {
    id("nookfall.java-conventions")
    id("nookfall.publishing")
}

description = "Nookfall Framework runtime: bootstrap, commands, configuration, localization, GUIs and tasks."

dependencies {
    api(project(":framework-api"))
    api(project(":framework-utils"))
    api(libs.avaje.inject)
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
}
