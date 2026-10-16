plugins {
    id("com.nookfall.framework")
}

nookfall {
    pluginName = "NookfallHomesDemo"
    mainClass = "com.nookfall.examples.homes.HomesPlugin"
    description = "Example: player homes built with Nookfall Framework."
    website = "https://nookfall.com"
    authors = listOf("Nookfall")
    storage = true
}

// Code from docs/ that is not copied from a compiled example; compiled by `check` so the docs match the API.
val docs = sourceSets.create("docs") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
}

tasks.named("check") {
    dependsOn(tasks.named(docs.compileJavaTaskName))
}
