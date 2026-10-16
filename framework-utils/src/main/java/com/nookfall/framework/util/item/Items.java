package com.nookfall.framework.util.item;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item serialization using Paper's versioned binary format, which is upgraded by the server when it changes between
 * Minecraft versions.
 */
public final class Items {

    private Items() {}

    /** Serializes a non-empty item to Base64. */
    public static String toBase64(ItemStack item) {
        if (item.getType().isAir() || item.getAmount() <= 0) {
            throw new IllegalArgumentException("Cannot serialize an empty item");
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /**
     * Deserializes an item written by {@link #toBase64(ItemStack)}.
     *
     * @throws IllegalArgumentException if the data is not valid
     */
    public static ItemStack fromBase64(String data) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Item data is not valid Base64", e);
        }
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Item data could not be decoded: " + e.getMessage(), e);
        }
    }

    public static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
