package com.nookfall.framework.internal.command;

import java.util.List;
import org.bukkit.command.CommandSender;

/** Converts a word of command input for argument types that Brigadier does not handle natively. */
interface ArgumentConverter {

    Object convert(CommandSender sender, String paramName, String input);

    List<String> suggest(CommandSender sender, String input);
}
