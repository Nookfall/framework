package com.nookfall.framework.testing;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.nookfall.framework.command.ArgumentResolver;
import com.nookfall.framework.internal.command.CommandManager;
import com.nookfall.framework.internal.i18n.LocalizationImpl;
import com.nookfall.framework.message.Localization;
import com.nookfall.framework.spi.CommandSpec;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

/**
 * Runs a {@code @Command} class through the same Brigadier tree the server would register. Argument types that need
 * server internals (Player, World and UUID selectors) are not available here; test those on a server.
 */
public final class CommandTester {

    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final CommandManager manager;
    private final String label;

    private CommandTester(
            Localization localization, CommandSpec<?> spec, Object instance, List<Resolver<?>> resolvers) {
        this.manager =
                new CommandManager((LocalizationImpl) localization, Logger.getLogger("CommandTester"), () -> false);
        for (Resolver<?> resolver : resolvers) {
            register(resolver);
        }
        this.label = spec.name();
        dispatcher.getRoot().addChild(build(spec, instance));
    }

    /** A custom argument type for commands that use {@code @CommandArgument} resolvers. */
    public record Resolver<T>(Class<T> type, ArgumentResolver<T> resolver) {}

    /**
     * Creates a tester for a command instance. Messages are rendered with the given translations, for example from
     * {@link TestLocalization#fromResources}.
     */
    public static CommandTester of(Object command, Localization localization, Resolver<?>... resolvers) {
        for (CommandSpec<?> spec : Generated.<CommandSpec<?>>specs(command.getClass(), "commands")) {
            if (spec.type() == command.getClass()) {
                return new CommandTester(localization, spec, command, List.of(resolvers));
            }
        }
        throw new IllegalArgumentException(command.getClass().getName() + " is not annotated with @Command");
    }

    private <T> void register(Resolver<T> resolver) {
        manager.registerResolver(resolver.type(), resolver.resolver());
    }

    @SuppressWarnings("unchecked")
    private <T> com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> build(
            CommandSpec<T> spec, Object instance) {
        return manager.build(spec, (T) instance).node();
    }

    /**
     * Executes input such as {@code "homes set base"} (with or without a leading slash).
     *
     * @return 1 on success, 0 when the handler reported a failure to the sender
     * @throws CommandSyntaxException when Brigadier rejects the input, as the server would
     */
    public int execute(CommandSender sender, String input) throws CommandSyntaxException {
        return dispatcher.execute(normalize(input), source(sender));
    }

    /**
     * Tab completions for the input, for example {@code "homes delete "}. Like a client, it omits subcommands the
     * sender cannot use; Brigadier itself does not filter them when suggesting.
     */
    public List<String> suggest(CommandSender sender, String input) {
        String command = normalize(input);
        CommandSourceStack source = source(sender);
        ParseResults<CommandSourceStack> parse = dispatcher.parse(command, source);
        var parent = parse.getContext().findSuggestionContext(command.length()).parent;
        return dispatcher.getCompletionSuggestions(parse).join().getList().stream()
                .map(Suggestion::getText)
                .filter(text -> {
                    var child = parent.getChild(text);
                    return !(child instanceof com.mojang.brigadier.tree.LiteralCommandNode) || child.canUse(source);
                })
                .toList();
    }

    public String label() {
        return label;
    }

    private static String normalize(String input) {
        return input.startsWith("/") ? input.substring(1) : input;
    }

    /** A source stack backed by the sender; implemented dynamically so it works across Paper API versions. */
    private static CommandSourceStack source(CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(
                CommandSourceStack.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getSender" -> sender;
                        case "getExecutor" -> sender instanceof Entity entity ? entity : null;
                        case "getLocation" -> sender instanceof Entity entity ? entity.getLocation() : (Location) null;
                        case "toString" -> "CommandSourceStack[" + sender.getName() + "]";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default ->
                            throw new UnsupportedOperationException(method.getName() + " is not available in tests");
                    };
                });
    }
}
