package com.example.starter;

import com.nookfall.framework.NookfallRuntime;
import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Execute;
import com.nookfall.framework.command.Permission;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.task.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(value = "starter", description = "Starter plugin commands")
public final class StarterCommand {

    private final GuiManager guis;
    private final VisitRepository visits;
    private final StarterMessages messages;
    private final ConfigRef<StarterConfig> config;
    private final Tasks tasks;
    private final NookfallRuntime runtime;

    public StarterCommand(
            GuiManager guis,
            VisitRepository visits,
            StarterMessages messages,
            ConfigRef<StarterConfig> config,
            Tasks tasks,
            NookfallRuntime runtime) {
        this.guis = guis;
        this.visits = visits;
        this.messages = messages;
        this.config = config;
        this.tasks = tasks;
        this.runtime = runtime;
    }

    @Execute
    void menu(Player player) {
        guis.open(player, "menu");
    }

    @Subcommand(value = "visitors", description = "Show recent visitors")
    void visitors(CommandSender sender) {
        visits.recentVisitors(config.get().visitorsShown())
                .thenAcceptAsync(names -> messages.visitors(sender, String.join(", ", names)), tasks.global().executor());
    }

    @Subcommand(value = "reload", description = "Reload configuration, translations and menus")
    @Permission("starter.admin")
    void reload(CommandSender sender) {
        ReloadResult result = runtime.reload();
        if (result.success()) {
            messages.reloaded(sender);
        } else {
            messages.reloadFailed(sender, String.join("; ", result.errors()));
        }
    }
}
