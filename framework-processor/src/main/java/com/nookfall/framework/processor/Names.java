package com.nookfall.framework.processor;

/** Fully qualified names of the types the processor inspects. */
final class Names {

    private Names() {}

    static final String PKG = "com.nookfall.framework.";
    static final String COMMAND = PKG + "command.Command";
    static final String ALIASES = PKG + "command.Aliases";
    static final String PERMISSION = PKG + "command.Permission";
    static final String EXECUTE = PKG + "command.Execute";
    static final String SUBCOMMAND = PKG + "command.Subcommand";
    static final String ARG = PKG + "command.Arg";
    static final String DEFAULT = PKG + "command.Default";
    static final String GREEDY = PKG + "command.Greedy";
    static final String SUGGESTIONS = PKG + "command.Suggestions";
    static final String COOLDOWN = PKG + "command.Cooldown";
    static final String COMMAND_ARGUMENT = PKG + "command.CommandArgument";
    static final String ARGUMENT_RESOLVER = PKG + "command.ArgumentResolver";
    static final String RANGE = PKG + "constraint.Range";
    static final String SIZE = PKG + "constraint.Size";
    static final String NOT_BLANK = PKG + "constraint.NotBlank";
    static final String PATTERN = PKG + "constraint.Pattern";
    static final String LISTENER_COMPONENT = PKG + "listener.ListenerComponent";
    static final String INTEGRATION = PKG + "integration.Integration";
    static final String CONFIG = PKG + "config.Config";
    static final String CODEC = PKG + "config.Codec";
    static final String MESSAGES = PKG + "message.Messages";
    static final String KEY = PKG + "message.Key";
    static final String PARAM = PKG + "message.Param";
    static final String GUI = PKG + "gui.Gui";
    static final String GUI_PROVIDER = PKG + "gui.GuiProvider";
    static final String GUI_ACTION = PKG + "gui.GuiAction";
    static final String GUI_CONDITION = PKG + "gui.GuiCondition";
    static final String GUI_PLACEHOLDER = PKG + "gui.GuiPlaceholder";
    static final String GUI_RENDERER = PKG + "gui.GuiRenderer";
    static final String GUI_CONTEXT = PKG + "gui.GuiContext";
    static final String GUI_CLICK_CONTEXT = PKG + "gui.GuiClickContext";
    static final String GUI_RENDER_CONTEXT = PKG + "gui.GuiRenderContext";
    static final String GUI_ENTRY = PKG + "gui.GuiEntry";
    static final String SCHEDULED = PKG + "task.Scheduled";

    static final String JAVA_PLUGIN = "org.bukkit.plugin.java.JavaPlugin";
    static final String LISTENER = "org.bukkit.event.Listener";
    static final String EVENT_HANDLER = "org.bukkit.event.EventHandler";
    static final String COMMAND_SENDER = "org.bukkit.command.CommandSender";
    static final String CONSOLE_SENDER = "org.bukkit.command.ConsoleCommandSender";
    static final String PLAYER = "org.bukkit.entity.Player";
    static final String WORLD = "org.bukkit.World";
    static final String OFFLINE_PLAYER = "org.bukkit.OfflinePlayer";
    static final String MATERIAL = "org.bukkit.Material";
    static final String ITEM_STACK = "org.bukkit.inventory.ItemStack";
    static final String CLICK_TYPE = "org.bukkit.event.inventory.ClickType";
    static final String AUDIENCE = "net.kyori.adventure.audience.Audience";
    static final String COMPONENT = "net.kyori.adventure.text.Component";

    static final String SINGLETON = "jakarta.inject.Singleton";
    static final String AVAJE_COMPONENT = "io.avaje.inject.Component";
    static final String AVAJE_PROTOTYPE = "io.avaje.inject.Prototype";
    static final String AVAJE_FACTORY = "io.avaje.inject.Factory";
}
