package com.nookfall.examples.docs;

import com.nookfall.examples.homes.Home;
import com.nookfall.examples.homes.HomesPlugin;
import com.nookfall.framework.command.ArgumentResolver;
import com.nookfall.framework.command.CommandArgument;
import com.nookfall.framework.command.CommandFailure;
import com.nookfall.framework.config.Codec;
import com.nookfall.framework.config.ConfigCodec;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.listener.ListenerComponent;
import com.nookfall.framework.message.Arg;
import com.nookfall.framework.message.Localization;
import com.nookfall.framework.storage.Database;
import com.nookfall.framework.storage.Migration;
import com.nookfall.framework.task.Tasks;
import com.nookfall.framework.util.item.ItemBuilder;
import com.nookfall.framework.util.item.PersistentData;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Code shown in docs/ that is not copied from a compiled example. This source set is compiled by {@code check} so
 * the documentation cannot drift from the API. Keep each snippet identical to its page.
 */
final class DocSnippets {

    private DocSnippets() {}

    // docs/commands.md
    @CommandArgument
    public static final class TextColorArgument implements ArgumentResolver<TextColor> {

        @Override
        public TextColor parse(CommandSender sender, String input) {
            NamedTextColor named = NamedTextColor.NAMES.value(input.toLowerCase(Locale.ROOT));
            if (named != null) {
                return named;
            }
            if (input.matches("[0-9a-fA-F]{6}")) {
                return TextColor.color(Integer.parseInt(input, 16));
            }
            throw CommandFailure.message("color.invalid", Arg.text("value", input));
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return List.copyOf(NamedTextColor.NAMES.keys());
        }
    }

    // docs/listeners.md
    @ListenerComponent(requiresPlugins = "PlaceholderAPI")
    public static final class PlaceholderListener implements Listener {}

    // docs/configuration.md
    @Codec(ColorCodec.class)
    public record Color(int rgb) {}

    public static final class ColorCodec implements ConfigCodec<Color> {
        @Override
        public Color decode(Object raw) {
            String text = String.valueOf(raw);
            if (!text.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("expected #RRGGBB but got " + text);
            }
            return new Color(Integer.parseInt(text.substring(1), 16));
        }

        @Override
        public Object encode(Color value) {
            return String.format("#%06x", value.rgb());
        }
    }

    // docs/localization.md
    static void localization(Localization localization, Player player, String name) {
        localization.send(player, "homes.created", Arg.text("home", name));
        Component title = localization.message(player, "menu.title");
        List<Component> lore = localization.lines(Locale.US, "items.home.lore", Arg.number("x", 10));
    }

    // docs/guis.md
    static GuiEntry<Home> entry(Home home) {
        return GuiEntry.of(home.name(), home)
                .placeholder("home_name", home.name())
                .placeholder("world", home.location().world())
                .material(Material.CYAN_BED);
    }

    static void open(GuiManager guis, Player player, UUID homeId) {
        guis.open(player, "homes");
        guis.open(player, "home-edit", Map.of("home", homeId));
    }

    // docs/scheduling.md
    static void tasks(Tasks tasks, Player player) {
        tasks.entity(player).later(Duration.ofSeconds(3), () -> player.sendMessage("Teleported"));
    }

    // docs/storage.md
    static void migrations(Database database) {
        database.migrate(List.of(
                Migration.resource(1, "create homes", HomesPlugin.class, "db/V1__homes.sql"),
                Migration.sql(2, "index owner", "CREATE INDEX homes_owner ON homes (owner);"),
                Migration.code(3, "backfill", connection -> {
                    /* JDBC */
                })));
    }

    // docs/utilities.md
    static ItemStack item(Plugin plugin) {
        return ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(Component.text("Blade", NamedTextColor.RED))
                .lore(Component.text("Sharp", NamedTextColor.GRAY))
                .enchant(Enchantment.SHARPNESS, 3)
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .glow(true)
                .customModelData(7)
                .data(PersistentData.key(plugin, "kit"), PersistentDataType.STRING, "starter")
                .build();
    }
}
