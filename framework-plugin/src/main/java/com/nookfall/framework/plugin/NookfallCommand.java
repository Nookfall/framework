package com.nookfall.framework.plugin;

import com.nookfall.framework.NookfallRuntime;
import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Execute;
import com.nookfall.framework.command.Permission;
import com.nookfall.framework.command.Subcommand;
import com.nookfall.framework.command.Suggestions;
import com.nookfall.framework.internal.RuntimeRegistry;
import com.nookfall.framework.message.Localization;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

/** Administration of plugins that use the shared runtime. */
@Command(value = "nookfall", description = "Nookfall Framework administration")
@Permission("nookfall.admin")
public final class NookfallCommand {

    private final Localization messages;

    public NookfallCommand(Localization messages) {
        this.messages = messages;
    }

    @Execute
    void version(CommandSender sender) {
        messages.send(
                sender,
                "framework.version",
                com.nookfall.framework.message.Arg.text("version", com.nookfall.framework.Nookfall.version()));
    }

    @Subcommand(value = "plugins", description = "List plugins using the shared runtime")
    void plugins(CommandSender sender) {
        List<NookfallRuntime> runtimes = consumers();
        messages.send(
                sender,
                "framework.plugins-header",
                com.nookfall.framework.message.Arg.number("count", runtimes.size()));
        for (NookfallRuntime runtime : runtimes) {
            messages.send(
                    sender,
                    "framework.plugins-entry",
                    com.nookfall.framework.message.Arg.text(
                            "name", runtime.plugin().getName()),
                    com.nookfall.framework.message.Arg.text(
                            "mode", runtime.mode().name().toLowerCase(Locale.ROOT)));
        }
    }

    @Subcommand(value = "reload", description = "Reload configuration, translations and GUIs of a plugin")
    void reload(
            CommandSender sender,
            @com.nookfall.framework.command.Arg(value = "plugin", suggestions = "plugins") String name) {
        NookfallRuntime runtime = consumers().stream()
                .filter(r -> r.plugin().getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (runtime == null) {
            messages.send(sender, "framework.unknown-plugin", com.nookfall.framework.message.Arg.text("name", name));
            return;
        }
        ReloadResult result = runtime.reload();
        if (result.success()) {
            messages.send(
                    sender,
                    "framework.reloaded",
                    com.nookfall.framework.message.Arg.text(
                            "name", runtime.plugin().getName()));
        } else {
            messages.send(
                    sender,
                    "framework.reload-failed",
                    com.nookfall.framework.message.Arg.text(
                            "name", runtime.plugin().getName()),
                    com.nookfall.framework.message.Arg.text("reason", String.join("; ", result.errors())));
        }
    }

    @Suggestions("plugins")
    List<String> pluginNames(CommandSender sender) {
        return consumers().stream().map(r -> r.plugin().getName()).toList();
    }

    private static List<NookfallRuntime> consumers() {
        return RuntimeRegistry.runtimes();
    }
}
