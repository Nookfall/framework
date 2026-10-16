package com.nookfall.framework.util.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.nookfall.framework.util.location.SafeLocations.Safety;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class SafeLocationsTest {

    /** A tiny block world: unknown positions are air. */
    private static final class Blocks implements SafeLocations.BlockView {
        private final Map<List<Integer>, Material> types = new HashMap<>();
        private final Map<List<Integer>, double[]> heights = new HashMap<>();

        Blocks full(Material type, int x, int y, int z) {
            return shaped(type, x, y, z, 1.0);
        }

        /** A block whose collision box spans from its bottom to {@code height}; 0 means no collision. */
        Blocks shaped(Material type, int x, int y, int z, double height) {
            types.put(List.of(x, y, z), type);
            heights.put(List.of(x, y, z), new double[] {height});
            return this;
        }

        Blocks floor(Material type, int y) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    full(type, x, y, z);
                }
            }
            return this;
        }

        @Override
        public Material type(int x, int y, int z) {
            return types.getOrDefault(List.of(x, y, z), Material.AIR);
        }

        @Override
        public List<BoundingBox> collisions(int x, int y, int z) {
            double[] height = heights.get(List.of(x, y, z));
            if (height == null || height[0] <= 0) {
                return List.of();
            }
            return List.of(new BoundingBox(x, y, z, x + 1, y + height[0], z + 1));
        }
    }

    @Test
    void standingOnAFullBlockIsSafe() {
        Blocks world = new Blocks().floor(Material.STONE, 64);
        assertThat(SafeLocations.check(world, 0.5, 65, 0.5)).isEqualTo(Safety.SAFE);
        // Near a block edge the player overlaps the neighbouring column, which is also floor.
        assertThat(SafeLocations.check(world, 0.95, 65, 0.05)).isEqualTo(Safety.SAFE);
    }

    @Test
    void partialBlocksCountAsGround() {
        Blocks slab = new Blocks().floor(Material.STONE, 63).shaped(Material.STONE_SLAB, 0, 64, 0, 0.5);
        assertThat(SafeLocations.check(slab, 0.5, 64.5, 0.5)).isEqualTo(Safety.SAFE);

        Blocks carpet = new Blocks().floor(Material.STONE, 64).shaped(Material.WHITE_CARPET, 0, 65, 0, 0.0625);
        assertThat(SafeLocations.check(carpet, 0.5, 65.0625, 0.5)).isEqualTo(Safety.SAFE);

        Blocks farmland = new Blocks().floor(Material.DIRT, 63).shaped(Material.FARMLAND, 0, 64, 0, 0.9375);
        assertThat(SafeLocations.check(farmland, 0.5, 64.9375, 0.5)).isEqualTo(Safety.SAFE);
    }

    @Test
    void blocksAtFeetOrHeadObstruct() {
        Blocks world = new Blocks().floor(Material.STONE, 64).full(Material.STONE, 0, 65, 0);
        assertThat(SafeLocations.check(world, 0.5, 65, 0.5)).isEqualTo(Safety.OBSTRUCTED);

        Blocks lowCeiling = new Blocks().floor(Material.STONE, 64).full(Material.STONE, 0, 66, 0);
        assertThat(SafeLocations.check(lowCeiling, 0.5, 65, 0.5)).isEqualTo(Safety.OBSTRUCTED);
    }

    @Test
    void passableBlocksDoNotObstruct() {
        Blocks world = new Blocks().floor(Material.GRASS_BLOCK, 64).shaped(Material.SHORT_GRASS, 0, 65, 0, 0);
        assertThat(SafeLocations.check(world, 0.5, 65, 0.5)).isEqualTo(Safety.SAFE);
    }

    @Test
    void airBelowIsNoGround() {
        Blocks world = new Blocks().floor(Material.STONE, 60);
        assertThat(SafeLocations.check(world, 0.5, 65, 0.5)).isEqualTo(Safety.NO_GROUND);
        // Ground within one block below the feet still supports the player.
        assertThat(SafeLocations.check(world, 0.5, 61.8, 0.5)).isEqualTo(Safety.SAFE);
    }

    @Test
    void waterSupportsThePlayer() {
        Blocks world = new Blocks().shaped(Material.WATER, 0, 65, 0, 0).shaped(Material.WATER, 0, 64, 0, 0);
        assertThat(SafeLocations.check(world, 0.5, 65, 0.5)).isEqualTo(Safety.SAFE);
    }

    @Test
    void hazardsAreRejected() {
        Blocks lava = new Blocks().floor(Material.STONE, 64).shaped(Material.LAVA, 0, 65, 0, 0);
        assertThat(SafeLocations.check(lava, 0.5, 65, 0.5)).isEqualTo(Safety.HAZARD);

        Blocks magma = new Blocks().floor(Material.STONE, 64).full(Material.MAGMA_BLOCK, 0, 64, 0);
        assertThat(SafeLocations.check(magma, 0.5, 65, 0.5)).isEqualTo(Safety.HAZARD);

        Blocks lavaPit = new Blocks().floor(Material.STONE, 62).shaped(Material.LAVA, 0, 64, 0, 0);
        assertThat(SafeLocations.check(lavaPit, 0.5, 65, 0.5)).isEqualTo(Safety.NO_GROUND);

        Blocks fire = new Blocks().floor(Material.STONE, 64).shaped(Material.FIRE, 0, 66, 0, 0);
        assertThat(SafeLocations.check(fire, 0.5, 65, 0.5)).isEqualTo(Safety.HAZARD);
    }
}
