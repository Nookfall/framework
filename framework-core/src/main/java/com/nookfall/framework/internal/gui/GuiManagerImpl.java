package com.nookfall.framework.internal.gui;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.internal.ResourceIndex;
import com.nookfall.framework.internal.YamlFiles;
import com.nookfall.framework.internal.gui.GuiDefinition.Element;
import com.nookfall.framework.internal.gui.GuiDefinition.Type;
import com.nookfall.framework.internal.gui.GuiSession.SlotBinding;
import com.nookfall.framework.internal.i18n.LocalizationImpl;
import com.nookfall.framework.internal.i18n.MessageRenderer;
import com.nookfall.framework.internal.i18n.TranslationTable;
import com.nookfall.framework.internal.task.TasksImpl;
import com.nookfall.framework.message.Arg;
import com.nookfall.framework.spi.GuiBinding;
import com.nookfall.framework.util.item.ItemBuilder;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** Loads GUI layouts and runs GUI sessions for one plugin. */
public final class GuiManagerImpl implements GuiManager, Listener {

    private static final long CLICK_DEBOUNCE_NANOS = 100_000_000L;

    /** A definition together with its translations. */
    record Loaded(GuiDefinition definition, TranslationTable translations) {}

    private record Bound<T>(GuiBinding<T> binding, T instance) {}

    private final Plugin plugin;
    private final Path guiFolder;
    private final ResourceIndex resources;
    private final LocalizationImpl localization;
    private final TasksImpl tasks;
    private final Logger logger;
    private final BooleanSupplier closed;
    private final Map<String, Bound<?>> bindings = new LinkedHashMap<>();
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final Set<String> reportedEnchantments = ConcurrentHashMap.newKeySet();
    private volatile Map<String, Loaded> definitions = Map.of();

    public GuiManagerImpl(
            Plugin plugin,
            Path dataFolder,
            ResourceIndex resources,
            LocalizationImpl localization,
            TasksImpl tasks,
            Logger logger,
            BooleanSupplier closed) {
        this.plugin = plugin;
        this.guiFolder = dataFolder.resolve("guis");
        this.resources = resources;
        this.localization = localization;
        this.tasks = tasks;
        this.logger = logger;
        this.closed = closed;
    }

    public <T> void bind(GuiBinding<T> binding, T instance) {
        if (bindings.putIfAbsent(binding.id(), new Bound<>(binding, instance)) != null) {
            throw new IllegalStateException("GUI id '" + binding.id() + "' is bound twice");
        }
    }

    LocalizationImpl localization() {
        return localization;
    }

    // ---- loading ----

    /**
     * Loads every GUI. A broken user layout falls back to the bundled one; a broken bundled layout fails startup.
     */
    public void loadInitial() {
        Map<String, Loaded> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (String id : ids()) {
            List<String> warnings = new ArrayList<>();
            try {
                loaded.put(id, load(id, false, warnings));
            } catch (GuiDefinitionException | IllegalArgumentException e) {
                logger.severe(message(e));
                try {
                    loaded.put(id, load(id, true, warnings));
                    logger.severe("GUI '" + id + "': using the bundled default layout until the file is fixed");
                } catch (GuiDefinitionException | IllegalArgumentException bundled) {
                    errors.add(message(bundled));
                }
            }
            warnings.forEach(w -> logger.warning("GUI '" + id + "': " + w));
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join("\n", errors));
        }
        definitions = Map.copyOf(loaded);
    }

    private static String message(RuntimeException e) {
        return e.getMessage();
    }

    private Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>(bindings.keySet());
        ids.addAll(resources.directories("guis/"));
        return ids;
    }

    private Loaded load(String id, boolean bundledOnly, List<String> warnings) {
        String base = "guis/" + id + "/";
        Path dir = guiFolder.resolve(id);
        Optional<String> bundledLayout = resources.read(base + "layout.yml");
        try {
            Files.createDirectories(dir.resolve("lang"));
            if (bundledLayout.isPresent() && !Files.exists(dir.resolve("layout.yml"))) {
                YamlFiles.writeAtomically(dir.resolve("layout.yml"), bundledLayout.get());
            }
            for (String langPath : resources.list(base + "lang/", ".yml")) {
                Path target = dir.resolve("lang").resolve(langPath.substring(langPath.lastIndexOf('/') + 1));
                if (!Files.exists(target)) {
                    YamlFiles.writeAtomically(target, resources.read(langPath).orElseThrow());
                }
            }
        } catch (IOException e) {
            warnings.add("could not copy default files: " + e.getMessage());
        }
        String text;
        try {
            Path file = dir.resolve("layout.yml");
            if (!bundledOnly && Files.exists(file)) {
                text = Files.readString(file);
            } else {
                text = bundledLayout.orElseThrow(() ->
                        new IllegalArgumentException("GUI '" + id + "': no layout.yml in the plugin jar or " + file));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("GUI '" + id + "': cannot read layout.yml: " + e.getMessage());
        }
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.parse(text, "GUI '" + id + "' layout.yml");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        GuiDefinition definition = GuiDefinitionParser.parse(id, yaml, names(id), warnings);
        return new Loaded(definition, translations(id, base, dir, bundledOnly));
    }

    private TranslationTable translations(String id, String base, Path dir, boolean bundledOnly) {
        Map<Locale, Map<String, Object>> tables = new LinkedHashMap<>();
        for (String path : resources.list(base + "lang/", ".yml")) {
            Locale locale = TranslationTable.localeOf(path);
            if (locale != null) {
                tables.put(
                        locale,
                        new LinkedHashMap<>(TranslationTable.parse(
                                resources.read(path).orElseThrow(), "GUI '" + id + "' " + path)));
            }
        }
        Path langDir = dir.resolve("lang");
        if (!bundledOnly && Files.isDirectory(langDir)) {
            try (Stream<Path> files = Files.list(langDir)) {
                for (Path file : files.sorted().toList()) {
                    Locale locale = TranslationTable.localeOf(file.getFileName().toString());
                    if (locale != null) {
                        tables.computeIfAbsent(locale, l -> new LinkedHashMap<>())
                                .putAll(TranslationTable.parse(
                                        Files.readString(file), "GUI '" + id + "' lang/" + file.getFileName()));
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("GUI '" + id + "': cannot read translations: " + e.getMessage());
            }
        }
        return new TranslationTable(tables);
    }

    private GuiDefinitionParser.Names names(String id) {
        Bound<?> bound = bindings.get(id);
        if (bound == null) {
            return GuiDefinitionParser.Names.NONE;
        }
        GuiBinding<?> binding = bound.binding();
        return new GuiDefinitionParser.Names(
                binding.providers().keySet(),
                binding.actions().keySet(),
                binding.conditions().keySet(),
                binding.renderers().keySet());
    }

    @Override
    public ReloadResult reload() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Loaded> updated = new LinkedHashMap<>(definitions);
        for (String id : ids()) {
            List<String> found = new ArrayList<>();
            try {
                updated.put(id, load(id, false, found));
            } catch (GuiDefinitionException | IllegalArgumentException e) {
                errors.add(message(e));
                if (definitions.containsKey(id)) {
                    errors.add("GUI '" + id + "': keeping the previously working layout");
                }
            }
            found.forEach(w -> warnings.add("GUI '" + id + "': " + w));
        }
        definitions = Map.copyOf(updated);
        for (GuiSession session : sessions.values()) {
            Loaded next = definitions.get(session.guiId);
            if (next == null || next == session.loaded) {
                continue;
            }
            tasks.entity(session.player).run(() -> {
                if (!isCurrent(session)) {
                    return;
                }
                if (next.definition().rows() != session.loaded.definition().rows()) {
                    reopen(session, next);
                } else {
                    session.loaded = next;
                    render(session);
                }
            });
        }
        return new ReloadResult(errors, warnings);
    }

    // ---- GuiManager ----

    @Override
    public void open(Player player, String guiId) {
        open(player, guiId, Map.of());
    }

    @Override
    public void open(Player player, String guiId, Map<String, Object> arguments) {
        if (closed.getAsBoolean()) {
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            tasks.entity(player).run(() -> open(player, guiId, arguments));
            return;
        }
        Loaded loaded = definitions.get(guiId);
        if (loaded == null) {
            throw new IllegalArgumentException("Unknown GUI '" + guiId + "'. Known: " + definitions.keySet());
        }
        GuiSession session = new GuiSession(this, player, loaded, arguments);
        try {
            loadCollections(session, true);
            session.inventory =
                    Bukkit.createInventory(session, loaded.definition().rows() * 9, title(session));
            render(session);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not open GUI '" + guiId + "' for " + player.getName(), e);
            localization.send(player, "nookfall.gui.error");
            return;
        }
        GuiSession previous = sessions.put(player.getUniqueId(), session);
        if (previous != null) {
            previous.active = false;
        }
        player.openInventory(session.inventory);
        if (player.getOpenInventory().getTopInventory() != session.inventory) {
            // Another plugin cancelled the open event.
            session.active = false;
            sessions.remove(player.getUniqueId(), session);
        }
    }

    @Override
    public Optional<String> openGui(Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        return session != null && session.active ? Optional.of(session.guiId) : Optional.empty();
    }

    @Override
    public void refresh(Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            tasks.entity(player).run(() -> refreshSession(session));
        }
    }

    @Override
    public void refreshAll(String guiId) {
        for (GuiSession session : sessions.values()) {
            if (session.guiId.equals(guiId)) {
                tasks.entity(session.player).run(() -> refreshSession(session));
            }
        }
    }

    @Override
    public void close(Player player) {
        GuiSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            closeSession(session);
        }
    }

    @Override
    public Set<String> guiIds() {
        return definitions.keySet();
    }

    /** Number of open sessions, for diagnostics and tests. */
    public int openSessions() {
        return sessions.size();
    }

    /** Closes every session; called when the runtime shuts down. */
    public void shutdown() {
        for (GuiSession session : List.copyOf(sessions.values())) {
            session.active = false;
            Player player = session.player;
            try {
                if (player.isOnline()
                        && player.getOpenInventory().getTopInventory().getHolder(false) == session) {
                    player.closeInventory();
                }
            } catch (RuntimeException e) {
                logger.log(Level.FINE, "Could not close GUI for " + player.getName(), e);
            }
        }
        sessions.clear();
    }

    // ---- session operations ----

    private boolean isCurrent(GuiSession session) {
        return session.active && !closed.getAsBoolean() && sessions.get(session.playerId) == session;
    }

    void refreshSession(GuiSession session) {
        if (!isCurrent(session)) {
            return;
        }
        loadCollections(session, false);
        render(session);
    }

    void closeSession(GuiSession session) {
        if (session.dispatching) {
            tasks.entity(session.player).run(() -> closeSession(session));
            return;
        }
        if (sessions.remove(session.playerId, session)) {
            session.active = false;
            if (session.player.getOpenInventory().getTopInventory().getHolder(false) == session) {
                session.player.closeInventory();
            }
        }
    }

    void openFrom(GuiSession session, String guiId, Map<String, Object> arguments) {
        if (session.dispatching) {
            tasks.entity(session.player).run(() -> openFrom(session, guiId, arguments));
            return;
        }
        if (isCurrent(session)) {
            open(session.player, guiId, arguments);
        }
    }

    private void reopen(GuiSession old, Loaded next) {
        GuiSession session = new GuiSession(this, old.player, next, old.arguments);
        session.state.putAll(old.state);
        session.pages.putAll(old.pages);
        try {
            loadCollections(session, true);
            session.inventory =
                    Bukkit.createInventory(session, next.definition().rows() * 9, title(session));
            render(session);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not reopen GUI '" + old.guiId + "' after reload", e);
            return;
        }
        old.active = false;
        sessions.put(old.playerId, session);
        old.player.openInventory(session.inventory);
    }

    private void loadCollections(GuiSession session, boolean initial) {
        Bound<?> bound = bindings.get(session.guiId);
        long generation = ++session.generation;
        for (Element element : session.loaded.definition().elements().values()) {
            if (element.type() != Type.COLLECTION || bound == null) {
                continue;
            }
            load(session, bound, element, generation, initial);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void load(GuiSession session, Bound<T> bound, Element element, long generation, boolean initial) {
        GuiBinding.Provider<T> provider = bound.binding().providers().get(element.provider());
        GuiContextImpl context = GuiContextImpl.render(session, element.id(), null);
        Object result;
        try {
            result = provider.invoker().provide(bound.instance(), context);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "GUI provider '" + element.provider() + "' failed", e);
            session.entries.put(element.id(), List.of());
            return;
        }
        if (!provider.async()) {
            session.entries.put(element.id(), checked(element, (List<GuiEntry<?>>) result));
            return;
        }
        if (initial) {
            session.entries.remove(element.id());
        }
        session.loading.add(element.id());
        CompletableFuture<List<GuiEntry<?>>> future = (CompletableFuture<List<GuiEntry<?>>>) result;
        if (future == null) {
            future = CompletableFuture.completedFuture(List.of());
        }
        future.whenComplete((entries, error) -> {
            if (!isCurrent(session) || session.generation != generation) {
                return; // stale: closed, replaced or refreshed again since
            }
            tasks.entity(session.player).run(() -> {
                if (!isCurrent(session) || session.generation != generation) {
                    return;
                }
                session.loading.remove(element.id());
                if (error != null) {
                    logger.log(Level.SEVERE, "GUI provider '" + element.provider() + "' failed", error);
                    session.entries.put(element.id(), List.of());
                } else {
                    session.entries.put(element.id(), checked(element, entries));
                }
                render(session);
            });
        });
    }

    private List<GuiEntry<?>> checked(Element element, @Nullable List<GuiEntry<?>> entries) {
        if (entries == null) {
            return List.of();
        }
        Set<String> ids = new HashSet<>();
        for (GuiEntry<?> entry : entries) {
            if (!ids.add(entry.id())) {
                logger.warning("GUI provider '" + element.provider() + "' returned duplicate entry id '" + entry.id()
                        + "'; clicks on these entries are ambiguous");
            }
        }
        return List.copyOf(entries);
    }

    // ---- rendering ----

    private Component title(GuiSession session) {
        String title = session.loaded.definition().title();
        Locale locale = localization.locale(session.player);
        GuiContextImpl context = GuiContextImpl.render(session, null, null);
        TagResolver resolvers = resolvers(session, context);
        if (localization.lookup(session.loaded.translations(), locale, title) != null) {
            return localization.render(session.loaded.translations(), locale, title, new Arg[0], resolvers);
        }
        return MessageRenderer.render(title, locale, new Arg[0], resolvers);
    }

    void render(GuiSession session) {
        GuiDefinition definition = session.loaded.definition();
        int size = definition.rows() * 9;
        ItemStack[] contents = new ItemStack[size];
        SlotBinding[] slots = new SlotBinding[size];
        for (Element element : definition.elements().values()) {
            int[] elementSlots = definition.slotsOf(element.id());
            if (elementSlots.length == 0 || !visible(session, element.visibleIf(), null)) {
                continue;
            }
            if (element.type() == Type.COLLECTION) {
                renderCollection(session, element, elementSlots, contents, slots);
                continue;
            }
            ItemStack item = build(session, element.item(), GuiContextImpl.render(session, element.id(), null), null);
            for (int slot : elementSlots) {
                contents[slot] = item;
                slots[slot] = new SlotBinding(element.id(), null, element.item());
            }
        }
        if (session.inventory.getSize() != size) {
            throw new IllegalStateException("inventory size changed");
        }
        session.inventory.setContents(contents);
        session.slots = slots;
    }

    private void renderCollection(
            GuiSession session, Element element, int[] elementSlots, ItemStack[] contents, SlotBinding[] slots) {
        List<GuiEntry<?>> entries = session.entries.get(element.id());
        if (entries == null) {
            if (element.loadingItem() != null) {
                contents[elementSlots[0]] =
                        build(session, element.loadingItem(), GuiContextImpl.render(session, element.id(), null), null);
            }
            return;
        }
        int pageSize = pageSize(element, elementSlots);
        int page = clampPage(session, element.id(), entries.size(), pageSize);
        if (entries.isEmpty() && element.emptyItem() != null) {
            contents[elementSlots[0]] =
                    build(session, element.emptyItem(), GuiContextImpl.render(session, element.id(), null), null);
            return;
        }
        for (int i = 0; i < pageSize && i < elementSlots.length; i++) {
            int index = page * pageSize + i;
            if (index >= entries.size()) {
                break;
            }
            GuiEntry<?> entry = entries.get(index);
            int slot = elementSlots[i];
            contents[slot] = build(session, element.item(), GuiContextImpl.render(session, element.id(), entry), entry);
            slots[slot] = new SlotBinding(element.id(), entry, element.item());
        }
    }

    private static int pageSize(Element element, int[] elementSlots) {
        return element.pageSize() > 0 ? element.pageSize() : elementSlots.length;
    }

    private static int pageCount(int entries, int pageSize) {
        return Math.max(1, (entries + pageSize - 1) / pageSize);
    }

    private int clampPage(GuiSession session, String collection, int entries, int pageSize) {
        int page = Math.min(session.pages.getOrDefault(collection, 0), pageCount(entries, pageSize) - 1);
        page = Math.max(0, page);
        session.pages.put(collection, page);
        return page;
    }

    private ItemStack build(GuiSession session, ItemSpec spec, GuiContextImpl context, @Nullable GuiEntry<?> entry) {
        Locale locale = context.locale();
        TranslationTable translations = session.loaded.translations();
        Arg[] args = entryArgs(entry);
        TagResolver resolvers = resolvers(session, context);
        Material material = entry != null && entry.material() != null ? entry.material() : spec.material();
        ItemBuilder builder = ItemBuilder.of(material).amount(spec.amount());
        String key = spec.translation();
        if (key != null && localization.lookup(translations, locale, key + ".name") != null) {
            builder.name(localization.render(translations, locale, key + ".name", args, resolvers));
        } else if (spec.name() != null) {
            builder.name(MessageRenderer.render(spec.name(), locale, args, resolvers));
        }
        if (key != null && localization.lookup(translations, locale, key + ".lore") != null) {
            builder.lore(localization.renderLines(translations, locale, key + ".lore", args, resolvers));
        } else if (spec.lore() != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : spec.lore()) {
                lore.add(MessageRenderer.render(line, locale, args, resolvers));
            }
            builder.lore(lore);
        }
        if (!spec.flags().isEmpty()) {
            builder.flags(spec.flags().toArray(org.bukkit.inventory.ItemFlag[]::new));
        }
        spec.enchantments().forEach((name, level) -> {
            Enchantment enchantment = enchantment(name);
            if (enchantment != null) {
                builder.enchant(enchantment, level);
            }
        });
        if (spec.glow() != null) {
            builder.glow(spec.glow());
        }
        if (spec.customModelData() != null) {
            builder.customModelData(spec.customModelData());
        }
        ItemStack item = builder.build();
        if (spec.renderer() != null) {
            item = applyRenderer(session, spec.renderer(), context, item);
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private <T> ItemStack applyRenderer(GuiSession session, String name, GuiContextImpl context, ItemStack item) {
        Bound<T> bound = (Bound<T>) bindings.get(session.guiId);
        if (bound == null) {
            return item;
        }
        GuiBinding.Renderer<T> renderer = bound.binding().renderers().get(name);
        try {
            ItemStack result = renderer.render(bound.instance(), context, item);
            return result == null ? item : result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "GUI renderer '" + name + "' failed", e);
            return item;
        }
    }

    private @Nullable Enchantment enchantment(String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        Enchantment enchantment = key == null
                ? null
                : RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(key);
        if (enchantment == null && reportedEnchantments.add(name)) {
            logger.warning("Unknown enchantment '" + name + "' in a GUI layout");
        }
        return enchantment;
    }

    private static Arg[] entryArgs(@Nullable GuiEntry<?> entry) {
        if (entry == null || entry.placeholders().isEmpty()) {
            return new Arg[0];
        }
        List<Arg> args = new ArrayList<>();
        entry.placeholders().forEach((name, value) -> args.add(Arg.of(name, value)));
        return args.toArray(Arg[]::new);
    }

    /** Built-in and {@code @GuiPlaceholder} tags, resolved lazily only when a template uses them. */
    private TagResolver resolvers(GuiSession session, GuiContextImpl context) {
        Bound<?> bound = bindings.get(session.guiId);
        return new TagResolver() {
            @Override
            public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context ctx) throws ParsingException {
                Object value = placeholder(session, bound, context, name);
                if (value == null) {
                    return null;
                }
                Component component = value instanceof ComponentLike like
                        ? like.asComponent()
                        : Component.text(String.valueOf(value));
                return Tag.selfClosingInserting(component);
            }

            @Override
            public boolean has(String name) {
                return switch (name) {
                    case "page", "pages", "player" -> true;
                    default -> bound != null && bound.binding().placeholders().containsKey(name);
                };
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable Object placeholder(
            GuiSession session, @Nullable Bound<T> bound, GuiContextImpl context, String name) {
        switch (name) {
            case "player":
                return session.player.getName();
            case "page":
            case "pages": {
                String collection = session.loaded.definition().defaultCollection();
                if (collection == null) {
                    return "1";
                }
                int page = session.pages.getOrDefault(collection, 0);
                if (name.equals("page")) {
                    return Integer.toString(page + 1);
                }
                List<GuiEntry<?>> entries = session.entries.getOrDefault(collection, List.of());
                Element element = session.loaded.definition().elements().get(collection);
                int size = pageSize(element, session.loaded.definition().slotsOf(collection));
                return Integer.toString(pageCount(entries.size(), Math.max(1, size)));
            }
            default:
                break;
        }
        if (bound == null) {
            return null;
        }
        GuiBinding.Placeholder<T> placeholder = bound.binding().placeholders().get(name);
        if (placeholder == null) {
            return null;
        }
        try {
            return placeholder.resolve(bound.instance(), context);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "GUI placeholder '" + name + "' failed", e);
            return "";
        }
    }

    // ---- conditions and actions ----

    @SuppressWarnings("unchecked")
    private boolean visible(GuiSession session, @Nullable String expression, @Nullable GuiEntry<?> entry) {
        if (expression == null) {
            return true;
        }
        boolean negate = expression.startsWith("!");
        String condition = negate ? expression.substring(1) : expression;
        String name = condition.contains(":") ? condition.substring(0, condition.indexOf(':')) : condition;
        String argument = condition.contains(":") ? condition.substring(condition.indexOf(':') + 1) : null;
        boolean result;
        switch (name) {
            case "has_next_page", "has_previous_page", "loading" -> {
                String collection = argument != null
                        ? argument
                        : session.loaded.definition().defaultCollection();
                if (collection == null) {
                    result = false;
                } else if (name.equals("loading")) {
                    result = session.loading.contains(collection);
                } else {
                    int page = session.pages.getOrDefault(collection, 0);
                    if (name.equals("has_previous_page")) {
                        result = page > 0;
                    } else {
                        List<GuiEntry<?>> entries = session.entries.getOrDefault(collection, List.of());
                        Element element = session.loaded.definition().elements().get(collection);
                        int size = element == null
                                ? 1
                                : pageSize(element, session.loaded.definition().slotsOf(collection));
                        result = page + 1 < pageCount(entries.size(), Math.max(1, size));
                    }
                }
            }
            case "permission" -> result = argument != null && session.player.hasPermission(argument);
            default -> {
                Bound<Object> bound = (Bound<Object>) bindings.get(session.guiId);
                GuiBinding.Condition<Object> custom =
                        bound == null ? null : bound.binding().conditions().get(condition);
                if (custom == null) {
                    result = false;
                } else {
                    try {
                        result = custom.test(bound.instance(), GuiContextImpl.render(session, null, entry), entry);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "GUI condition '" + condition + "' failed", e);
                        result = false;
                    }
                }
            }
        }
        return negate != result;
    }

    @SuppressWarnings("unchecked")
    private void dispatch(GuiSession session, SlotBinding binding, String action, ClickType click, int hotbar) {
        String name = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        String argument = action.contains(":") ? action.substring(action.indexOf(':') + 1) : null;
        String collection =
                argument != null ? argument : session.loaded.definition().defaultCollection();
        switch (name) {
            case "close" -> {
                closeSession(session);
                return;
            }
            case "next_page", "previous_page" -> {
                if (collection != null) {
                    int page = session.pages.getOrDefault(collection, 0) + (name.equals("next_page") ? 1 : -1);
                    session.pages.put(collection, Math.max(0, page));
                    render(session);
                }
                return;
            }
            case "refresh" -> {
                refreshSession(session);
                return;
            }
            case "open" -> {
                openFrom(session, argument, Map.of());
                return;
            }
            default -> {}
        }
        Bound<Object> bound = (Bound<Object>) bindings.get(session.guiId);
        GuiBinding.Action<Object> handler =
                bound == null ? null : bound.binding().actions().get(action);
        if (handler == null) {
            logger.warning("GUI '" + session.guiId + "': no handler for action '" + action + "'");
            return;
        }
        if (handler.needsEntry() && binding.entry() == null) {
            logger.warning("GUI '" + session.guiId + "': action '" + action
                    + "' needs a collection entry but was used on element '" + binding.elementId() + "'");
            return;
        }
        GuiContextImpl context = new GuiContextImpl(session, binding.elementId(), binding.entry(), click, hotbar);
        session.dispatching = true;
        try {
            handler.invoker().invoke(bound.instance(), context, binding.entry());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "GUI action '" + action + "' failed for " + session.player.getName(), e);
            localization.send(session.player, "nookfall.gui.action-error");
        } finally {
            session.dispatching = false;
        }
        if (isCurrent(session)) {
            render(session);
        }
    }

    // ---- events ----

    private @Nullable GuiSession sessionOf(Inventory top) {
        return top.getHolder(false) instanceof GuiSession session && session.manager == this ? session : null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        GuiSession session = sessionOf(event.getView().getTopInventory());
        if (session == null) {
            return;
        }
        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < event.getView().getTopInventory().getSize();
        if (!top) {
            // Clicks in the player's own inventory are allowed unless they could move items into the GUI.
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR
                    || action == InventoryAction.UNKNOWN) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (!isCurrent(session) || raw >= session.slots.length) {
            return;
        }
        SlotBinding binding = session.slots[raw];
        if (binding == null) {
            return;
        }
        String action = binding.spec().actionFor(event.getClick());
        if (action == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - session.lastClick < CLICK_DEBOUNCE_NANOS) {
            return;
        }
        session.lastClick = now;
        dispatch(session, binding, action, event.getClick(), event.getHotbarButton());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        GuiSession session = sessionOf(event.getView().getTopInventory());
        if (session == null) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        GuiSession session = sessionOf(event.getInventory());
        if (session != null) {
            session.active = false;
            sessions.remove(session.playerId, session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        GuiSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.active = false;
        }
    }

    /** Sessions currently open, for diagnostics. */
    public Collection<String> describeSessions() {
        List<String> out = new ArrayList<>();
        sessions.forEach((id, session) -> out.add(session.player.getName() + " -> " + session.guiId));
        return out;
    }
}
