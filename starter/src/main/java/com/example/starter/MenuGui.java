package com.example.starter;

import com.nookfall.framework.gui.Gui;
import com.nookfall.framework.gui.GuiAction;
import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiPlaceholder;
import com.nookfall.framework.gui.GuiRenderContext;
import org.bukkit.Sound;

@Gui("menu")
public final class MenuGui {

    @GuiAction("ping")
    public void ping(GuiClickContext context) {
        context.player().playSound(context.player().getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        context.state().merge("clicks", 1, (a, b) -> (Integer) a + (Integer) b);
    }

    @GuiPlaceholder("clicks")
    public Object clicks(GuiRenderContext context) {
        return context.state().getOrDefault("clicks", 0);
    }
}
