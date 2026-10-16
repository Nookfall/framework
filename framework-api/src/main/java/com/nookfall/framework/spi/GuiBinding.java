package com.nookfall.framework.spi;

import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiRenderContext;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Generated handlers of a {@code @Gui} class. */
public record GuiBinding<T>(
        String id,
        Class<T> type,
        Map<String, Provider<T>> providers,
        Map<String, Action<T>> actions,
        Map<String, Condition<T>> conditions,
        Map<String, Placeholder<T>> placeholders,
        Map<String, Renderer<T>> renderers) {

    /**
     * @param async whether the method returns a {@code CompletableFuture}
     */
    public record Provider<T>(boolean async, ProviderInvoker<T> invoker) {}

    /**
     * @param needsEntry whether the method takes a {@code GuiEntry} and so only works on collection items
     */
    public record Action<T>(boolean needsEntry, ActionInvoker<T> invoker) {}

    @FunctionalInterface
    public interface ProviderInvoker<T> {
        Object provide(T gui, GuiContext context) throws Exception;
    }

    @FunctionalInterface
    public interface ActionInvoker<T> {
        void invoke(T gui, GuiClickContext context, @Nullable GuiEntry<?> entry) throws Exception;
    }

    @FunctionalInterface
    public interface Condition<T> {
        boolean test(T gui, GuiContext context, @Nullable GuiEntry<?> entry) throws Exception;
    }

    @FunctionalInterface
    public interface Placeholder<T> {
        @Nullable
        Object resolve(T gui, GuiRenderContext context) throws Exception;
    }

    @FunctionalInterface
    public interface Renderer<T> {
        ItemStack render(T gui, GuiRenderContext context, ItemStack item) throws Exception;
    }
}
