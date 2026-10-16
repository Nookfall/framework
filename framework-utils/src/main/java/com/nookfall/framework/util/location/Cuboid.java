package com.nookfall.framework.util.location;

import org.bukkit.Location;
import org.bukkit.World;

/** An axis-aligned block region in one world, inclusive on all bounds. */
public record Cuboid(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Cuboid {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Minimum corner must not exceed maximum corner");
        }
    }

    /** Creates the region spanned by two corners in any order. */
    public static Cuboid between(Location a, Location b) {
        World wa = a.getWorld();
        World wb = b.getWorld();
        if (wa == null || wb == null || !wa.getUID().equals(wb.getUID())) {
            throw new IllegalArgumentException("Corners must be in the same world");
        }
        return new Cuboid(
                wa.getName(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contains(Location location) {
        World w = location.getWorld();
        return w != null
                && w.getName().equals(world)
                && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Cuboid other) {
        return world.equals(other.world)
                && minX <= other.maxX
                && maxX >= other.minX
                && minY <= other.maxY
                && maxY >= other.minY
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Returns a region grown by {@code amount} blocks on every side. */
    public Cuboid expand(int amount) {
        return new Cuboid(
                world, minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }
}
