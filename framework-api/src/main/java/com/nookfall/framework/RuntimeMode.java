package com.nookfall.framework;

/** How the framework implementation is packaged for a plugin. */
public enum RuntimeMode {
    /** The framework is shaded and relocated into the plugin jar. */
    STANDALONE,
    /** The framework is provided by the installed NookfallFramework plugin. */
    SHARED
}
