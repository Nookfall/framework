package com.nookfall.framework.internal.gui;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.jspecify.annotations.Nullable;

/**
 * An item as described in a layout file. Text is either literal MiniMessage ({@code name}, {@code lore}) or looked up
 * from translations ({@code translation}: uses {@code <key>.name} and {@code <key>.lore}).
 */
record ItemSpec(
        Material material,
        int amount,
        @Nullable String name,
        @Nullable List<String> lore,
        @Nullable String translation,
        List<ItemFlag> flags,
        Map<String, Integer> enchantments,
        @Nullable Boolean glow,
        @Nullable Integer customModelData,
        @Nullable String renderer,
        @Nullable String action,
        Map<ClickType, String> clickActions) {

    /** The action for a click: an explicit per-click-type action, else the default action for ordinary clicks. */
    @Nullable
    String actionFor(ClickType click) {
        String specific = clickActions.get(click);
        if (specific != null) {
            return specific;
        }
        return switch (click) {
            case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT -> action;
            default -> null;
        };
    }
}
