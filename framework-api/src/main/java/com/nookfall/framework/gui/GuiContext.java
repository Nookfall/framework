package com.nookfall.framework.gui;

import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The open GUI session of one player. Use only on the thread that delivered it (the player's thread). */
public interface GuiContext {

    Player player();

    Locale locale();

    String guiId();

    /** Values passed to {@link GuiManager#open(Player, String, Map)}. */
    Map<String, Object> arguments();

    /** Returns an argument value, or {@code null}. */
    <V> @Nullable V argument(String key);

    /** Mutable per-session state that survives refreshes and reloads while the GUI stays open. */
    Map<String, Object> state();

    /** Zero-based page of a collection element. */
    int page(String collection);

    void page(String collection, int page);

    /** Re-runs providers and redraws the GUI. */
    void refresh();

    void close();

    /** Opens another GUI for the same player, replacing this one. */
    void open(String guiId, Map<String, Object> arguments);
}
