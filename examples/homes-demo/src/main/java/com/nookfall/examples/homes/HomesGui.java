package com.nookfall.examples.homes;

import com.nookfall.framework.gui.Gui;
import com.nookfall.framework.gui.GuiAction;
import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiPlaceholder;
import com.nookfall.framework.gui.GuiProvider;
import com.nookfall.framework.gui.GuiRenderContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Gui("homes")
public final class HomesGui {

    private final HomeService homes;

    public HomesGui(HomeService homes) {
        this.homes = homes;
    }

    @GuiProvider("homes")
    public CompletableFuture<List<GuiEntry<Home>>> entries(GuiContext context) {
        return homes.entries(context.player().getUniqueId());
    }

    @GuiAction("home_click")
    public void click(GuiClickContext context, GuiEntry<Home> entry) {
        homes.teleport(context.player(), entry.value());
    }

    @GuiAction("delete_home")
    public void delete(GuiClickContext context, GuiEntry<Home> entry) {
        homes.delete(context.player(), entry.value().name());
    }

    @GuiPlaceholder("home_count")
    public Object count(GuiRenderContext context) {
        return homes.names(context.player().getUniqueId()).size();
    }
}
