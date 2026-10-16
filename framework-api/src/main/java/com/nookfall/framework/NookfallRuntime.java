package com.nookfall.framework;

import com.nookfall.framework.config.ConfigService;
import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.message.Localization;
import com.nookfall.framework.task.Tasks;
import java.util.Optional;
import org.bukkit.plugin.Plugin;

/**
 * The running framework instance owned by a single plugin.
 *
 * <p>Every resource created through the runtime (beans, listeners, commands, tasks, GUI sessions) belongs to the
 * owning plugin and is released by {@link #close()}.
 */
public interface NookfallRuntime extends AutoCloseable {

    /** The plugin that owns this runtime. */
    Plugin plugin();

    /** Whether the framework classes are bundled in the plugin or provided by the NookfallFramework plugin. */
    RuntimeMode mode();

    /** The framework version this runtime is executing. */
    String frameworkVersion();

    /** Returns the bean of the given type, failing if it does not exist. */
    <T> T get(Class<T> type);

    /** Returns the bean of the given type if it exists. */
    <T> Optional<T> find(Class<T> type);

    Tasks tasks();

    Localization localization();

    ConfigService configs();

    GuiManager guis();

    /**
     * Reloads configurations, translations and GUI definitions. Each part keeps its previous working state when its
     * reload fails.
     */
    ReloadResult reload();

    boolean isClosed();

    /** Releases everything owned by this runtime. Safe to call more than once. */
    @Override
    void close();
}
