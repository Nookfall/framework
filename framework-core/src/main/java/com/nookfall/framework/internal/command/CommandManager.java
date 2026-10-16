package com.nookfall.framework.internal.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.nookfall.framework.command.ArgumentResolver;
import com.nookfall.framework.command.CommandFailure;
import com.nookfall.framework.internal.i18n.LocalizationImpl;
import com.nookfall.framework.message.Arg;
import com.nookfall.framework.spi.CommandSpec;
import com.nookfall.framework.spi.CommandSpec.Handler;
import com.nookfall.framework.spi.CommandSpec.Param;
import com.nookfall.framework.spi.CommandSpec.SenderKind;
import com.nookfall.framework.util.time.Durations;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Builds Brigadier trees from generated command specs and registers them with Paper. */
public final class CommandManager {

    /** A built command ready for registration. */
    public record Built(
            String name, String description, List<String> aliases, LiteralCommandNode<CommandSourceStack> node) {}

    private enum Kind {
        WORD,
        GREEDY,
        INT,
        LONG,
        DOUBLE,
        FLOAT,
        BOOL,
        PLAYER,
        WORLD,
        UUID,
        CONVERTED
    }

    private final LocalizationImpl localization;
    private final Logger logger;
    private final BooleanSupplier closed;
    private final Converters converters = new Converters();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final List<Built> built = new ArrayList<>();

    public CommandManager(LocalizationImpl localization, Logger logger, BooleanSupplier closed) {
        this.localization = localization;
        this.logger = logger;
        this.closed = closed;
    }

    public <T> void registerResolver(Class<T> type, ArgumentResolver<T> resolver) {
        converters.register(type, resolver);
    }

    /** Builds the command tree for a spec bound to its instance. */
    public <T> Built build(CommandSpec<T> spec, T instance) {
        Node root = new Node(spec.name(), null);
        root.permission = spec.permission();
        for (Handler<T> handler : spec.handlers()) {
            Node node = root;
            for (String literal : handler.path()) {
                node = node.literals.computeIfAbsent(literal, l -> new Node(l, null));
            }
            if (!handler.path().isEmpty() && handler.permission() != null && node.permission == null) {
                node.permission = handler.permission();
            }
            List<Param> params = handler.params();
            for (int i = 0; i <= params.size(); i++) {
                if (allOptional(params, i)) {
                    if (node.executor != null) {
                        throw new IllegalStateException("/" + spec.name() + ": " + handler.method() + " and "
                                + node.executor.handler.method() + " accept the same input");
                    }
                    node.executor = new Executor<>(spec, instance, handler, i);
                }
                if (i < params.size()) {
                    Param param = params.get(i);
                    Node existing = node.arguments.get(param.name());
                    if (existing != null && !existing.param.type().equals(param.type())) {
                        throw new IllegalStateException("/" + spec.name() + ": argument <" + param.name()
                                + "> has different types in different handlers");
                    }
                    node = node.arguments.computeIfAbsent(param.name(), n -> new Node(n, param));
                }
            }
        }
        boolean hasRootHandler = root.executor != null;
        if (!root.literals.containsKey("help")) {
            root.literals.put("help", new Node("help", null));
            root.literals.get("help").help = spec;
        }
        if (!hasRootHandler) {
            root.help = spec;
        }
        @SuppressWarnings("unchecked")
        LiteralArgumentBuilder<CommandSourceStack> builder =
                (LiteralArgumentBuilder<CommandSourceStack>) toBuilder(root, spec, instance);
        Built result = new Built(spec.name(), spec.description(), spec.aliases(), builder.build());
        built.add(result);
        return result;
    }

    /** Registers every built command through Paper's lifecycle API. */
    public void registerAll(JavaPlugin plugin) {
        if (built.isEmpty()) {
            return;
        }
        List<Built> commands = List.copyOf(built);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (Built command : commands) {
                Set<String> labels = registrar.register(command.node(), command.description(), command.aliases());
                if (!labels.contains(command.name())) {
                    logger.warning("/" + command.name() + " is already used by another plugin; use /"
                            + plugin.getName().toLowerCase(Locale.ROOT) + ":" + command.name());
                }
                for (String alias : command.aliases()) {
                    if (!labels.contains(alias)) {
                        logger.warning("Alias /" + alias + " of /" + command.name() + " is already taken");
                    }
                }
            }
        });
    }

    public List<Built> built() {
        return List.copyOf(built);
    }

    // ---- tree ----

    private static final class Node {
        final String name;
        final Param param;
        final Map<String, Node> literals = new LinkedHashMap<>();
        final Map<String, Node> arguments = new LinkedHashMap<>();
        String permission;
        Executor<?> executor;
        CommandSpec<?> help;

        Node(String name, Param param) {
            this.name = name;
            this.param = param;
        }
    }

    private record Executor<T>(CommandSpec<T> spec, T instance, Handler<T> handler, int provided) {}

    private static boolean allOptional(List<Param> params, int from) {
        for (int i = from; i < params.size(); i++) {
            if (!params.get(i).optional()) {
                return false;
            }
        }
        return true;
    }

    private ArgumentBuilder<CommandSourceStack, ?> toBuilder(Node node, CommandSpec<?> spec, Object instance) {
        ArgumentBuilder<CommandSourceStack, ?> builder;
        if (node.param == null) {
            builder = Commands.literal(node.name);
        } else {
            RequiredArgumentBuilder<CommandSourceStack, ?> argument =
                    Commands.argument(node.name, argumentType(node.param));
            SuggestionProvider<CommandSourceStack> suggestions = suggestions(node.param, spec, instance);
            if (suggestions != null) {
                argument.suggests(suggestions);
            }
            builder = argument;
        }
        String permission = node.permission;
        if (permission != null) {
            builder.requires(source -> source.getSender().hasPermission(permission));
        }
        Executor<?> executor = node.executor;
        if (executor != null) {
            builder.executes(context -> execute(context, executor));
        } else if (node.help != null) {
            CommandSpec<?> helpSpec = node.help;
            builder.executes(context -> {
                sendHelp(context.getSource().getSender(), helpSpec);
                return Command.SINGLE_SUCCESS;
            });
        }
        for (Node child : node.literals.values()) {
            builder.then(toBuilder(child, spec, instance));
        }
        for (Node child : node.arguments.values()) {
            builder.then(toBuilder(child, spec, instance));
        }
        return builder;
    }

    private Kind kind(Param param) {
        Class<?> type = param.type();
        if (converters.hasCustom(type)) {
            return Kind.CONVERTED;
        }
        if (type == String.class) {
            return param.greedy() ? Kind.GREEDY : Kind.WORD;
        }
        if (type == int.class || type == Integer.class) return Kind.INT;
        if (type == long.class || type == Long.class) return Kind.LONG;
        if (type == double.class || type == Double.class) return Kind.DOUBLE;
        if (type == float.class || type == Float.class) return Kind.FLOAT;
        if (type == boolean.class || type == Boolean.class) return Kind.BOOL;
        if (type == Player.class) return Kind.PLAYER;
        if (type == World.class) return Kind.WORLD;
        if (type == UUID.class) return Kind.UUID;
        if (converters.find(type) != null) {
            return Kind.CONVERTED;
        }
        throw new IllegalStateException(
                "No argument type for " + type.getName() + "; add an ArgumentResolver annotated with @CommandArgument");
    }

    private ArgumentType<?> argumentType(Param param) {
        double min = param.min();
        double max = param.max();
        return switch (kind(param)) {
            case WORD -> StringArgumentType.word();
            case GREEDY -> StringArgumentType.greedyString();
            case INT ->
                IntegerArgumentType.integer(
                        (int) Math.max(min, Integer.MIN_VALUE), (int) Math.min(max, Integer.MAX_VALUE));
            case LONG ->
                LongArgumentType.longArg((long) Math.max(min, Long.MIN_VALUE), (long) Math.min(max, Long.MAX_VALUE));
            case DOUBLE ->
                DoubleArgumentType.doubleArg(Math.max(min, -Double.MAX_VALUE), Math.min(max, Double.MAX_VALUE));
            case FLOAT ->
                FloatArgumentType.floatArg(
                        (float) Math.max(min, -Float.MAX_VALUE), (float) Math.min(max, Float.MAX_VALUE));
            case BOOL -> BoolArgumentType.bool();
            case PLAYER -> ArgumentTypes.player();
            case WORLD -> ArgumentTypes.world();
            case UUID -> ArgumentTypes.uuid();
            case CONVERTED -> param.greedy() ? StringArgumentType.greedyString() : StringArgumentType.word();
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SuggestionProvider<CommandSourceStack> suggestions(Param param, CommandSpec<?> spec, Object instance) {
        CommandSpec.Suggester suggester =
                param.suggestions() == null ? null : spec.suggesters().get(param.suggestions());
        ArgumentConverter converter = kind(param) == Kind.CONVERTED ? converters.find(param.type()) : null;
        if (suggester == null && converter == null) {
            return null;
        }
        return (context, builder) -> {
            CommandSender sender = context.getSource().getSender();
            String remaining = builder.getRemaining();
            String lower = remaining.toLowerCase(Locale.ROOT);
            try {
                List<String> options = suggester != null
                        ? suggester.suggest(instance, sender, remaining)
                        : converter.suggest(sender, remaining);
                for (String option : options) {
                    if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                        builder.suggest(option);
                    }
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Suggestions for <" + param.name() + "> failed", e);
            }
            return builder.buildFuture();
        };
    }

    // ---- execution ----

    private <T> int execute(CommandContext<CommandSourceStack> context, Executor<T> executor)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        Handler<T> handler = executor.handler();
        String label = executor.spec().name();
        if (closed.getAsBoolean()) {
            localization.send(sender, "nookfall.command.unavailable");
            return 0;
        }
        try {
            CommandSender target = resolveSender(source, handler.sender());
            if (handler.permission() != null && !sender.hasPermission(handler.permission())) {
                throw CommandFailure.message("nookfall.command.no-permission");
            }
            checkCooldown(target, executor);
            List<Param> params = handler.params();
            Object[] args = new Object[params.size()];
            for (int i = 0; i < params.size(); i++) {
                Param param = params.get(i);
                args[i] = i < executor.provided() ? argument(context, param, sender) : defaultValue(param, sender);
            }
            handler.invoker().invoke(executor.instance(), target, args);
            return Command.SINGLE_SUCCESS;
        } catch (CommandFailure failure) {
            report(sender, failure);
            return 0;
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (Throwable t) {
            logger.log(
                    Level.SEVERE,
                    "Command /" + label + " (" + handler.method() + ") failed for " + sender.getName(),
                    t);
            localization.send(sender, "nookfall.command.internal-error");
            return 0;
        }
    }

    private static CommandSender resolveSender(CommandSourceStack source, SenderKind kind) {
        CommandSender sender = source.getSender();
        return switch (kind) {
            case ANY -> sender;
            case PLAYER -> {
                Entity executor = source.getExecutor();
                if (executor instanceof Player player) {
                    yield player;
                }
                if (sender instanceof Player player) {
                    yield player;
                }
                throw CommandFailure.message("nookfall.command.player-only");
            }
            case CONSOLE -> {
                if (sender instanceof ConsoleCommandSender) {
                    yield sender;
                }
                throw CommandFailure.message("nookfall.command.console-only");
            }
        };
    }

    private void checkCooldown(CommandSender sender, Executor<?> executor) {
        Handler<?> handler = executor.handler();
        if (handler.cooldown() == null) {
            return;
        }
        if (handler.cooldownBypass() != null && sender.hasPermission(handler.cooldownBypass())) {
            return;
        }
        String id = sender instanceof Entity entity ? entity.getUniqueId().toString() : sender.getName();
        String key = executor.spec().name() + "#" + handler.method() + "#" + id;
        long now = System.nanoTime();
        Long until = cooldowns.get(key);
        if (until != null && until - now > 0) {
            Duration remaining = Duration.ofNanos(until - now);
            throw CommandFailure.message(
                    "nookfall.command.cooldown",
                    Arg.duration("remaining", remaining.plusMillis(999).withNanos(0)));
        }
        cooldowns.put(key, now + Durations.parse(handler.cooldown()).toNanos());
        if (cooldowns.size() > 10_000) {
            cooldowns.values().removeIf(expiry -> expiry - now <= 0);
        }
    }

    private Object argument(CommandContext<CommandSourceStack> context, Param param, CommandSender sender)
            throws CommandSyntaxException {
        String name = param.name();
        return switch (kind(param)) {
            case WORD, GREEDY -> StringArgumentType.getString(context, name);
            case INT -> context.getArgument(name, Integer.class);
            case LONG -> context.getArgument(name, Long.class);
            case DOUBLE -> context.getArgument(name, Double.class);
            case FLOAT -> context.getArgument(name, Float.class);
            case BOOL -> context.getArgument(name, Boolean.class);
            case WORLD -> context.getArgument(name, World.class);
            case UUID -> context.getArgument(name, UUID.class);
            case PLAYER -> {
                List<Player> players = context.getArgument(name, PlayerSelectorArgumentResolver.class)
                        .resolve(context.getSource());
                if (players.isEmpty()) {
                    throw CommandFailure.message("nookfall.command.player-not-found", Arg.text("value", name));
                }
                yield players.getFirst();
            }
            case CONVERTED ->
                converters.find(param.type()).convert(sender, name, StringArgumentType.getString(context, name));
        };
    }

    private Object defaultValue(Param param, CommandSender sender) {
        String raw = param.defaultValue();
        if (raw == null) {
            return null;
        }
        try {
            return switch (kind(param)) {
                case WORD, GREEDY -> raw;
                case INT -> Integer.parseInt(raw);
                case LONG -> Long.parseLong(raw);
                case DOUBLE -> Double.parseDouble(raw);
                case FLOAT -> Float.parseFloat(raw);
                case BOOL -> Boolean.parseBoolean(raw);
                case UUID -> UUID.fromString(raw);
                case WORLD -> org.bukkit.Bukkit.getWorld(raw);
                case PLAYER -> org.bukkit.Bukkit.getPlayerExact(raw);
                case CONVERTED -> converters.find(param.type()).convert(sender, param.name(), raw);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid default '" + raw + "' for <" + param.name() + ">", e);
        }
    }

    private void report(CommandSender sender, CommandFailure failure) {
        if (failure.component() != null) {
            sender.sendMessage(failure.component());
        } else if (failure.key() != null) {
            localization.send(sender, failure.key(), failure.args().toArray(Arg[]::new));
        }
    }

    // ---- help ----

    private void sendHelp(CommandSender sender, CommandSpec<?> spec) {
        localization.send(sender, "nookfall.command.help-header", Arg.text("command", spec.name()));
        for (String line : usage(spec, sender)) {
            int split = line.indexOf('\u0000');
            String usage = split < 0 ? line : line.substring(0, split);
            String description = split < 0 ? "" : " - " + line.substring(split + 1);
            localization.send(
                    sender,
                    "nookfall.command.help-entry",
                    Arg.text("usage", usage),
                    Arg.text("description", description));
        }
    }

    /** Usage lines visible to the sender; descriptions follow a NUL separator. */
    public static List<String> usage(CommandSpec<?> spec, CommandSender sender) {
        List<String> lines = new ArrayList<>();
        for (Handler<?> handler : spec.handlers()) {
            if (handler.permission() != null && !sender.hasPermission(handler.permission())) {
                continue;
            }
            StringBuilder line = new StringBuilder("/").append(spec.name());
            for (String literal : handler.path()) {
                line.append(' ').append(literal);
            }
            for (Param param : handler.params()) {
                line.append(' ').append(param.optional() ? "[" : "<").append(param.name());
                line.append(param.optional() ? "]" : ">");
            }
            if (!handler.description().isEmpty()) {
                line.append('\u0000').append(handler.description());
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
