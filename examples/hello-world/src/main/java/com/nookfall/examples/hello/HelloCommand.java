package com.nookfall.examples.hello;

import com.nookfall.framework.command.Arg;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Execute;
import com.nookfall.framework.config.ConfigRef;
import org.bukkit.command.CommandSender;

@Command(value = "hello", description = "Say hello")
public final class HelloCommand {

    private final HelloMessages messages;
    private final ConfigRef<HelloConfig> config;

    public HelloCommand(HelloMessages messages, ConfigRef<HelloConfig> config) {
        this.messages = messages;
        this.config = config;
    }

    @Execute
    void hello(CommandSender sender, @Arg(value = "name", optional = true) String name) {
        for (int i = 0; i < config.get().repeat(); i++) {
            messages.greeting(sender, name != null ? name : sender.getName());
        }
    }
}
