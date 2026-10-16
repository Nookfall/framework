package __PKG__;

import com.nookfall.framework.command.Aliases;
import com.nookfall.framework.command.Arg;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.CommandFailure;
import com.nookfall.framework.command.Default;
import com.nookfall.framework.command.Execute;
import com.nookfall.framework.command.Greedy;
import com.nookfall.framework.command.Permission;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.command.Suggestions;
import com.nookfall.framework.constraint.Range;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(value = "__LABEL__", description = "Probe command")
@Aliases("__LABEL__-alias")
public final class ProbeCommand {

    enum Mode { FAST, SLOW }

    private final ProbeState state;

    public ProbeCommand(ProbeState state) {
        this.state = state;
    }

    @Execute
    void root(CommandSender sender) {
        sender.sendMessage("probe root");
    }

    @Subcommand(value = "echo", description = "Echo text")
    void echo(CommandSender sender, @Arg(value = "text", suggestions = "words") @Greedy String text) {
        state.lastEcho = text;
    }

    @Subcommand("add")
    void add(CommandSender sender, @Arg("a") int a, @Arg("b") @Default("10") @Range(min = 0, max = 100) int b) {
        state.sum = a + b;
    }

    @Subcommand("mode")
    void mode(CommandSender sender, @Arg("mode") Mode mode) {
        state.mode = mode;
    }

    @Subcommand("player-only")
    void playerOnly(Player player) {}

    @Subcommand("fail")
    void fail(CommandSender sender) {
        throw new IllegalStateException("intentional failure from the probe");
    }

    @Subcommand("refuse")
    void refuse(CommandSender sender) {
        throw CommandFailure.message("probe.refused");
    }

    @Subcommand("secret")
    @Permission("probe.secret.__LABEL__")
    void secret(CommandSender sender) {
        state.secretRan = true;
    }

    @Suggestions("words")
    List<String> words(CommandSender sender) {
        return List.of("hello", "world");
    }
}
