plugins {
    id("com.nookfall.framework")
}

nookfall {
    pluginName = "NookfallHomes"
    mainClass = "com.nookfall.examples.homes.HomesPlugin"
    description = "Player homes built with Nookfall Framework."
    website = "https://nookfall.com"
    authors = listOf("Nookfall")
    storage = true
}
