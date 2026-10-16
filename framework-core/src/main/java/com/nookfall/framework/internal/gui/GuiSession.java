package com.nookfall.framework.internal.gui;

import com.nookfall.framework.gui.GuiEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.Nullable;

/**
 * One open GUI for one player. Also the inventory holder, which is how click events are matched to sessions. All
 * mutable state is confined to the player's thread.
 */
final class GuiSession implements InventoryHolder {

    /** What a slot shows, so clicks resolve to elements and entries rather than slot numbers. */
    record SlotBinding(String elementId, @Nullable GuiEntry<?> entry, ItemSpec spec) {}

    final GuiManagerImpl manager;
    final Player player;
    final UUID playerId;
    final String guiId;
    final Map<String, Object> arguments;
    final Map<String, Object> state = new LinkedHashMap<>();
    final Map<String, Integer> pages = new HashMap<>();
    final Map<String, List<GuiEntry<?>>> entries = new HashMap<>();
    final Set<String> loading = new HashSet<>();
    volatile GuiManagerImpl.Loaded loaded;
    volatile boolean active = true;
    volatile long generation;
    Inventory inventory;
    SlotBinding[] slots = new SlotBinding[0];
    long lastClick;
    boolean dispatching;

    GuiSession(GuiManagerImpl manager, Player player, GuiManagerImpl.Loaded loaded, Map<String, Object> arguments) {
        this.manager = manager;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.guiId = loaded.definition().id();
        this.loaded = loaded;
        this.arguments = Map.copyOf(arguments);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
