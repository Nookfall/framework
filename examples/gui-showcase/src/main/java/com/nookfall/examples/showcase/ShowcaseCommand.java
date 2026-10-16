package com.nookfall.examples.showcase;

import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Execute;
import com.nookfall.framework.command.Permission;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.gui.GuiManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(value = "showcase", description = "Open the GUI showcase")
public final class ShowcaseCommand {

    private final GuiManager guis;

    public ShowcaseCommand(GuiManager guis) {
        this.guis = guis;
    }

    /** "main" has no Java class at all: it is defined entirely in guis/main/layout.yml. */
    @Execute
    void open(Player player) {
        guis.open(player, "main");
    }

    @Subcommand("reload")
    @Permission("nookfall.showcase.reload")
    void reload(CommandSender sender) {
        var result = guis.reload();
        sender.sendMessage(result.success() ? "Menus reloaded." : "Kept previous menus: " + result.errors());
    }
}
