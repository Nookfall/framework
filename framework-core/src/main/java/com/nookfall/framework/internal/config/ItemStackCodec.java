package com.nookfall.framework.internal.config;

import com.nookfall.framework.config.ConfigCodec;
import com.nookfall.framework.util.item.ItemBuilder;
import com.nookfall.framework.util.item.Items;
import com.nookfall.framework.util.item.Materials;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

/**
 * Items in configuration. A plain material name or a section with {@code material}, {@code amount}, {@code name},
 * {@code lore} (MiniMessage), {@code flags}, {@code enchantments}, {@code glow} and {@code custom-model-data} is
 * readable and hand-editable. Items with anything else (books, potions, components) are written as {@code data}: Paper's
 * versioned Base64 encoding, which survives Minecraft upgrades.
 */
final class ItemStackCodec implements ConfigCodec<ItemStack> {

    private static final Set<String> KEYS =
            Set.of("material", "amount", "name", "lore", "flags", "enchantments", "glow", "custom-model-data");

    @Override
    public ItemStack decode(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            if (raw instanceof Iterable<?>) {
                throw new IllegalArgumentException("expected a material name or an item section");
            }
            return new ItemStack(Materials.requireItem(String.valueOf(raw)));
        }
        if (map.containsKey("data")) {
            if (map.size() != 1) {
                throw new IllegalArgumentException("'data' cannot be combined with other item keys");
            }
            return Items.fromBase64(String.valueOf(map.get("data")));
        }
        for (Object key : map.keySet()) {
            if (!KEYS.contains(String.valueOf(key))) {
                throw new IllegalArgumentException("unknown item key '" + key + "'");
            }
        }
        Object material = map.get("material");
        if (material == null) {
            throw new IllegalArgumentException("item 'material' is required");
        }
        ItemBuilder builder = ItemBuilder.of(Materials.requireItem(String.valueOf(material)));
        if (map.containsKey("amount")) {
            int amount = wholeNumber(map.get("amount"), "amount");
            if (amount < 1 || amount > 99) {
                throw new IllegalArgumentException("item amount must be between 1 and 99");
            }
            builder.amount(amount);
        }
        MiniMessage mini = MiniMessage.miniMessage();
        if (map.get("name") != null) {
            builder.name(mini.deserialize(String.valueOf(map.get("name"))));
        }
        if (map.get("lore") instanceof List<?> lines) {
            builder.lore(lines.stream()
                    .map(line -> mini.deserialize(String.valueOf(line)))
                    .toList());
        } else if (map.get("lore") != null) {
            throw new IllegalArgumentException("item 'lore' must be a list");
        }
        if (map.get("flags") instanceof List<?> flags) {
            for (Object flag : flags) {
                try {
                    builder.flags(ItemFlag.valueOf(String.valueOf(flag).strip().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("unknown item flag '" + flag + "'");
                }
            }
        } else if (map.get("flags") != null) {
            throw new IllegalArgumentException("item 'flags' must be a list");
        }
        if (map.get("enchantments") instanceof Map<?, ?> enchantments) {
            for (Map.Entry<?, ?> entry : enchantments.entrySet()) {
                String name = String.valueOf(entry.getKey());
                int level = wholeNumber(entry.getValue(), "enchantment level");
                if (level < 1) {
                    throw new IllegalArgumentException("enchantment level must be at least 1");
                }
                builder.enchant(enchantment(name), level);
            }
        } else if (map.get("enchantments") != null) {
            throw new IllegalArgumentException("item 'enchantments' must be a section of enchantment: level");
        }
        if (map.get("glow") != null) {
            Object glow = map.get("glow");
            if (!(glow instanceof Boolean value)) {
                throw new IllegalArgumentException("item 'glow' must be true or false");
            }
            builder.glow(value);
        }
        if (map.get("custom-model-data") != null) {
            builder.customModelData(wholeNumber(map.get("custom-model-data"), "custom-model-data"));
        }
        return builder.build();
    }

    @Override
    public Object encode(ItemStack value) {
        Map<String, Object> readable = readable(value);
        if (readable != null) {
            try {
                ItemStack rebuilt = decode(readable);
                if (rebuilt.isSimilar(value) && rebuilt.getAmount() == value.getAmount()) {
                    return readable.size() == 1 ? readable.get("material") : readable;
                }
            } catch (IllegalArgumentException ignored) {
                // falls through to the lossless form
            }
        }
        return Map.of("data", Items.toBase64(value));
    }

    @SuppressWarnings("deprecation")
    private static @Nullable Map<String, Object> readable(ItemStack item) {
        if (item.getType().isAir() || item.getAmount() < 1) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("material", item.getType().name());
        if (item.getAmount() != 1) {
            out.put("amount", item.getAmount());
        }
        if (!item.hasItemMeta()) {
            return out;
        }
        ItemMeta meta = item.getItemMeta();
        MiniMessage mini = MiniMessage.miniMessage();
        if (meta.hasDisplayName()) {
            out.put("name", mini.serialize(stripDefaultItalic(meta.displayName())));
        }
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (Component line : lore) {
                lines.add(mini.serialize(stripDefaultItalic(line)));
            }
            out.put("lore", lines);
        }
        if (!meta.getItemFlags().isEmpty()) {
            out.put(
                    "flags",
                    meta.getItemFlags().stream().map(Enum::name).sorted().toList());
        }
        if (meta.hasEnchants()) {
            Map<String, Object> enchantments = new LinkedHashMap<>();
            meta.getEnchants()
                    .forEach((enchantment, level) -> enchantments.put(
                            enchantment.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)
                                    ? enchantment.getKey().getKey()
                                    : enchantment.getKey().toString(),
                            level));
            out.put("enchantments", enchantments);
        }
        if (meta.hasEnchantmentGlintOverride()) {
            out.put("glow", meta.getEnchantmentGlintOverride());
        }
        if (meta.hasCustomModelData()) {
            out.put("custom-model-data", meta.getCustomModelData());
        }
        return out;
    }

    /** Undoes the explicit non-italic that {@link ItemBuilder} adds, so the written text stays as the author wrote it. */
    private static Component stripDefaultItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                ? component.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET)
                : component;
    }

    private static Enchantment enchantment(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.strip().toLowerCase(Locale.ROOT));
        Enchantment enchantment = key == null
                ? null
                : RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(key);
        if (enchantment == null) {
            throw new IllegalArgumentException("unknown enchantment '" + name + "'");
        }
        return enchantment;
    }

    private static int wholeNumber(Object raw, String what) {
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            return ((Number) raw).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("item " + what + " must be a whole number but got '" + raw + "'");
        }
    }
}
