package com.nookfall.framework.util.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Fluent {@link ItemStack} construction with Adventure names and lore. Names and lore are not italic unless the
 * component sets italic explicitly.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final List<Consumer<ItemMeta>> metaChanges = new ArrayList<>();

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        if (!material.isItem()) {
            throw new IllegalArgumentException(material + " is not an item");
        }
        return new ItemBuilder(new ItemStack(material));
    }

    /** Starts from a copy of an existing stack. */
    public static ItemBuilder from(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return this;
    }

    public ItemBuilder name(Component name) {
        Component value = name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return meta(meta -> meta.displayName(value));
    }

    public ItemBuilder lore(List<? extends Component> lines) {
        List<Component> value = new ArrayList<>(lines.size());
        for (Component line : lines) {
            value.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return meta(meta -> meta.lore(value));
    }

    public ItemBuilder lore(Component... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder flags(ItemFlag... flags) {
        return meta(meta -> meta.addItemFlags(flags));
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        return meta(meta -> meta.addEnchant(enchantment, level, true));
    }

    public ItemBuilder enchants(Map<Enchantment, Integer> enchantments) {
        enchantments.forEach(this::enchant);
        return this;
    }

    /** Forces or removes the enchantment glint. */
    public ItemBuilder glow(boolean glow) {
        return meta(meta -> meta.setEnchantmentGlintOverride(glow));
    }

    /** Sets legacy integer custom model data. */
    @SuppressWarnings("deprecation")
    public ItemBuilder customModelData(int data) {
        return meta(meta -> meta.setCustomModelData(data));
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        return meta(meta -> meta.setUnbreakable(unbreakable));
    }

    public <P, C> ItemBuilder data(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        return meta(meta -> meta.getPersistentDataContainer().set(key, type, value));
    }

    /** Applies an arbitrary meta change. */
    public ItemBuilder meta(Consumer<ItemMeta> change) {
        metaChanges.add(change);
        return this;
    }

    public ItemStack build() {
        ItemStack result = item.clone();
        if (!metaChanges.isEmpty()) {
            result.editMeta(meta -> metaChanges.forEach(change -> change.accept(meta)));
        }
        return result;
    }
}
