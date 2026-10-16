package com.nookfall.framework.gui;

import com.nookfall.framework.ReloadResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Player;

/** Opens and manages the configurable GUIs of one plugin. Call from the player's thread. */
public interface GuiManager {

    void open(Player player, String guiId);

    void open(Player player, String guiId, Map<String, Object> arguments);

    /** The GUI id the player currently has open from this plugin. */
    Optional<String> openGui(Player player);

    /** Re-runs providers and redraws the player's GUI if one is open. */
    void refresh(Player player);

    /** Refreshes every open session of a GUI. */
    void refreshAll(String guiId);

    void close(Player player);

    Set<String> guiIds();

    /**
     * Reloads layouts and GUI translations. Invalid definitions are reported and the previously working definition is
     * kept. Open sessions are redrawn.
     */
    ReloadResult reload();
}
