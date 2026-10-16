package com.nookfall.framework.util.location;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Location serialization and comparison helpers.
 *
 * <p>The text form is {@code world;x;y;z;yaw;pitch}, where {@code world} is the world name. Worlds are resolved
 * lazily: {@link StoredLocation} keeps the name so a location can be stored while its world is unloaded.
 */
public final class Locations {

    private Locations() {}

    /** Serializes a location whose world is set. */
    public static String serialize(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return StoredLocation.of(location).serialize();
    }

    /**
     * Parses and resolves a location.
     *
     * @return empty if the world is not loaded
     * @throws IllegalArgumentException if the text is malformed
     */
    public static Optional<Location> deserialize(String text) {
        return StoredLocation.parse(text).resolve();
    }

    /** Distance check that is false for different worlds, instead of throwing. */
    public static boolean within(Location a, Location b, double distance) {
        return sameWorld(a, b) && a.distanceSquared(b) <= distance * distance;
    }

    /** Horizontal (x/z) distance check, false for different worlds. */
    public static boolean withinHorizontal(Location a, Location b, double distance) {
        if (!sameWorld(a, b)) {
            return false;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz <= distance * distance;
    }

    public static boolean sameWorld(Location a, Location b) {
        World wa = a.getWorld();
        World wb = b.getWorld();
        return wa != null && wb != null && wa.getUID().equals(wb.getUID());
    }

    /** Same block position and world. */
    public static boolean sameBlock(Location a, Location b) {
        return sameWorld(a, b)
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** Center of the block at the location, keeping yaw and pitch. */
    public static Location centered(Location location) {
        Location out = location.clone();
        out.setX(location.getBlockX() + 0.5);
        out.setZ(location.getBlockZ() + 0.5);
        out.setY(location.getBlockY());
        return out;
    }

    /** Looks up a world by UUID or, failing that, by name. */
    public static Optional<World> world(String nameOrId) {
        try {
            World byId = Bukkit.getWorld(UUID.fromString(nameOrId));
            if (byId != null) {
                return Optional.of(byId);
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        return Optional.ofNullable(Bukkit.getWorld(nameOrId));
    }

    /** Short readable form, for example {@code world 10, 64, -3}. */
    public static String describe(Location location) {
        World world = location.getWorld();
        return String.format(
                Locale.ROOT,
                "%s %d, %d, %d",
                world == null ? "?" : world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
