package com.nookfall.framework.spi;

import java.util.List;

/**
 * Registration metadata generated at compile time for one plugin. Implementations are named
 * {@code <MainClass>_NookfallIndex} and located without classpath scanning.
 *
 * <p>This is an internal contract between generated code and the runtime.
 */
public interface PluginIndex {

    /** Framework version the plugin was compiled against. */
    String frameworkVersion();

    List<ListenerSpec> listeners();

    List<CommandSpec<?>> commands();

    List<ArgumentSpec<?>> arguments();

    List<ConfigSpec<?>> configs();

    List<GuiBinding<?>> guis();

    List<ScheduledSpec<?>> scheduled();

    /** Optional integrations; empty for indexes generated before they existed. */
    default List<IntegrationSpec> integrations() {
        return List.of();
    }
}
