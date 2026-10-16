package com.nookfall.framework.gui;

import org.bukkit.event.inventory.ClickType;

/** Context for a click on a GUI element. */
public interface GuiClickContext extends GuiContext {

    ClickType clickType();

    /** Id of the clicked element as declared under {@code elements} in the layout. */
    String elementId();

    /** Hotbar button (0-8) for number-key clicks, otherwise -1. */
    int hotbarButton();
}
