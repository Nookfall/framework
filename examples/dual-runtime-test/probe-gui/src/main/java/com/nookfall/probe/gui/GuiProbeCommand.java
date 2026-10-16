package com.nookfall.probe.gui;

import com.nookfall.framework.NookfallRuntime;
import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.gui.GuiManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Command("guiprobe")
public final class GuiProbeCommand {

    private final GuiManager guis;
    private final NookfallRuntime runtime;
    private final Recorder recorder;

    public GuiProbeCommand(GuiManager guis, NookfallRuntime runtime, Recorder recorder) {
        this.guis = guis;
        this.runtime = runtime;
        this.recorder = recorder;
    }

    private Path layout() {
        return runtime.plugin().getDataFolder().toPath().resolve("guis/probe/layout.yml");
    }

    @Subcommand("prepare")
    void prepare(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(9, new ItemStack(Material.DIAMOND, 8));
        recorder.line("INFO prepared " + player.getName());
    }

    @Subcommand("open")
    void open(Player player) {
        guis.open(player, "probe");
    }

    @Subcommand("slow")
    void slow(Player player) {
        guis.open(player, "slow");
    }

    /** Moves the entries to the top row and the buttons below them. */
    @Subcommand("reload-move")
    void reloadMove(Player player) throws IOException {
        String text = Files.readString(layout());
        Files.writeString(layout(), text.replace("  - \"#########\"\n  - \"NNNNNNNNN\"", "  - \"NNNNNNNNN\"\n  - \"#########\""));
        ReloadResult result = runtime.reload();
        recorder.check("gui-reload-move", result.success(), String.valueOf(result.errors()));
    }

    @Subcommand("reload-broken")
    void reloadBroken(Player player) throws IOException {
        // Four rows declared for a three-row layout.
        Files.writeString(layout(), Files.readString(layout()).replace("rows: 3", "rows: 4"));
        ReloadResult result = runtime.reload();
        recorder.check("gui-reload-broken-rejected", !result.success(), "broken layout was accepted");
    }

    @Subcommand("check-protection")
    void checkProtection(Player player) {
        int diamonds = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND) {
                diamonds += item.getAmount();
            }
        }
        var top = player.getOpenInventory().getTopInventory();
        boolean guiClean = !top.contains(Material.DIAMOND);
        ItemStack hotbar = player.getInventory().getItem(0);
        recorder.check("gui-no-items-moved-in", guiClean, "GUI contains diamonds");
        recorder.check("gui-player-items-kept", diamonds + countCursor(player) == 8, "diamonds=" + diamonds);
        recorder.check("gui-number-key-blocked", hotbar == null || hotbar.getType().isAir(), "hotbar 0 has " + hotbar);
        recorder.check("gui-still-open", guis.openGui(player).filter("probe"::equals).isPresent(), "GUI not open");
    }

    private static int countCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        return cursor.getType() == Material.DIAMOND ? cursor.getAmount() : 0;
    }
}
