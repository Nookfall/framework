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

nookfall {
    permissions {
        register("probe.member.beta") {
            description = "Granted to everyone by default"
            default = "true"
            children.put("probe.member.beta.extra", true)
        }
    }
}
