package com.nookfall.framework.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * One item of dynamic GUI content. The id must be stable across refreshes so clicks resolve to the right value even
 * when the list changes or the layout is rearranged. Instances are immutable.
 *
 * @param <T> wrapped value type
 */
public final class GuiEntry<T> {

    private final String id;
    private final T value;
    private final Map<String, Object> placeholders;
    private final @Nullable Material material;
    private final @Nullable String variant;

    private GuiEntry(
            String id,
            T value,
            Map<String, Object> placeholders,
            @Nullable Material material,
            @Nullable String variant) {
        this.id = Objects.requireNonNull(id, "id");
        this.value = Objects.requireNonNull(value, "value");
        this.placeholders = placeholders;
        this.material = material;
        this.variant = variant;
    }

    public static <T> GuiEntry<T> of(String id, T value) {
        return new GuiEntry<>(id, value, Map.of(), null, null);
    }

    /** Returns a copy with an additional placeholder, available as {@code <name>} in the item template. */
    public GuiEntry<T> placeholder(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(placeholders);
        copy.put(name, Objects.requireNonNull(value, "value"));
        return new GuiEntry<>(id, this.value, Collections.unmodifiableMap(copy), material, variant);
    }

    /** Returns a copy that overrides the template material. */
    public GuiEntry<T> material(Material material) {
        return new GuiEntry<>(id, value, placeholders, material, variant);
    }

    /**
     * Returns a copy drawn with another item template. The collection's {@code variants} section in the layout maps the
     * variant name to a template; layouts that do not map it draw the entry with the collection's {@code item-template}.
     * Use it for states such as "unavailable" so server owners can style them separately.
     */
    public GuiEntry<T> variant(String variant) {
        if (!variant.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Variant names must match [a-z0-9_-]+: " + variant);
        }
        return new GuiEntry<>(id, value, placeholders, material, variant);
    }

    public String id() {
        return id;
    }

    public T value() {
        return value;
    }

    public Map<String, Object> placeholders() {
        return placeholders;
    }

    public @Nullable Material material() {
        return material;
    }

    /** The variant set with {@link #variant(String)}, or {@code null}. */
    public @Nullable String variant() {
        return variant;
    }

    @Override
    public String toString() {
        return "GuiEntry[" + id + "]";
    }
}
