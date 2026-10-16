package com.example.starter;

import com.nookfall.framework.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Messages("starter")
public interface StarterMessages {
    void welcome(Player player, int visits);

    void visitors(CommandSender sender, String names);

    void reloaded(CommandSender sender);

    void reloadFailed(CommandSender sender, String reason);
}
