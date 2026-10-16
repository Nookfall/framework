package com.nookfall.framework.internal.gui;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A validated, immutable GUI layout. */
record GuiDefinition(
        String id,
        int rows,
        String title,
        List<String> layout,
        Map<String, Element> elements,
        Map<String, int[]> slots) {

    enum Type {
        ITEM,
        BUTTON,
        COLLECTION
    }

    /**
     * @param item the displayed item; for collections, the template used for entries
     * @param pageSize entries per page for collections (0 = all slots)
     * @param variants templates for entries that set a variant, by variant name (collections only)
     */
    record Element(
            String id,
            char symbol,
            Type type,
            @Nullable ItemSpec item,
            @Nullable String visibleIf,
            @Nullable String provider,
            int pageSize,
            @Nullable ItemSpec emptyItem,
            @Nullable ItemSpec loadingItem,
            Map<String, ItemSpec> variants) {

        /** The template for an entry: its variant's template when the layout maps it, else the item template. */
        @Nullable
        ItemSpec templateFor(@Nullable String variant) {
            ItemSpec spec = variant == null ? null : variants.get(variant);
            return spec != null ? spec : item;
        }
    }

    int[] slotsOf(String elementId) {
        return slots.getOrDefault(elementId, new int[0]);
    }

    /** The first collection element, used by page actions and conditions without an explicit collection. */
    @Nullable
    String defaultCollection() {
        for (Element element : elements.values()) {
            if (element.type() == Type.COLLECTION) {
                return element.id();
            }
        }
        return null;
    }
}
