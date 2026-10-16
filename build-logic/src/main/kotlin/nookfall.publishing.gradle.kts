plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = project.name
                description = provider { project.description ?: "Nookfall Framework module ${project.name}" }
                url = "https://nookfall.com"
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                organization {
                    name = "Nookfall"
                    url = "https://nookfall.com"
                }
                scm {
                    url = "https://github.com/nookfall/framework"
                }
            }
        }
    }
    repositories {
        // Credentials are read from NOOKFALL_MAVEN_URL/USERNAME/PASSWORD when a release is published.
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
