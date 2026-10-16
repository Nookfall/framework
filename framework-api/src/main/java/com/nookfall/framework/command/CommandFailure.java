package com.nookfall.framework.command;

import com.nookfall.framework.message.Arg;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Thrown from command handlers or argument resolvers to stop execution and show a message to the sender. Messages are
 * looked up in the plugin's translations.
 */
public final class CommandFailure extends RuntimeException {

    private final @Nullable String key;
    private final List<Arg> args;
    private final @Nullable Component component;

    private CommandFailure(@Nullable String key, List<Arg> args, @Nullable Component component) {
        super(key != null ? key : "command failure", null, false, false);
        this.key = key;
        this.args = List.copyOf(args);
        this.component = component;
    }

    /** Fails with a translated message. */
    public static CommandFailure message(String key, Arg... args) {
        return new CommandFailure(key, List.of(args), null);
    }

    /** Fails with an already-rendered component. */
    public static CommandFailure component(Component component) {
        return new CommandFailure(null, List.of(), component);
    }

    public @Nullable String key() {
        return key;
    }

    public List<Arg> args() {
        return args;
    }

    public @Nullable Component component() {
        return component;
    }
}
