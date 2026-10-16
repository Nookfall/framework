plugins {
    id("com.nookfall.framework")
}

nookfall {
    pluginName = "ProbeIncompatible"
    mainClass = "com.nookfall.probe.incompatible.IncompatiblePlugin"
}

// Pretend this plugin was compiled against a future framework release.
tasks.compileJava {
    options.compilerArgs.add("-Anookfall.frameworkVersion=0.9.0")
}
