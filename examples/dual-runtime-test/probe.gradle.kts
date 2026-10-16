// Shared build logic for probe plugins: sources come from probe-template with the package and name substituted.
val probeName = project.extra["probeName"] as String
val probePackage = "com.nookfall.probe." + probeName.lowercase()
val templateDir = rootProject.layout.projectDirectory.dir("examples/dual-runtime-test/probe-template")
val generatedJava = layout.buildDirectory.dir("probe-src/java")
val generatedResources = layout.buildDirectory.dir("probe-src/resources")

val generateProbeSources = tasks.register<Sync>("generateProbeSources") {
    from(templateDir.dir("java")) {
        rename("__NAME__(.*)", probeName + "\$1")
        into(probePackage.replace('.', '/'))
    }
    into(generatedJava)
    inputs.property("probeName", probeName)
    filter<org.apache.tools.ant.filters.ReplaceTokens>(
        "beginToken" to "__",
        "endToken" to "__",
        "tokens" to mapOf("PKG" to probePackage, "NAME" to probeName, "LABEL" to probeName.lowercase()),
    )
}

val generateProbeResources = tasks.register<Sync>("generateProbeResources") {
    from(templateDir.dir("resources"))
    into(generatedResources)
}

the<SourceSetContainer>().named("main") {
    java.srcDir(generateProbeSources.map { generatedJava.get() })
    resources.srcDir(generateProbeResources.map { generatedResources.get() })
}
