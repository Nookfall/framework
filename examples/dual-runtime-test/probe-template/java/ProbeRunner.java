package __PKG__;

import com.nookfall.framework.NookfallRuntime;
import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.RuntimeMode;
import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.task.TaskHandle;
import com.nookfall.framework.task.Tasks;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Runs the integration checks inside the server and records the outcome for the harness. */
@Singleton
public final class ProbeRunner {

    private final Plugin plugin;
    private final NookfallRuntime runtime;
    private final ProbeState state;
    private final ConfigRef<ProbeConfig> config;
    private final ProbeMessages messages;
    private final Tasks tasks;
    private final String label = "__LABEL__";

    public ProbeRunner(
            Plugin plugin,
            NookfallRuntime runtime,
            ProbeState state,
            ConfigRef<ProbeConfig> config,
            ProbeMessages messages,
            Tasks tasks) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.state = state;
        this.config = config;
        this.messages = messages;
        this.tasks = tasks;
    }

    private void pass(String name) {
        Results.append(plugin, "PASS " + name);
    }

    private void fail(String name, String detail) {
        Results.append(plugin, "FAIL " + name + ": " + detail);
    }

    private void info(String name, String detail) {
        Results.append(plugin, "INFO " + name + ": " + detail);
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            pass(name);
        } else {
            fail(name, detail);
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Dispatches as a sender whose feedback is captured. */
    private List<String> dispatch(String command) {
        return dispatch(command, null, false);
    }

    private List<String> dispatch(String command, String permission, boolean granted) {
        List<String> feedback = new ArrayList<>();
        CommandSender sender = Bukkit.createCommandSender(component -> feedback.add(plain(component)));
        if (permission != null) {
            sender.addAttachment(plugin, permission, granted);
        }
        try {
            Bukkit.dispatchCommand(sender, command);
        } catch (org.bukkit.command.CommandException e) {
            // Paper rethrows Brigadier syntax errors from dispatchCommand; players see them as error messages.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            feedback.add("rejected: " + cause.getMessage());
        }
        return feedback;
    }

    void run(int run) {
        Results.append(plugin, "RUN " + run);
        try {
            checks();
        } catch (Throwable t) {
            fail("unexpected-exception", t.toString());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Probe failed", t);
        }
        // Give async and timer checks time to complete before finishing.
        tasks.global().later(Duration.ofSeconds(4), () -> {
            try {
                delayedChecks();
            } catch (Throwable t) {
                fail("unexpected-exception-delayed", t.toString());
            }
            coordinate(run);
        });
    }

    private void checks() throws Exception {
        info("mode", runtime.mode().name());
        info("framework-version", runtime.frameworkVersion());

        // Class identity: shared runtimes come from the NookfallFramework plugin; standalone ones are relocated
        // into this plugin's package.
        ClassLoader frameworkLoader = NookfallRuntime.class.getClassLoader();
        if (runtime.mode() == RuntimeMode.SHARED) {
            Plugin framework = Bukkit.getPluginManager().getPlugin("NookfallFramework");
            check("class-identity", framework != null && framework.getClass().getClassLoader() == frameworkLoader,
                    "framework classes loaded by " + frameworkLoader);
            check("class-names", NookfallRuntime.class.getName().equals("com.nookfall.framework.NookfallRuntime"),
                    NookfallRuntime.class.getName());
        } else {
            check("class-identity", frameworkLoader == plugin.getClass().getClassLoader(),
                    "framework classes loaded by " + frameworkLoader);
            check("class-names", NookfallRuntime.class.getName().startsWith("__PKG__.libs.nookfall."),
                    NookfallRuntime.class.getName());
        }

        // Dependency injection.
        check("di-singleton", runtime.get(ProbeState.class) == state, "ProbeState is not a singleton");
        check("di-config", config.get().answer() == 42, "answer=" + config.get().answer());

        // Commands.
        dispatch(label + " echo hello world");
        check("command-greedy", "hello world".equals(state.lastEcho), "lastEcho=" + state.lastEcho);
        dispatch(label + " add 5");
        check("command-default", state.sum == 15, "sum=" + state.sum);
        dispatch(label + " add 5 7");
        check("command-optional", state.sum == 12, "sum=" + state.sum);
        dispatch(label + "-alias add 1 1");
        check("command-alias", state.sum == 2, "sum=" + state.sum);
        dispatch(plugin.getName().toLowerCase(Locale.ROOT) + ":" + label + " add 2 2");
        check("command-namespaced", state.sum == 4, "sum=" + state.sum);
        dispatch(label + " mode slow");
        check("command-enum", state.mode == ProbeCommand.Mode.SLOW, "mode=" + state.mode);
        List<String> invalidEnum = dispatch(label + " mode sideways");
        check("command-enum-invalid", state.mode == ProbeCommand.Mode.SLOW && joined(invalidEnum).contains("sideways"),
                "feedback=" + invalidEnum);
        List<String> invalid = dispatch(label + " add notanumber");
        check("command-invalid-input", state.sum == 4 && joined(invalid).contains("Expected integer"),
                "sum=" + state.sum + " feedback=" + invalid);
        List<String> range = dispatch(label + " add 1 500");
        check("command-range", state.sum == 4 && !range.isEmpty(), "sum=" + state.sum + " feedback=" + range);
        List<String> playerOnly = dispatch(label + " player-only");
        check("command-sender-restriction", joined(playerOnly).contains("Only players"), "feedback=" + playerOnly);
        List<String> failed = dispatch(label + " fail");
        check("command-exception", joined(failed).contains("internal error"), "feedback=" + failed);
        List<String> refused = dispatch(label + " refuse");
        check("command-failure-message", joined(refused).contains("[Probe] Refused."), "feedback=" + refused);
        List<String> help = dispatch(label + " help");
        check("command-help", joined(help).contains("/" + label + " echo <text>") && joined(help).contains("Echo text"),
                "feedback=" + help);
        List<String> denied = dispatch(label + " secret", "probe.secret." + label, false);
        check("command-permission-denied", !state.secretRan && !denied.isEmpty(),
                "secret ran without permission or no feedback: " + denied);
        dispatch(label + " secret", "probe.secret." + label, true);
        check("command-permission-granted", state.secretRan, "secret did not run with permission");
        List<String> shared = dispatch("nookfall-probe-shared");
        info("command-conflict", "unqualified /nookfall-probe-shared answered by " + shared);
        List<String> ownShared = dispatch(plugin.getName().toLowerCase(Locale.ROOT) + ":nookfall-probe-shared");
        check("command-conflict-namespaced", joined(ownShared).contains("shared-name:__NAME__"), "feedback=" + ownShared);

        // Tab completion through Brigadier.
        CommandSender console = Bukkit.getConsoleSender();
        List<String> words = Bukkit.getCommandMap().tabComplete(console, label + " echo ");
        check("command-suggestions", words != null && words.containsAll(List.of("hello", "world")), "suggestions=" + words);
        List<String> modes = Bukkit.getCommandMap().tabComplete(console, label + " mode s");
        check("command-suggestions-enum", modes != null && modes.contains("slow") && !modes.contains("fast"),
                "suggestions=" + modes);
        List<String> subcommands = Bukkit.getCommandMap().tabComplete(console, label + " ");
        check("command-suggestions-subcommands", subcommands != null && subcommands.contains("echo")
                && subcommands.contains("help"), "suggestions=" + subcommands);

        // Utilities against the live server's registries.
        utilities();

        // Listeners.
        Bukkit.getPluginManager().callEvent(new ProbeEvent());
        check("listener", state.listenerHits.get() == 1, "hits=" + state.listenerHits.get());
        check("listener-conditional", MissingIntegrationListener.CONSTRUCTED.get() == 0,
                "listener requiring a missing plugin was constructed");

        // Configuration.
        Path file = config.file();
        String text = Files.readString(file);
        check("config-defaults", text.contains("# The answer.") && text.contains("answer: 42")
                && text.contains("interval: 1s") && text.contains("Probe configuration"), text);
        Files.writeString(file, text.replace("answer: 42", "answer: not-a-number") + "custom-user-key: kept\n");
        ReloadResult broken = config.reload();
        check("config-reload-rollback", !broken.success() && config.get().answer() == 42,
                "result=" + broken + " answer=" + config.get().answer());
        Files.writeString(file, text.replace("answer: 42", "answer: 2000"));
        ReloadResult outOfRange = config.reload();
        check("config-validation", !outOfRange.success() && config.get().answer() == 42, "result=" + outOfRange);
        Files.writeString(file, text.replace("answer: 42", "answer: 7") + "custom-user-key: kept\n");
        ReloadResult ok = config.reload();
        check("config-reload", ok.success() && config.get().answer() == 7, "result=" + ok);
        check("config-unknown-key-kept", Files.readString(file).contains("custom-user-key: kept")
                && ok.warnings().stream().anyMatch(w -> w.contains("custom-user-key")), "warnings=" + ok.warnings());

        // Localization: player-provided text is never parsed as MiniMessage.
        check("l10n-default", plain(messages.greeting(Locale.US, "<red>Bob")).equals("Hello <red>Bob"),
                plain(messages.greeting(Locale.US, "<red>Bob")));
        check("l10n-polish", plain(messages.greeting(Locale.of("pl", "PL"), "Ala")).equals("Cześć Ala"),
                plain(messages.greeting(Locale.of("pl", "PL"), "Ala")));
        check("l10n-fallback", plain(messages.greeting(Locale.GERMANY, "Max")).equals("Hello Max"),
                plain(messages.greeting(Locale.GERMANY, "Max")));
        check("l10n-files-copied", Files.exists(plugin.getDataFolder().toPath().resolve("lang/pl_PL.yml")),
                "lang/pl_PL.yml missing");

        // GUI definitions.
        check("gui-loaded", runtime.guis().guiIds().contains("probe"), "guis=" + runtime.guis().guiIds());
        check("gui-files-copied", Files.exists(plugin.getDataFolder().toPath().resolve("guis/probe/layout.yml")),
                "guis/probe/layout.yml missing");

        // Tasks.
        check("tasks-primary", Bukkit.isPrimaryThread(), "checks did not run on the main thread");
        CompletableFuture<Boolean> asyncThread = tasks.async().supply(() -> !Bukkit.isPrimaryThread());
        check("tasks-async-thread", asyncThread.get(5, TimeUnit.SECONDS), "async task ran on the main thread");
        TaskHandle cancelled = tasks.global().later(Duration.ofSeconds(1), () -> fail("tasks-cancel", "cancelled task ran"));
        cancelled.cancel();
        check("tasks-cancel-handle", cancelled.isCancelled() && cancelled.isDone(), "handle state wrong");

        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        // Entities only tick (and run entity tasks) in ticking chunks; with no players online that needs a ticket.
        world.getChunkAt(spawn).addPluginChunkTicket(plugin);
        AtomicBoolean regionRan = new AtomicBoolean();
        tasks.region(spawn).run(() -> regionRan.set(Bukkit.isPrimaryThread()));
        ArmorStand stand = world.spawn(spawn, ArmorStand.class);
        state.entityRan = new AtomicBoolean();
        state.retired = new AtomicBoolean();
        tasks.entity(stand).later(Duration.ofMillis(100), () -> state.entityRan.set(true));
        ArmorStand doomed = world.spawn(spawn, ArmorStand.class);
        tasks.entity(doomed, () -> state.retired.set(true)).later(Duration.ofSeconds(1), () -> fail("tasks-entity-retired",
                "task ran for a removed entity"));
        doomed.remove();
        state.regionRan = regionRan;
        state.stand = stand;

        // Scheduled methods: interval comes from configuration and changes on reload.
        info("scheduled", String.valueOf(((com.nookfall.framework.internal.NookfallRuntimeImpl) runtime).scheduledTasks()));
    }

    @SuppressWarnings("deprecation")
    private void utilities() {
        var sharpness = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                .get(org.bukkit.NamespacedKey.minecraft("sharpness"));
        org.bukkit.inventory.ItemStack item = com.nookfall.framework.util.item.ItemBuilder.of(org.bukkit.Material.DIAMOND_SWORD)
                .name(Component.text("Blade"))
                .lore(Component.text("Line"))
                .glow(true)
                .customModelData(1234)
                .enchant(sharpness, 3)
                .flags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
                .data(com.nookfall.framework.util.item.PersistentData.key(plugin, "id"),
                        org.bukkit.persistence.PersistentDataType.STRING, "blade-1")
                .build();
        var meta = item.getItemMeta();
        check("item-builder", plain(meta.displayName()).equals("Blade") && meta.hasCustomModelData()
                && meta.getCustomModelData() == 1234 && meta.getEnchantLevel(sharpness) == 3
                && meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
                && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()), "meta=" + meta);
        String base64 = com.nookfall.framework.util.item.Items.toBase64(item);
        org.bukkit.inventory.ItemStack restored = com.nookfall.framework.util.item.Items.fromBase64(base64);
        check("item-serialization", restored.isSimilar(item)
                && com.nookfall.framework.util.item.PersistentData.get(restored,
                        com.nookfall.framework.util.item.PersistentData.key(plugin, "id"),
                        org.bukkit.persistence.PersistentDataType.STRING).orElse("").equals("blade-1"), base64);
        check("materials", com.nookfall.framework.util.item.Materials.findItem("minecraft:diamond_sword")
                .orElse(null) == org.bukkit.Material.DIAMOND_SWORD
                && com.nookfall.framework.util.item.Materials.findItem("water").isEmpty(), "lookup failed");
        World world = Bukkit.getWorlds().getFirst();
        Location location = new Location(world, 10.5, 64, -3.25, 90f, 10f);
        var stored = com.nookfall.framework.util.location.StoredLocation.of(location);
        var parsed = com.nookfall.framework.util.location.StoredLocation.parse(stored.serialize());
        check("location-serialization", parsed.equals(stored) && parsed.resolve().map(l -> l.equals(location)).orElse(false),
                stored.serialize());
        Location surface = world.getHighestBlockAt(world.getSpawnLocation()).getLocation().add(0.5, 1, 0.5);
        check("safe-location", com.nookfall.framework.util.location.SafeLocations.isSafe(surface)
                && !com.nookfall.framework.util.location.SafeLocations.isSafe(surface.clone().add(0, -2, 0)),
                "surface=" + surface);
        var cuboid = com.nookfall.framework.util.location.Cuboid.between(location, location.clone().add(5, 5, 5));
        check("cuboid", cuboid.contains(location.clone().add(2, 2, 2)) && !cuboid.contains(location.clone().add(9, 0, 0))
                && cuboid.volume() == 216, String.valueOf(cuboid));
    }

    private void delayedChecks() throws Exception {
        check("tasks-region", state.regionRan.get(), "region task did not run on the main thread");
        check("tasks-entity", state.entityRan.get(), "entity task did not run; entity valid=" + state.stand.isValid());
        check("tasks-entity-retired", state.retired.get(), "retired callback did not run");
        state.stand.remove();
        check("scheduled-ticks", state.ticks.get() >= 2 && state.ticksOnMainThread.get(), "ticks=" + state.ticks.get());
        check("scheduled-async", state.asyncRuns.get() >= 3 && state.asyncOffMainThread.get(),
                "asyncRuns=" + state.asyncRuns.get() + " offMain=" + state.asyncOffMainThread.get());
        Path file = config.file();
        Files.writeString(file, Files.readString(file).replace("interval: 1s", "interval: 3s"));
        config.reload();
        List<String> scheduled = ((com.nookfall.framework.internal.NookfallRuntimeImpl) runtime).scheduledTasks();
        check("scheduled-rescheduled", scheduled.stream().anyMatch(s -> s.endsWith("tick every 3s")), "scheduled=" + scheduled);
        Files.writeString(file, Files.readString(file).replace("interval: 3s", "interval: banana"));
        config.reload();
        scheduled = ((com.nookfall.framework.internal.NookfallRuntimeImpl) runtime).scheduledTasks();
        check("scheduled-invalid-keeps-previous", scheduled.stream().anyMatch(s -> s.endsWith("tick every 3s")),
                "scheduled=" + scheduled);

        if (runtime.mode() == RuntimeMode.SHARED) {
            List<String> names = com.nookfall.framework.internal.RuntimeRegistry.runtimes().stream()
                    .map(r -> r.plugin().getName())
                    .toList();
            info("shared-runtimes", String.valueOf(names));
            check("shared-registry", names.contains(plugin.getName()), "registry=" + names);
        }
    }

    /** Alpha disables and re-enables Beta to test cleanup and re-enable. */
    private void coordinate(int run) {
        PluginManager manager = Bukkit.getPluginManager();
        Plugin beta = manager.getPlugin("ProbeBeta");
        if ("__NAME__".equals("Alpha") && run == 1 && beta != null) {
            if (beta.isEnabled()) {
                manager.disablePlugin(beta);
                check("cycle-disable-listeners", HandlerList.getRegisteredListeners(beta).isEmpty(),
                        "listeners left: " + HandlerList.getRegisteredListeners(beta).size());
                // Paper closes a plugin's class loader when it is disabled, so enabling the same instance again can
                // fail while loading classes. Either outcome is acceptable as long as nothing is left registered.
                manager.enablePlugin(beta);
                if (beta.isEnabled()) {
                    info("cycle-reenable", "re-enabled");
                    pass("cycle-reenable-handled");
                } else {
                    info("cycle-reenable", "Paper could not re-enable the disabled plugin instance");
                    check("cycle-reenable-handled", HandlerList.getRegisteredListeners(beta).isEmpty(),
                            "listeners left after failed re-enable: " + HandlerList.getRegisteredListeners(beta).size());
                }
            } else {
                check("failed-startup-cleanup", HandlerList.getRegisteredListeners(beta).isEmpty(),
                        "listeners left by failed plugin: " + HandlerList.getRegisteredListeners(beta).size());
            }
        }
        Results.append(plugin, "DONE run=" + run);
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }
}
