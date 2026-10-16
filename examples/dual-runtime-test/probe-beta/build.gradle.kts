plugins {
    id("com.nookfall.framework")
}

extra["probeName"] = "Beta"
apply(from = rootProject.file("examples/dual-runtime-test/probe.gradle.kts"))

nookfall {
    pluginName = "ProbeBeta"
    mainClass = "com.nookfall.probe.beta.BetaPlugin"
    description = "Nookfall integration probe Beta"
}
