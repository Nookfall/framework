package com.nookfall.framework.util.location;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Checks whether a player can stand at a location without suffocating, falling into the void or landing in a hazard.
 * Must be called from the thread that owns the location's region.
 */
public final class SafeLocations {

    private SafeLocations() {}

    public static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int y = location.getBlockY();
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return isPassable(feet) && isPassable(head) && isSolidGround(ground);
    }

    /**
     * Searches upwards and then downwards from the location for the nearest safe position in the same column, up to
     * {@code range} blocks away.
     */
    public static Optional<Location> findNearby(Location location, int range) {
        if (isSafe(location)) {
            return Optional.of(location);
        }
        for (int offset = 1; offset <= range; offset++) {
            for (int direction : new int[] {1, -1}) {
                Location candidate = location.clone().add(0, offset * direction, 0);
                if (isSafe(candidate)) {
                    return Optional.of(Locations.centered(candidate));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isPassable(Block block) {
        Material type = block.getType();
        return block.isPassable() && !isHazard(type);
    }

    private static boolean isSolidGround(Block block) {
        Material type = block.getType();
        return type.isSolid() && !isHazard(type);
    }

    private static boolean isHazard(Material type) {
        return switch (type) {
            case LAVA,
                    FIRE,
                    SOUL_FIRE,
                    CAMPFIRE,
                    SOUL_CAMPFIRE,
                    MAGMA_BLOCK,
                    CACTUS,
                    SWEET_BERRY_BUSH,
                    POWDER_SNOW,
                    WITHER_ROSE,
                    POINTED_DRIPSTONE,
                    END_PORTAL,
                    NETHER_PORTAL -> true;
            default -> false;
        };
    }
}
