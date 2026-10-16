plugins {
    id("nookfall.java-conventions")
    id("nookfall.publishing")
}

description = "Nookfall Framework utilities: items, locations, durations and players."

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.jspecify)
    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
}
