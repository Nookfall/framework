package com.nookfall.examples.homes;

import com.nookfall.framework.NookfallRuntime;
import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.command.Aliases;
import com.nookfall.framework.command.Arg;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Cooldown;
import com.nookfall.framework.command.Execute;
import com.nookfall.framework.command.Permission;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.command.Suggestions;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(value = "homes", description = "Manage your homes")
@Aliases({"home"})
@Permission("nookfall.homes")
public final class HomesCommand {

    private final HomeService homes;
    private final NookfallRuntime runtime;

    public HomesCommand(HomeService homes, NookfallRuntime runtime) {
        this.homes = homes;
        this.runtime = runtime;
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

    @Subcommand(value = "delete", description = "Delete a home")
    void delete(Player player, @Arg(value = "name", suggestions = "homes") String name) {
        homes.delete(player, name);
    }

    @Subcommand(value = "tp", description = "Teleport to a home")
    @Cooldown(value = "5s", bypassPermission = "nookfall.homes.bypass-cooldown")
    void teleport(Player player, @Arg(value = "name", suggestions = "homes") String name) {
        homes.teleport(player, name);
    }

    @Subcommand(value = "reload", description = "Reload configuration, translations and menus")
    @Permission("nookfall.homes.admin")
    void reload(CommandSender sender) {
        ReloadResult result = runtime.reload();
        sender.sendMessage(result.success()
                ? "NookfallHomes reloaded."
                : "Reload failed, previous settings kept: " + String.join("; ", result.errors()));
    }

    @Suggestions("homes")
    List<String> homeNames(CommandSender sender) {
        return sender instanceof Player player ? homes.names(player.getUniqueId()) : List.of();
    }
}
