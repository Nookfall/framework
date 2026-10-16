package com.nookfall.framework.internal.gui;

import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiRenderContext;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jspecify.annotations.Nullable;

/** Context handed to GUI handlers. */
final class GuiContextImpl implements GuiClickContext, GuiRenderContext {

    private final GuiSession session;
    private final @Nullable String elementId;
    private final @Nullable GuiEntry<?> entry;
    private final ClickType clickType;
    private final int hotbarButton;

    GuiContextImpl(
            GuiSession session,
            @Nullable String elementId,
            @Nullable GuiEntry<?> entry,
            ClickType clickType,
            int hotbarButton) {
        this.session = session;
        this.elementId = elementId;
        this.entry = entry;
        this.clickType = clickType;
        this.hotbarButton = hotbarButton;
    }

    static GuiContextImpl render(GuiSession session, @Nullable String elementId, @Nullable GuiEntry<?> entry) {
        return new GuiContextImpl(session, elementId, entry, ClickType.UNKNOWN, -1);
    }

    @Override
    public Player player() {
        return session.player;
    }

    @Override
    public Locale locale() {
        return session.manager.localization().locale(session.player);
    }

    @Override
    public String guiId() {
        return session.guiId;
    }

    @Override
    public Map<String, Object> arguments() {
        return session.arguments;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> @Nullable V argument(String key) {
        return (V) session.arguments.get(key);
    }

    @Override
    public Map<String, Object> state() {
        return session.state;
    }

    @Override
    public int page(String collection) {
        return session.pages.getOrDefault(collection, 0);
    }

    @Override
    public void page(String collection, int page) {
        session.pages.put(collection, Math.max(0, page));
    }

    @Override
    public void refresh() {
        session.manager.refreshSession(session);
    }

    @Override
    public void close() {
        session.manager.closeSession(session);
    }

    @Override
    public void open(String guiId, Map<String, Object> arguments) {
        session.manager.openFrom(session, guiId, arguments);
    }

    @Override
    public ClickType clickType() {
        return clickType;
    }

    @Override
    public String elementId() {
        return elementId;
    }

    @Override
    public int hotbarButton() {
        return hotbarButton;
    }

    @Override
    public @Nullable GuiEntry<?> entry() {
        return entry;
    }
}
