package com.nookfall.framework.config;

import java.util.List;

/** A configuration file could not be loaded or validated. */
public class ConfigException extends RuntimeException {

    private final List<String> problems;

    public ConfigException(String file, List<String> problems) {
        super(file + ": " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
