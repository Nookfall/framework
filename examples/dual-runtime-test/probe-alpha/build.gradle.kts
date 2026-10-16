plugins {
    id("com.nookfall.framework")
}

extra["probeName"] = "Alpha"
apply(from = rootProject.file("examples/dual-runtime-test/probe.gradle.kts"))

nookfall {
    pluginName = "ProbeAlpha"
    mainClass = "com.nookfall.probe.alpha.AlphaPlugin"
    description = "Nookfall integration probe Alpha"
}
