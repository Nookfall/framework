package com.nookfall.framework.util.location;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A location that refers to its world by name, suitable for storage. Resolve it when the world is needed.
 */
public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public StoredLocation {
        if (world.isBlank() || world.contains(";")) {
            throw new IllegalArgumentException("Invalid world name: '" + world + "'");
        }
    }

    public static StoredLocation of(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return new StoredLocation(
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    /**
     * Parses {@code world;x;y;z} or {@code world;x;y;z;yaw;pitch}.
     *
     * @throws IllegalArgumentException if malformed
     */
    public static StoredLocation parse(String text) {
        String[] parts = text.strip().split(";");
        if (parts.length != 4 && parts.length != 6) {
            throw new IllegalArgumentException("Expected world;x;y;z[;yaw;pitch] but got '" + text + "'");
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length == 6 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length == 6 ? Float.parseFloat(parts[5]) : 0f;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Coordinates must be finite: '" + text + "'");
            }
            return new StoredLocation(parts[0], x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in location '" + text + "'", e);
        }
    }

    public String serialize() {
        return String.format(Locale.ROOT, "%s;%s;%s;%s;%s;%s", world, x, y, z, yaw, pitch);
    }

    /** Resolves against loaded worlds; empty if the world is not loaded. */
    public Optional<Location> resolve() {
        World w = Bukkit.getWorld(world);
        return w == null ? Optional.empty() : Optional.of(new Location(w, x, y, z, yaw, pitch));
    }
}
