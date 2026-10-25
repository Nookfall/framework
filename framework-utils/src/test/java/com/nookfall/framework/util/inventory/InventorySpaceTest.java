package com.nookfall.framework.util.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class InventorySpaceTest {

    @BeforeEach
    void server() {
        MockBukkit.mock();
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void emptySlotsHoldWholeStacks() {
        ItemStack[] slots = new ItemStack[2];
        assertThat(InventorySpace.fits(slots, Map.of(Material.BONE, 128))).isTrue();
        assertThat(InventorySpace.fits(slots, Map.of(Material.BONE, 129))).isFalse();
    }

    @Test
    void differentItemsShareTheFreeSlots() {
        ItemStack[] slots = new ItemStack[2];
        assertThat(InventorySpace.fits(slots, Map.of(Material.BONE, 1, Material.ARROW, 1)))
                .isTrue();
        assertThat(InventorySpace.fits(slots, Map.of(Material.BONE, 1, Material.ARROW, 1, Material.STRING, 1)))
                .isFalse();
    }

    @Test
    void smallerStackLimitsNeedMoreSlots() {
        ItemStack[] slots = new ItemStack[3];
        assertThat(InventorySpace.fits(slots, new ItemStack(Material.ENDER_PEARL), 48))
                .isTrue(); // 16 per stack
        assertThat(InventorySpace.fits(slots, new ItemStack(Material.ENDER_PEARL), 49))
                .isFalse();
    }

    @Test
    void existingStacksAreToppedUpFirst() {
        ItemStack[] slots = {new ItemStack(Material.STONE, 60)};
        assertThat(InventorySpace.fits(slots, new ItemStack(Material.STONE), 4)).isTrue();
        assertThat(InventorySpace.fits(slots, new ItemStack(Material.STONE), 5)).isFalse();
    }

    @Test
    void roomInAStackIsNotCountedTwice() {
        // One stack with room for 4: two kinds of wanted items cannot both use it, and the same kind asked twice cannot
        // either.
        ItemStack[] slots = {new ItemStack(Material.STONE, 60)};
        assertThat(InventorySpace.fits(slots, Map.of(Material.STONE, 4, Material.DIRT, 1)))
                .isFalse();
    }

    @Test
    void aFullInventoryHoldsNothing() {
        ItemStack[] slots = {new ItemStack(Material.DIRT, 64), new ItemStack(Material.DIRT, 64)};
        assertThat(InventorySpace.fits(slots, Map.of(Material.BONE, 1))).isFalse();
        assertThat(InventorySpace.fits(slots, Map.of())).isTrue();
    }

    @Test
    void roomCountsMatchingStacksAndEmptySlots() {
        ItemStack[] slots = {new ItemStack(Material.STONE, 60), new ItemStack(Material.DIRT, 10), null, null};
        assertThat(InventorySpace.room(slots, new ItemStack(Material.STONE))).isEqualTo(4 + 64 + 64);
        assertThat(InventorySpace.room(slots, new ItemStack(Material.ENDER_PEARL)))
                .isEqualTo(2 * 16);
        assertThat(InventorySpace.room(
                        new ItemStack[] {new ItemStack(Material.DIRT, 64)}, new ItemStack(Material.STONE)))
                .isZero();
    }
}
