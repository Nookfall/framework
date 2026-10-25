package com.nookfall.framework.util.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Room in inventories: checking before anything is moved or paid for, and giving items without ever losing them.
 *
 * <p>Only the 36 storage slots of a player inventory count (never armor or the off hand). Checks are conservative: they
 * never say yes when {@link #give} would leave something over. Call on the thread that owns the inventory.
 */
public final class InventorySpace {

    /** Storage slots of a player inventory. */
    public static final int STORAGE_SLOTS = 36;

    private InventorySpace() {}

    /** How many items like {@code template} fit: room in matching stacks plus empty slots. */
    public static int room(Inventory inventory, ItemStack template) {
        return room(slots(inventory), template);
    }

    public static int room(ItemStack[] slots, ItemStack template) {
        int max = Math.max(1, template.getMaxStackSize());
        long room = 0;
        for (ItemStack slot : slots) {
            if (slot == null || slot.getType().isAir()) {
                room += max;
            } else if (slot.isSimilar(template)) {
                room += Math.max(0, max - slot.getAmount());
            }
        }
        return (int) Math.min(room, Integer.MAX_VALUE);
    }

    /** Whether {@code amount} items like {@code template} fit. */
    public static boolean fits(Inventory inventory, ItemStack template, int amount) {
        return fits(slots(inventory), template, amount);
    }

    /** Whether all of these plain items fit together. */
    public static boolean fits(Inventory inventory, Map<Material, Integer> items) {
        return fits(slots(inventory), items);
    }

    public static boolean fits(ItemStack[] slots, ItemStack template, int amount) {
        return fits(slots, List.of(new Want(template, amount)));
    }

    public static boolean fits(ItemStack[] slots, Map<Material, Integer> items) {
        List<Want> wants = new ArrayList<>();
        items.forEach((material, amount) -> wants.add(new Want(new ItemStack(material), amount)));
        return fits(slots, wants);
    }

    private record Want(ItemStack template, int amount) {}

    private static boolean fits(ItemStack[] slots, List<Want> wants) {
        // Room left in each slot for the stack kept there, and empty slots, shared between all wanted items.
        int[] room = new int[slots.length];
        int emptySlots = 0;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null || slots[i].getType().isAir()) {
                emptySlots++;
            }
        }
        for (Want want : wants) {
            int max = Math.max(1, want.template().getMaxStackSize());
            int need = want.amount();
            for (int i = 0; i < slots.length && need > 0; i++) {
                ItemStack slot = slots[i];
                if (slot != null && !slot.getType().isAir() && slot.isSimilar(want.template())) {
                    int free = Math.max(0, max - slot.getAmount()) - room[i];
                    int used = Math.min(free, need);
                    room[i] += used;
                    need -= used;
                }
            }
            if (need > 0) {
                int stacks = (need + max - 1) / max;
                if (stacks > emptySlots) {
                    return false;
                }
                emptySlots -= stacks;
            }
        }
        return true;
    }

    /**
     * Gives items like {@code template} in stacks, topping up existing stacks first.
     *
     * @return how many items did not fit (zero when all were given)
     */
    public static int give(Inventory inventory, ItemStack template, int amount) {
        int max = Math.max(1, template.getMaxStackSize());
        int notGiven = 0;
        for (int remaining = amount; remaining > 0; remaining -= max) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, remaining));
            for (ItemStack left : inventory.addItem(stack).values()) {
                notGiven += left.getAmount();
            }
        }
        return notGiven;
    }

    /** Gives items and drops whatever does not fit at the player's feet, so nothing is ever lost. */
    public static void giveOrDrop(Player player, ItemStack template, int amount) {
        int left = give(player.getInventory(), template, amount);
        int max = Math.max(1, template.getMaxStackSize());
        for (int remaining = left; remaining > 0; remaining -= max) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, remaining));
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private static ItemStack[] slots(Inventory inventory) {
        ItemStack[] slots = new ItemStack[Math.min(STORAGE_SLOTS, inventory.getSize())];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = inventory.getItem(i);
        }
        return slots;
    }
}
