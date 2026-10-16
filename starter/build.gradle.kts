plugins {
    id("com.nookfall.framework") version "0.1.0"
}

nookfall {
    // Rename these when you copy the template.
    pluginName = "StarterPlugin"
    mainClass = "com.example.starter.StarterPlugin"
    description = "A plugin built from the Nookfall starter template."
    authors = listOf("You")
    storage = true
}

dependencies {
    testImplementation("com.nookfall:framework-testing:0.1.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
