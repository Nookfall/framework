package com.nookfall.framework.spi;

import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/** A generated command declaration. */
public record CommandSpec<T>(
        Class<T> type,
        String name,
        List<String> aliases,
        @Nullable String permission,
        String description,
        List<Handler<T>> handlers,
        Map<String, Suggester<T>> suggesters) {

    /** Who may run a handler, from the type of its first parameter. */
    public enum SenderKind {
        ANY,
        PLAYER,
        CONSOLE
    }

    /** A handler method bound to a literal path. */
    public record Handler<T>(
            String method,
            List<String> path,
            @Nullable String permission,
            String description,
            SenderKind sender,
            List<Param> params,
            @Nullable String cooldown,
            @Nullable String cooldownBypass,
            Invoker<T> invoker) {}

    /**
     * A command argument.
     *
     * @param defaultValue raw default parsed like user input, or {@code null}
     * @param suggestions name of a suggestions method, or {@code null}
     */
    public record Param(
            String name,
            Class<?> type,
            boolean optional,
            @Nullable String defaultValue,
            boolean greedy,
            double min,
            double max,
            @Nullable String suggestions) {}

    @FunctionalInterface
    public interface Invoker<T> {
        void invoke(T target, CommandSender sender, Object[] args) throws Exception;
    }

    @FunctionalInterface
    public interface Suggester<T> {
        List<String> suggest(T target, CommandSender sender, String input);
    }
}
