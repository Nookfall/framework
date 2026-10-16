---
title: Commands
description: Annotation-based Brigadier commands with typed arguments, permissions, suggestions, cooldowns, help and localized errors.
---

Commands are classes annotated with `@Command`. The processor validates them and generates a registration that
the runtime turns into a Brigadier tree, registered through Paper's lifecycle command API. Do not list commands
in `plugin.yml`.

```java
@Command(value = "homes", description = "Manage your homes")
@Aliases({"home"})
@Permission("nookfall.homes")
public final class HomesCommand {

    private final HomeService homes;

    public HomesCommand(HomeService homes) {
        this.homes = homes;
    }

    @Execute
    void root(Player player) {
        homes.openMenu(player);
    }

    @Subcommand(value = "set", description = "Save your location as a home")
    @Permission("nookfall.homes.set")
    void set(Player player, @Arg("name") String name) {
        homes.create(player, name);
    }

    @Subcommand(value = "tp", description = "Teleport to a home")
    @Cooldown(value = "5s", bypassPermission = "nookfall.homes.bypass-cooldown")
    void teleport(Player player, @Arg(value = "name", suggestions = "homes") String name) {
        homes.teleport(player, name);
    }

    @Suggestions("homes")
    List<String> homeNames(CommandSender sender) {
        return sender instanceof Player player ? homes.names(player.getUniqueId()) : List.of();
    }
}
```

(Shortened from `examples/homes-demo`.)

## Structure

| Annotation | On | Meaning |
|---|---|---|
| `@Command("label")` | class | Root label: lower case letters, digits, `_` and `-`. `description` appears in help. |
| `@Aliases({...})` | class | Extra labels for the same tree. |
| `@Execute` | method | Runs for the bare command. Without one, the bare command shows help. |
| `@Subcommand("a b")` | method | Runs for `/label a b ...`. Spaces create nested literals. |
| `@Permission("node")` | class or method | Required to see and run the node. Senders without it do not get it suggested at all. |
| `@Cooldown("5s")` | method | Per-sender cooldown, with optional `bypassPermission`. |

Methods may be package-private. Every command also gets `/label help` (unless you declare a `help` subcommand),
which lists the subcommands the sender may use with their usage and description.

## The sender parameter

The first parameter selects who may run the handler:

| First parameter | Allowed senders |
|---|---|
| `Player` | Players only; others get `nookfall.command.player-only` |
| `ConsoleCommandSender` | Console only; others get `nookfall.command.console-only` |
| `CommandSender` | Anyone |

## Arguments

Every other parameter is an argument and needs `@Arg("name")`. Supported types:

| Type | Input |
|---|---|
| `String` | One word (letters, digits, `_`, `-`, `.`, `+`); add `@Greedy` to the last argument to take the rest of the line |
| `int`, `long`, `double`, `float` (and boxed) | Numbers; `@Range(min, max)` limits them in Brigadier |
| `boolean` | `true` / `false` |
| `Player` | An online player |
| `OfflinePlayer` | A player name the server has seen |
| `World` | A world name |
| `UUID` | A UUID |
| `Duration` | `30s`, `5m`, `1h30m`, `250ms` |
| `Material` | An item material name, such as `diamond_sword` |
| Any enum | Constant names, case-insensitive, suggested automatically |
| Your type | Through an `ArgumentResolver` (below) |

Optional arguments must come after required ones:

- `@Arg(value = "page", optional = true) Integer page`: omitted values are `null` (use boxed types).
- `@Default("1") @Arg("page") int page`: omitted values are parsed from the default text.

Invalid input is answered with a localized message (`nookfall.command.invalid-argument`,
`invalid-enum`, `invalid-duration`, `player-not-found`); the handler does not run.

## Suggestions

`@Arg(suggestions = "name")` refers to a `@Suggestions("name")` method in the same class with the signature
`List<String> name(CommandSender sender)` or `List<String> name(CommandSender sender, String input)`. Results
are filtered by the typed prefix for you. Enums, booleans, players, offline players, worlds, durations and materials suggest automatically.

## Custom argument types

```java
@CommandArgument
public final class TextColorArgument implements ArgumentResolver<TextColor> {

    @Override
    public TextColor parse(CommandSender sender, String input) {
        NamedTextColor named = NamedTextColor.NAMES.value(input.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        if (input.matches("[0-9a-fA-F]{6}")) {
            return TextColor.color(Integer.parseInt(input, 16));
        }
        throw CommandFailure.message("color.invalid", Arg.text("value", input));
    }

    @Override
    public List<String> suggest(CommandSender sender, String input) {
        return List.copyOf(NamedTextColor.NAMES.keys());
    }
}
```

Any handler parameter of type `TextColor` now parses through this resolver. Input is one Brigadier word (letters,
digits, `_`, `-`, `.`, `+`), or the rest of the line with `@Greedy`. Resolvers are beans, so they can inject
services. (`Arg` here is `com.nookfall.framework.message.Arg`, the placeholder type.)

## Failing a command

Throw `CommandFailure.message("translation.key", args...)` from a handler or resolver to stop and send a
translated message, or `CommandFailure.component(component)` for an already-built component. Any other
exception is logged with the handler and sender, and the sender gets `nookfall.command.internal-error`.

## Messages

All framework messages live under `nookfall.command.*` and can be overridden in the plugin's own
`lang/<locale>.yml`. English, German and Polish defaults are built in.

## Compile-time checks

The processor rejects, with the source position:

- duplicate command labels or aliases in one plugin, a class with no handlers, two `@Execute` methods;
- parameters without `@Arg`, unsupported argument types without a resolver, and a sender parameter that is not
  first;
- required arguments after optional ones, optional primitives without `@Default`, `@Greedy` on anything but the
  last argument, `@Range` on non-numeric arguments;
- `@Default` values that do not parse, `suggestions` that name a missing `@Suggestions` method;
- invalid `@Cooldown` durations, labels, argument names and permission nodes.

Two handlers that accept exactly the same input, or one argument name used with different types, are detected
when the command tree is built at startup, which then fails with a message naming both methods.

## Lifecycle

Commands are registered when the runtime starts and removed when it closes. If the plugin is disabled while a
client still has the command tree, running it answers `nookfall.command.unavailable` instead of failing.
