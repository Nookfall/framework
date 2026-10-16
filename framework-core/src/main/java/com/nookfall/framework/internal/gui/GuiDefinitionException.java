package com.nookfall.framework.internal.gui;

import java.util.List;

/** A layout file has errors. */
final class GuiDefinitionException extends RuntimeException {

    private final List<String> problems;

    GuiDefinitionException(String gui, List<String> problems) {
        super("GUI '" + gui + "': " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    List<String> problems() {
        return problems;
    }
}
