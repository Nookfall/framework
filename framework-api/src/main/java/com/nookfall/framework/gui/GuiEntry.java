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

    private GuiEntry(String id, T value, Map<String, Object> placeholders, @Nullable Material material) {
        this.id = Objects.requireNonNull(id, "id");
        this.value = Objects.requireNonNull(value, "value");
        this.placeholders = placeholders;
        this.material = material;
    }

    public static <T> GuiEntry<T> of(String id, T value) {
        return new GuiEntry<>(id, value, Map.of(), null);
    }

    /** Returns a copy with an additional placeholder, available as {@code <name>} in the item template. */
    public GuiEntry<T> placeholder(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(placeholders);
        copy.put(name, Objects.requireNonNull(value, "value"));
        return new GuiEntry<>(id, this.value, Collections.unmodifiableMap(copy), material);
    }

    /** Returns a copy that overrides the template material. */
    public GuiEntry<T> material(Material material) {
        return new GuiEntry<>(id, value, placeholders, material);
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

    @Override
    public String toString() {
        return "GuiEntry[" + id + "]";
    }
}
