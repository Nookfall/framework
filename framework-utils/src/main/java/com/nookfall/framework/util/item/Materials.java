package com.nookfall.framework.util.item;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;

/** Lenient material lookup for configuration and command input. */
public final class Materials {

    /**
     * Materials that Minecraft renamed, in both directions, so a file written for one version still works on another
     * (a name is only translated when the server does not know it).
     */
    private static final Map<String, String> RENAMED = Map.of(
            "CHAIN", "IRON_CHAIN",
            "IRON_CHAIN", "CHAIN",
            "GRASS", "SHORT_GRASS",
            "SHORT_GRASS", "GRASS",
            "SCUTE", "TURTLE_SCUTE",
            "TURTLE_SCUTE", "SCUTE",
            "DISC_FRAGMENT", "DISC_FRAGMENT_5",
            "DISC_FRAGMENT_5", "DISC_FRAGMENT");

    private Materials() {}

    /**
     * Finds a material by name, accepting {@code DIAMOND_SWORD}, {@code diamond_sword} or {@code minecraft:diamond_sword}.
     * Legacy materials are never returned. Names Minecraft has renamed (such as {@code CHAIN} and {@code IRON_CHAIN}) are
     * found under either name.
     */
    public static Optional<Material> find(String name) {
        String key = name.strip();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Optional<Material> found = lookup(key);
        if (found.isEmpty()) {
            String renamed = RENAMED.get(key.toUpperCase(Locale.ROOT).replace(' ', '_'));
            if (renamed != null) {
                found = lookup(renamed);
            }
        }
        return found;
    }

    private static Optional<Material> lookup(String key) {
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
