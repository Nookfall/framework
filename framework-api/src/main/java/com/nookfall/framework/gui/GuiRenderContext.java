package com.nookfall.framework.gui;

import org.jspecify.annotations.Nullable;

/** Context while an item or title is rendered. */
public interface GuiRenderContext extends GuiContext {

    /** Id of the element being rendered, or {@code null} for the title. */
    @Nullable
    String elementId();

    /** The dynamic entry being rendered, or {@code null} for static elements. */
    @Nullable
    GuiEntry<?> entry();
}
