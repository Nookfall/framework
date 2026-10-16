package com.nookfall.framework.util.item;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Material;

/** Lenient material lookup for configuration and command input. */
public final class Materials {

    private Materials() {}

    /**
     * Finds a material by name, accepting {@code DIAMOND_SWORD}, {@code diamond_sword} or {@code minecraft:diamond_sword}.
     * Legacy materials are never returned.
     */
    public static Optional<Material> find(String name) {
        String key = name.strip();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(key);
        if (material == null) {
            material = Material.matchMaterial(key.toUpperCase(Locale.ROOT).replace(' ', '_'));
        }
        return Optional.ofNullable(material).filter(m -> !isLegacy(m));
    }

    /** Finds a material that can exist as an item stack. */
    public static Optional<Material> findItem(String name) {
        return find(name).filter(Material::isItem);
    }

    /**
     * Like {@link #findItem(String)} but throws a readable message.
     *
     * @throws IllegalArgumentException if unknown or not an item
     */
    public static Material requireItem(String name) {
        return findItem(name).orElseThrow(() -> new IllegalArgumentException("Unknown item material '" + name + "'"));
    }

    @SuppressWarnings("deprecation")
    private static boolean isLegacy(Material material) {
        return material.isLegacy();
    }
}
