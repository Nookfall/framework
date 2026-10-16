package com.nookfall.framework.testing.fixture;

import com.nookfall.framework.command.Arg;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.CommandFailure;
import com.nookfall.framework.command.Default;
import com.nookfall.framework.command.Permission;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.command.Suggestions;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("fixture")
public final class FixtureCommand {

    public enum Color {
        RED,
        GREEN
    }

    public final List<String> calls = new ArrayList<>();

    @Subcommand("add")
    void add(CommandSender sender, @Arg("a") int a, @Arg("b") @Default("1") int b) {
        calls.add("add " + (a + b));
    }

    @Subcommand("paint")
    void paint(Player player, @Arg(value = "color", suggestions = "colors") Color color) {
        calls.add("paint " + color);
    }

    @Subcommand("deny")
    void deny(CommandSender sender) {
        throw CommandFailure.message(
                "fixture.denied", com.nookfall.framework.message.Arg.text("who", sender.getName()));
    }

    @Subcommand("admin")
    @Permission("fixture.admin")
    void admin(CommandSender sender) {
        calls.add("admin");
    }

    @Suggestions("colors")
    List<String> colors(CommandSender sender) {
        return List.of("red", "green");
    }
}
