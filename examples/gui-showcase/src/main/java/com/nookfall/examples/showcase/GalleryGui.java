package com.nookfall.examples.showcase;

import com.nookfall.framework.gui.Gui;
import com.nookfall.framework.gui.GuiAction;
import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiCondition;
import com.nookfall.framework.gui.GuiContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiPlaceholder;
import com.nookfall.framework.gui.GuiProvider;
import com.nookfall.framework.gui.GuiRenderContext;
import com.nookfall.framework.gui.GuiRenderer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A paged, asynchronously loaded collection with per-session favourites. */
@Gui("gallery")
public final class GalleryGui {

    private static final String FAVOURITES = "favourites";

    @GuiProvider("materials")
    public CompletableFuture<List<GuiEntry<Material>>> materials(GuiContext context) {
        // Pretend this is slow; the layout shows a loading item until it completes.
        return CompletableFuture.supplyAsync(() -> Arrays.stream(Material.values())
                .filter(m -> !m.isLegacy() && m.isItem() && !m.isAir() && m.name().endsWith("_WOOL"))
                .map(m -> GuiEntry.of(m.getKey().getKey(), m)
                        .material(m)
                        .placeholder("material", m.getKey().getKey().replace('_', ' ')))
                .toList());
    }

    @GuiAction("toggle_favourite")
    public void toggle(GuiClickContext context, GuiEntry<Material> entry) {
        Set<String> favourites = favourites(context);
        if (!favourites.remove(entry.id())) {
            favourites.add(entry.id());
        }
    }

    @GuiAction("give")
    public void give(Player player, GuiEntry<Material> entry) {
        player.getInventory().addItem(new ItemStack(entry.value()));
    }

    @GuiAction("clear_favourites")
    public void clear(GuiClickContext context) {
        favourites(context).clear();
    }

    @GuiCondition("has_favourites")
    public boolean hasFavourites(GuiContext context) {
        return !favourites(context).isEmpty();
    }

    @GuiPlaceholder("favourites")
    public Object count(GuiRenderContext context) {
        return favourites(context).size();
    }

    /** Favourite entries glow; everything else is rendered as configured. */
    @GuiRenderer("favourite_glow")
    public ItemStack glow(GuiRenderContext context, ItemStack item) {
        GuiEntry<?> entry = context.entry();
        if (entry != null && favourites(context).contains(entry.id())) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> favourites(GuiContext context) {
        return (Set<String>) context.state().computeIfAbsent(FAVOURITES, k -> new LinkedHashSet<String>());
    }
}
