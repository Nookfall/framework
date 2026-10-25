package com.nookfall.framework.internal;

import com.nookfall.framework.NookfallRuntime;
import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.RuntimeMode;
import com.nookfall.framework.command.ArgumentResolver;
import com.nookfall.framework.config.ConfigService;
import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.integration.Integrations;
import com.nookfall.framework.internal.command.CommandManager;
import com.nookfall.framework.internal.config.ConfigServiceImpl;
import com.nookfall.framework.internal.gui.GuiManagerImpl;
import com.nookfall.framework.internal.i18n.LocalizationImpl;
import com.nookfall.framework.internal.task.ScheduledRegistrar;
import com.nookfall.framework.internal.task.TasksImpl;
import com.nookfall.framework.message.Localization;
import com.nookfall.framework.spi.ArgumentSpec;
import com.nookfall.framework.spi.CommandSpec;
import com.nookfall.framework.spi.GuiBinding;
import com.nookfall.framework.spi.ListenerSpec;
import com.nookfall.framework.spi.PluginIndex;
import com.nookfall.framework.spi.ScheduledSpec;
import com.nookfall.framework.task.Tasks;
import io.avaje.inject.BeanScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** The runtime of one plugin. */
public final class NookfallRuntimeImpl implements NookfallRuntime {

    private final JavaPlugin plugin;
    private final PluginIndex index;
    private final Logger logger;
    private final Closer closer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final RuntimeMode mode;
    private TasksImpl tasks;
    private ConfigServiceImpl configs;
    private LocalizationImpl localization;
    private GuiManagerImpl guis;
    private CommandManager commands;
    private ScheduledRegistrar scheduled;
    private BeanScope scope;
    private final List<Listener> listeners = new ArrayList<>();

    public NookfallRuntimeImpl(JavaPlugin plugin, PluginIndex index) {
        this.plugin = plugin;
        this.index = index;
        this.logger = plugin.getLogger();
        this.closer = new Closer(logger);
        this.mode = NookfallRuntime.class.getClassLoader() == plugin.getClass().getClassLoader()
                ? RuntimeMode.STANDALONE
                : RuntimeMode.SHARED;
    }

    /** Starts everything; on failure the caller must {@link #close()}. */
    public void start() {
        long started = System.nanoTime();
        Path dataFolder = plugin.getDataFolder().toPath();
        ResourceIndex resources = ResourceIndex.of(plugin.getClass());

        tasks = new TasksImpl(plugin, logger);
        closer.add("tasks", tasks::cancelAll);

        configs = new ConfigServiceImpl(dataFolder, index.configs(), logger);
        configs.loadInitial();

        localization = new LocalizationImpl(dataFolder, resources, logger);
        localization.loadInitial();

        guis = new GuiManagerImpl(plugin, dataFolder, resources, localization, tasks, logger, closed::get);
        commands = new CommandManager(localization, logger, closed::get);

        IntegrationsImpl integrations = new IntegrationsImpl(
                index.integrations(), Bukkit.getPluginManager()::isPluginEnabled, type -> scope.get(type), logger);

        scope = BeanScope.builder()
                .classLoader(plugin.getClass().getClassLoader())
                .shutdownHook(false)
                .bean(Plugin.class, plugin)
                .bean(JavaPlugin.class, plugin)
                .bean(Server.class, plugin.getServer())
                .bean(Logger.class, logger)
                .bean(NookfallRuntime.class, this)
                .bean(Tasks.class, tasks)
                .bean(Localization.class, localization)
                .bean(ConfigService.class, configs)
                .bean(GuiManager.class, guis)
                .bean(Integrations.class, integrations)
                .build();
        closer.add("beans", scope::close);
        // Registered after the bean scope so tasks stop before beans are destroyed.
        closer.add("tasks", tasks::cancelAll);

        for (ArgumentSpec<?> argument : index.arguments()) {
            registerArgument(argument);
        }
        for (CommandSpec<?> command : index.commands()) {
            buildCommand(command);
        }
        commands.registerAll(plugin);

        for (ListenerSpec spec : index.listeners()) {
            List<String> missing = new ArrayList<>();
            for (String required : spec.requiredPlugins()) {
                if (!Bukkit.getPluginManager().isPluginEnabled(required)) {
                    missing.add(required);
                }
            }
            if (!missing.isEmpty()) {
                logger.info("Skipping " + spec.type().getSimpleName() + " because " + String.join(", ", missing)
                        + " is not enabled");
                continue;
            }
            register((Listener) scope.get(spec.type()));
        }

        for (GuiBinding<?> binding : index.guis()) {
            bindGui(binding);
        }
        guis.loadInitial();
        register(guis);
        closer.add("guis", guis::shutdown);

        scheduled = new ScheduledRegistrar(tasks, configs, logger);
        for (ScheduledSpec<?> spec : index.scheduled()) {
            schedule(spec);
        }

        RuntimeRegistry.add(this);
        closer.add("registry", () -> RuntimeRegistry.remove(this));

        long millis = (System.nanoTime() - started) / 1_000_000;
        logger.info(String.format(
                "Nookfall %s (%s) ready in %d ms: %d command(s), %d listener(s), %d GUI(s), %d scheduled task(s)",
                FrameworkVersion.CURRENT,
                mode.name().toLowerCase(java.util.Locale.ROOT),
                millis,
                index.commands().size(),
                listeners.size() - 1,
                guis.guiIds().size(),
                scheduled.count()));
    }

    private <T> void registerArgument(ArgumentSpec<T> spec) {
        ArgumentResolver<T> resolver = scope.get(spec.resolverType());
        commands.registerResolver(spec.valueType(), resolver);
    }

    private <T> void buildCommand(CommandSpec<T> spec) {
        commands.build(spec, scope.get(spec.type()));
    }

    private <T> void bindGui(GuiBinding<T> binding) {
        guis.bind(binding, scope.get(binding.type()));
    }

    private <T> void schedule(ScheduledSpec<T> spec) {
        scheduled.schedule(spec, scope.get(spec.type()));
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
        closer.add("listener " + listener.getClass().getSimpleName(), () -> HandlerList.unregisterAll(listener));
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public RuntimeMode mode() {
        return mode;
    }

    @Override
    public String frameworkVersion() {
        return FrameworkVersion.CURRENT;
    }

    @Override
    public <T> T get(Class<T> type) {
        ensureOpen();
        return scope.get(type);
    }

    @Override
    public <T> Optional<T> find(Class<T> type) {
        ensureOpen();
        return scope.getOptional(type);
    }

    @Override
    public Tasks tasks() {
        return tasks;
    }

    @Override
    public Localization localization() {
        return localization;
    }

    @Override
    public ConfigService configs() {
        return configs;
    }

    @Override
    public GuiManager guis() {
        return guis;
    }

    /** Scheduled task descriptions, for diagnostics. */
    public List<String> scheduledTasks() {
        return scheduled == null ? List.of() : scheduled.describe();
    }

    /** Built command labels, for diagnostics. */
    public List<String> commandNames() {
        return commands == null
                ? List.of()
                : commands.built().stream().map(CommandManager.Built::name).toList();
    }

    @Override
    public ReloadResult reload() {
        ensureOpen();
        ReloadResult result = configs.reloadAll().merge(localization.reload()).merge(guis.reload());
        result.errors().forEach(logger::severe);
        return result;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("The Nookfall runtime of " + plugin.getName() + " is closed");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            closer.closeAll();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error while shutting down Nookfall", t);
        }
    }
}
