package com.nookfall.framework.util.location;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * Checks whether a player can stand at a location without suffocating, falling into the void or landing in a hazard.
 *
 * <p>The check uses the real collision shapes of blocks, so standing on slabs, carpets, farmland, paths or snow layers
 * counts as safe. It reads blocks, so call it from the thread that owns the location's region and with the chunk
 * loaded; otherwise the chunk is loaded synchronously.
 */
public final class SafeLocations {

    /** Why a location is or is not safe. */
    public enum Safety {
        SAFE,
        /** The location has no world, or the world is not loaded. */
        NO_WORLD,
        /** Below the world's minimum height, above its build limit, or outside the world border. */
        OUT_OF_BOUNDS,
        /** A player standing there would be inside a block. */
        OBSTRUCTED,
        /** Nothing to stand on within one block below the feet (and no water to float in). */
        NO_GROUND,
        /** Lava, fire, magma, cactus, powder snow or a similar hazard at the feet, head or floor. */
        HAZARD
    }

    /** Player bounding box size in blocks. */
    private static final double HALF_WIDTH = 0.3;

    private static final double HEIGHT = 1.8;
    /** How far below the feet ground still counts as support. */
    private static final double SUPPORT_DEPTH = 1.0;
    /** Ignore boxes that merely touch the player box. */
    private static final double EPSILON = 1.0e-4;

    private SafeLocations() {}

    public static boolean isSafe(Location location) {
        return check(location) == Safety.SAFE;
    }

    /** Checks a location and returns why it is unsafe, or {@link Safety#SAFE}. */
    public static Safety check(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return Safety.NO_WORLD;
        }
        if (location.getY() < world.getMinHeight() || location.getY() + HEIGHT > world.getMaxHeight()) {
            return Safety.OUT_OF_BOUNDS;
        }
        if (!world.getWorldBorder().isInside(location)) {
            return Safety.OUT_OF_BOUNDS;
        }
        return check(new WorldView(world), location.getX(), location.getY(), location.getZ());
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

    /** Block access used by the check, separated so it can be tested without a server. */
    interface BlockView {
        Material type(int x, int y, int z);

        /** Collision boxes in world coordinates. */
        List<BoundingBox> collisions(int x, int y, int z);
    }

    static Safety check(BlockView blocks, double x, double y, double z) {
        BoundingBox body = new BoundingBox(
                x - HALF_WIDTH + EPSILON,
                y + EPSILON,
                z - HALF_WIDTH + EPSILON,
                x + HALF_WIDTH - EPSILON,
                y + HEIGHT - EPSILON,
                z + HALF_WIDTH - EPSILON);
        BoundingBox support = new BoundingBox(
                x - HALF_WIDTH + EPSILON,
                y - SUPPORT_DEPTH,
                z - HALF_WIDTH + EPSILON,
                x + HALF_WIDTH - EPSILON,
                y + EPSILON,
                z + HALF_WIDTH - EPSILON);
        boolean hazard = false;
        boolean obstructed = false;
        for (int[] block : blocksIn(body)) {
            Material type = blocks.type(block[0], block[1], block[2]);
            if (isHazard(type)) {
                hazard = true;
            }
            for (BoundingBox box : blocks.collisions(block[0], block[1], block[2])) {
                if (box.overlaps(body)) {
                    obstructed = true;
                }
            }
        }
        if (obstructed) {
            return Safety.OBSTRUCTED;
        }
        if (hazard) {
            return Safety.HAZARD;
        }
        boolean supported = false;
        for (int[] block : blocksIn(support)) {
            Material type = blocks.type(block[0], block[1], block[2]);
            boolean touches = false;
            for (BoundingBox box : blocks.collisions(block[0], block[1], block[2])) {
                if (box.overlaps(support)) {
                    touches = true;
                }
            }
            if (touches && isHazard(type)) {
                return Safety.HAZARD;
            }
            if (touches || isWater(type)) {
                supported = true;
            }
        }
        // Standing in water is fine: the player floats instead of falling.
        if (!supported && !isWater(blocks.type(floor(x), floor(y), floor(z)))) {
            return Safety.NO_GROUND;
        }
        return Safety.SAFE;
    }

    private static List<int[]> blocksIn(BoundingBox box) {
        List<int[]> out = new ArrayList<>();
        for (int bx = floor(box.getMinX()); bx <= floor(box.getMaxX()); bx++) {
            for (int by = floor(box.getMinY()); by <= floor(box.getMaxY()); by++) {
                for (int bz = floor(box.getMinZ()); bz <= floor(box.getMaxZ()); bz++) {
                    out.add(new int[] {bx, by, bz});
                }
            }
        }
        return out;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static boolean isWater(Material type) {
        return type == Material.WATER || type == Material.BUBBLE_COLUMN;
    }

    static boolean isHazard(Material type) {
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
                    NETHER_PORTAL,
                    END_GATEWAY -> true;
            default -> false;
        };
    }

    private record WorldView(World world) implements BlockView {

        @Override
        public Material type(int x, int y, int z) {
            return world.getBlockAt(x, y, z).getType();
        }

        @Override
        public List<BoundingBox> collisions(int x, int y, int z) {
            Block block = world.getBlockAt(x, y, z);
            List<BoundingBox> boxes = new ArrayList<>();
            for (BoundingBox local : block.getCollisionShape().getBoundingBoxes()) {
                boxes.add(local.clone().shift(x, y, z));
            }
            return boxes;
        }
    }
}
