package com.nookfall.framework.util.item;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Short helpers for persistent data on items and other holders. */
public final class PersistentData {

    private PersistentData() {}

    public static NamespacedKey key(Plugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }

    public static <P, C> Optional<C> get(
            PersistentDataHolder holder, NamespacedKey key, PersistentDataType<P, C> type) {
        return Optional.ofNullable(holder.getPersistentDataContainer().get(key, type));
    }

    /** Reads from an item without copying its meta when it has none. */
    public static <P, C> Optional<C> get(ItemStack item, NamespacedKey key, PersistentDataType<P, C> type) {
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                item.getItemMeta().getPersistentDataContainer().get(key, type));
    }

    public static <P, C> void set(ItemStack item, NamespacedKey key, PersistentDataType<P, C> type, C value) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    public static void remove(ItemStack item, NamespacedKey key) {
        item.editMeta(meta -> meta.getPersistentDataContainer().remove(key));
    }

    public static boolean has(ItemStack item, NamespacedKey key) {
        return item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key);
    }
}
