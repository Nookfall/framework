package com.nookfall.framework.command;

import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * Converts a single word of command input into a value. Annotate implementations with {@link CommandArgument}.
 *
 * @param <T> produced value type
 */
public interface ArgumentResolver<T> {

    /**
     * Parses the input. Throw {@link CommandFailure} with a translation key to report invalid input.
     *
     * @param sender the command sender
     * @param input the raw argument text
     */
    T parse(CommandSender sender, String input);

    /** Tab completions for the given partial input. */
    default List<String> suggest(CommandSender sender, String input) {
        return List.of();
    }
}
