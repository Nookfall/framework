package com.nookfall.framework.processor;

import static com.nookfall.framework.processor.Fixtures.compile;
import static com.nookfall.framework.processor.Fixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidDeclarationsTest {

    static final String SERVICE = """
            package demo;

            @jakarta.inject.Singleton
            public class HomeService {
                public java.util.List<String> names(org.bukkit.command.CommandSender sender) { return java.util.List.of("a"); }
            }
            """;

    static final String COMMAND = """
            package demo;

            import com.nookfall.framework.command.*;
            import com.nookfall.framework.constraint.Range;
            import org.bukkit.entity.Player;
            import org.bukkit.command.CommandSender;

            @Command(value = "homes", description = "Manage homes")
            @Aliases({"home"})
            @Permission("demo.homes")
            public final class HomesCommand {
                private final HomeService homes;

                public HomesCommand(HomeService homes) { this.homes = homes; }

                @Execute
                public void root(Player player) {}

                @Subcommand("set")
                @Permission("demo.homes.set")
                @Cooldown(value = "5s", bypassPermission = "demo.bypass")
                public void set(Player player, @Arg(value = "name", suggestions = "names") String name) {}

                @Subcommand("list")
                void list(CommandSender sender, @Arg("page") @Default("1") @Range(min = 1, max = 100) int page) {}

                @Subcommand("admin give")
                void give(CommandSender sender, @Arg("target") Player target, @Arg("mode") Mode mode,
                          @Arg(value = "note", optional = true) @Greedy String note) {}

                @Suggestions("names")
                java.util.List<String> names(CommandSender sender) { return homes.names(sender); }

                enum Mode { ADD, REMOVE }
            }
            """;

    static final String LISTENER = """
            package demo.listeners;

            import com.nookfall.framework.listener.ListenerComponent;
            import org.bukkit.event.EventHandler;
            import org.bukkit.event.Listener;
            import org.bukkit.event.player.PlayerJoinEvent;

            @ListenerComponent
            public final class JoinListener implements Listener {
                private final demo.HomeService homes;
                public JoinListener(demo.HomeService homes) { this.homes = homes; }
                @EventHandler public void onJoin(PlayerJoinEvent event) {}
            }
            """;

    static final String OPTIONAL_LISTENER = """
            package demo.listeners;

            import com.nookfall.framework.listener.ListenerComponent;
            import org.bukkit.event.EventHandler;
            import org.bukkit.event.Listener;
            import org.bukkit.event.player.PlayerQuitEvent;

            @ListenerComponent(requiresPlugins = "Vault")
            public final class VaultListener implements Listener {
                @EventHandler public void onQuit(PlayerQuitEvent event) {}
            }
            """;

    static final String CONFIG = """
            package demo;

            import com.nookfall.framework.config.*;
            import com.nookfall.framework.constraint.*;
            import java.time.Duration;
            import java.util.*;

            @Config(value = "homes.yml", header = "Homes settings")
            public class HomesConfig {
                @Comment("Maximum homes") @Range(min = 1, max = 100) private int maxHomes = 3;
                private Duration teleportDelay = Duration.ofSeconds(5);
                private List<String> worlds = List.of("world");
                private Map<String, Integer> limits = Map.of();
                private Cleanup cleanup = new Cleanup();
                @NotBlank private String prefix = "x";

                public static class Cleanup {
                    private String interval = "5m";
                }

                public int maxHomes() { return maxHomes; }
            }
            """;

    static final String CONSUMER = """
            package demo;

            import com.nookfall.framework.config.ConfigRef;
            import com.nookfall.framework.task.*;

            public final class Cleaner {
                private final ConfigRef<HomesConfig> config;
                private final Tasks tasks;
                public Cleaner(ConfigRef<HomesConfig> config, Tasks tasks) { this.config = config; this.tasks = tasks; }

                @Scheduled(every = "${cleanup.interval}", initialDelay = "30s", execution = Execution.ASYNC)
                void cleanup() {}

                @Scheduled(every = "1m")
                void tick() {}
            }
            """;

    static final String MESSAGES = """
            package demo;

            import com.nookfall.framework.message.*;
            import net.kyori.adventure.text.Component;
            import org.bukkit.entity.Player;

            @Messages("homes")
            public interface HomeMessages {
                @Key("created") void created(Player player, String home);
                void deleted(Player player, @Param("home") String name, int remaining);
                Component title(java.util.Locale locale);
            }
            """;

    static final String GUI = """
            package demo;

            import com.nookfall.framework.gui.*;
            import java.util.List;
            import java.util.concurrent.CompletableFuture;
            import org.bukkit.entity.Player;
            import org.bukkit.event.inventory.ClickType;
            import org.bukkit.inventory.ItemStack;

            @Gui("homes")
            public final class HomesGui {
                @GuiProvider("homes")
                public List<GuiEntry<String>> entries(GuiContext context) { return List.of(GuiEntry.of("a", "a")); }

                @GuiProvider("slow")
                CompletableFuture<List<GuiEntry<String>>> slow(GuiContext context) { return CompletableFuture.completedFuture(List.of()); }

                @GuiAction("home_click")
                void click(GuiClickContext context, GuiEntry<String> entry, ClickType type) {}

                @GuiAction("info")
                void info(Player player) {}

                @GuiCondition("can_delete")
                boolean canDelete(GuiContext context) { return true; }

                @GuiCondition("own")
                boolean own(GuiContext context, GuiEntry<String> entry) { return true; }

                @GuiPlaceholder("owner")
                Object owner(GuiRenderContext context) { return "x"; }

                @GuiRenderer("icon")
                ItemStack icon(GuiRenderContext context, ItemStack item) { return item; }
            }
            """;

    static final String RESOLVER = """
            package demo;

            import com.nookfall.framework.command.*;
            import org.bukkit.command.CommandSender;

            @CommandArgument
            public final class HomeResolver implements ArgumentResolver<Home> {
                public Home parse(CommandSender sender, String input) { return new Home(input); }
            }
            """;

    static final String HOME = """
            package demo;
            public record Home(String name) {}
            """;

    static final String RESOLVER_COMMAND = """
            package demo;

            import com.nookfall.framework.command.*;
            import org.bukkit.command.CommandSender;

            @Command("visit")
            public final class VisitCommand {
                @Execute void visit(CommandSender sender, @Arg("home") Home home) {}
            }
            """;

    @Test
    void fullPluginCompilesAndAvajeWiresGeneratedBeans() {
        Fixtures.Result compilation = compile(
                source("demo.HomeService", SERVICE),
                source("demo.HomesCommand", COMMAND),
                source("demo.listeners.JoinListener", LISTENER),
                source("demo.listeners.VaultListener", OPTIONAL_LISTENER),
                source("demo.HomesConfig", CONFIG),
                source("demo.Cleaner", CONSUMER),
                source("demo.HomeMessages", MESSAGES),
                source("demo.HomesGui", GUI),
                source("demo.Home", HOME),
                source("demo.HomeResolver", RESOLVER),
                source("demo.VisitCommand", RESOLVER_COMMAND));
        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.success()).as(compilation.output()).isTrue();
        assertThat(compilation.hasGenerated("demo.DemoPlugin_NookfallIndex"))
                .as("demo.DemoPlugin_NookfallIndex in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.Nookfall_Components"))
                .as("demo.Nookfall_Components in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.listeners.Nookfall_Components"))
                .as("demo.listeners.Nookfall_Components in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.listeners.Nookfall_Prototypes"))
                .as("demo.listeners.Nookfall_Prototypes in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.Nookfall_ConfigRefs"))
                .as("demo.Nookfall_ConfigRefs in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.HomeMessages_NookfallMessages"))
                .as("demo.HomeMessages_NookfallMessages in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        // Avaje generated DI classes for the imported and generated beans.
        assertThat(compilation.hasGenerated("demo.HomesCommand$DI"))
                .as("demo.HomesCommand$DI in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.listeners.JoinListener$DI"))
                .as("demo.listeners.JoinListener$DI in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.HomeMessages_NookfallMessages$DI"))
                .as("demo.HomeMessages_NookfallMessages$DI in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.hasGenerated("demo.Cleaner$DI"))
                .as("demo.Cleaner$DI in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
    }

    @Test
    void pluginWhoseOnlyBeansAreNookfallComponentsGetsAnAvajeModule() {
        Fixtures.Result compilation = compile(source("demo.Ping", """
                package demo;
                import com.nookfall.framework.command.*;
                @Command("ping")
                public final class Ping { @Execute void run(org.bukkit.command.CommandSender sender) {} }
                """));
        assertThat(compilation.success()).as(compilation.output()).isTrue();
        assertThat(compilation.output()).doesNotContain("is being ignored");
        assertThat(compilation.hasGenerated("demo.DemoModule"))
                .as("%s", Fixtures.generatedFiles(compilation))
                .isTrue();
        assertThat(compilation.generatedSource("demo.DemoModule")).contains("Ping$DI");
    }

    @Test
    void pluginWithoutComponentsGetsEmptyIndex() {
        Fixtures.Result compilation = compile();
        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.success()).as(compilation.output()).isTrue();
        assertThat(compilation.hasGenerated("demo.DemoPlugin_NookfallIndex"))
                .as("demo.DemoPlugin_NookfallIndex in %s", Fixtures.generatedFiles(compilation))
                .isTrue();
    }

    @Test
    void validatesResourcesWhenDirectoryIsKnown(@TempDir Path resources) throws IOException {
        Files.createDirectories(resources.resolve("lang"));
        Files.writeString(resources.resolve("lang/en_US.yml"), """
                homes:
                  created: "Created <home>"
                  deleted: "Deleted <home> (<remaining> left)"
                  title: "Homes"
                """);
        Files.createDirectories(resources.resolve("guis/homes"));
        Files.writeString(resources.resolve("guis/homes/layout.yml"), """
                rows: 2
                title: homes.title
                layout:
                  - "HHHHHHHHH"
                  - "P   C   N"
                elements:
                  homes: { symbol: "H", type: collection, provider: homes, item-template: home }
                  prev: { symbol: "P", type: button, action: previous_page, visible-if: has_previous_page, item: { material: ARROW } }
                  next: { symbol: "N", type: button, action: next_page, visible-if: "!can_delete", item: { material: ARROW } }
                  close: { symbol: "C", type: button, action: close, item: { material: BARRIER } }
                templates:
                  home: { material: RED_BED, action: home_click, renderer: icon, click-actions: { right: info } }
                """);
        Fixtures.Result compilation =
                compile(resources, source("demo.HomeMessages", MESSAGES), source("demo.HomesGui", GUI));
        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.success()).as(compilation.output()).isTrue();
    }
}
