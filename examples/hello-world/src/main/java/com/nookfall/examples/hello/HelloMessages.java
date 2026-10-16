package com.nookfall.examples.hello;

import com.nookfall.framework.message.Messages;
import org.bukkit.command.CommandSender;

@Messages("hello")
public interface HelloMessages {
    void greeting(CommandSender sender, String name);
}
