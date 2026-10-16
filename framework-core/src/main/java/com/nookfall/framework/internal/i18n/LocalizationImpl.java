package com.nookfall.framework.internal.i18n;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.internal.ResourceIndex;
import com.nookfall.framework.internal.YamlFiles;
import com.nookfall.framework.internal.config.Codecs;
import com.nookfall.framework.message.Arg;
import com.nookfall.framework.message.Localization;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Translations of one plugin. Lookup order for a key: the requested locale, its language, the default locale, then
 * {@code en_US}; plugin translations are consulted before the framework's built-in messages.
 */
public final class LocalizationImpl implements Localization {

    static final Locale FALLBACK = Locale.US;
    private static final List<String> FRAMEWORK_LOCALES = List.of("en_US", "pl_PL", "de_DE");
    private static final String SETTINGS = "settings.yml";

    /** Everything a lookup needs, swapped atomically on reload. */
    private record Snapshot(
            TranslationTable plugin, TranslationTable framework, Locale defaultLocale, boolean clientLocale) {}

    private final Path langFolder;
    private final ResourceIndex resources;
    private final java.util.logging.Logger logger;
    private final Map<UUID, Locale> overrides = new ConcurrentHashMap<>();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();
    private final TranslationTable framework;
    private volatile Snapshot snapshot;

    public LocalizationImpl(Path dataFolder, ResourceIndex resources, java.util.logging.Logger logger) {
        this.langFolder = dataFolder.resolve("lang");
        this.resources = resources;
        this.logger = logger;
        this.framework = loadFrameworkDefaults();
    }

    /** First load: invalid user files are reported and replaced by the bundled version of that locale. */
    public void loadInitial() {
        Loaded loaded = load(true);
        loaded.errors.forEach(logger::severe);
        loaded.warnings.forEach(logger::warning);
        snapshot = loaded.snapshot;
    }

    @Override
    public ReloadResult reload() {
        Loaded loaded = load(false);
        if (!loaded.errors.isEmpty()) {
            List<String> errors = new ArrayList<>(loaded.errors);
            errors.add("lang: keeping the previously loaded translations");
            return ReloadResult.failed(errors);
        }
        snapshot = loaded.snapshot;
        reportedMissing.clear();
        return ReloadResult.ok(loaded.warnings);
    }

    private static final class Loaded {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        Snapshot snapshot;
    }

    private Loaded load(boolean lenient) {
        Loaded out = new Loaded();
        Map<Locale, Map<String, Object>> bundled = new LinkedHashMap<>();
        Map<Locale, String> bundledText = new LinkedHashMap<>();
        for (String path : resources.list("lang/", ".yml")) {
            Locale locale = TranslationTable.localeOf(path);
            if (locale == null) {
                continue;
            }
            String text = resources.read(path).orElseThrow();
            bundled.put(locale, TranslationTable.parse(text, path));
            bundledText.put(locale, text);
        }

        Settings settings;
        try {
            settings = readSettings();
        } catch (RuntimeException e) {
            out.errors.add("lang/" + SETTINGS + ": " + e.getMessage());
            settings = new Settings(FALLBACK, true, true);
        }

        Map<Locale, Map<String, Object>> merged = new LinkedHashMap<>();
        bundled.forEach((locale, values) -> merged.put(locale, new LinkedHashMap<>(values)));
        try {
            Files.createDirectories(langFolder);
            for (Map.Entry<Locale, String> entry : bundledText.entrySet()) {
                Path file = langFolder.resolve(TranslationTable.fileName(entry.getKey()));
                if (!Files.exists(file)) {
                    YamlFiles.writeAtomically(file, entry.getValue());
                }
            }
            List<Path> files;
            try (Stream<Path> listing = Files.list(langFolder)) {
                files = listing.filter(p -> !p.getFileName().toString().equals(SETTINGS))
                        .sorted()
                        .toList();
            }
            for (Path file : files) {
                Locale locale = TranslationTable.localeOf(file.getFileName().toString());
                if (locale == null) {
                    continue;
                }
                String name = "lang/" + file.getFileName();
                YamlConfiguration yaml;
                try {
                    yaml = YamlFiles.parse(Files.readString(file), name);
                } catch (IllegalArgumentException e) {
                    if (lenient) {
                        out.errors.add(e.getMessage() + " (using bundled translations for " + locale + ")");
                    } else {
                        out.errors.add(e.getMessage());
                    }
                    continue;
                }
                Map<String, Object> user = TranslationTable.flatten(yaml);
                Map<String, Object> defaults = bundled.get(locale);
                if (defaults != null && settings.addMissingKeys) {
                    addMissingKeys(file, yaml, user, defaults, out);
                }
                merged.computeIfAbsent(locale, l -> new LinkedHashMap<>()).putAll(user);
            }
        } catch (IOException e) {
            out.errors.add("lang: " + e.getMessage());
        }
        if (!merged.containsKey(settings.defaultLocale)
                && !merged.containsKey(Locale.of(settings.defaultLocale.getLanguage()))) {
            out.warnings.add("lang: default locale " + settings.defaultLocale
                    + " has no translation file, falling back to " + FALLBACK);
        }
        out.snapshot =
                new Snapshot(new TranslationTable(merged), framework, settings.defaultLocale, settings.clientLocale);
        return out;
    }

    private void addMissingKeys(
            Path file, YamlConfiguration yaml, Map<String, Object> user, Map<String, Object> defaults, Loaded out)
            throws IOException {
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!user.containsKey(entry.getKey()) && !yaml.isConfigurationSection(entry.getKey())) {
                yaml.set(entry.getKey(), entry.getValue());
                user.put(entry.getKey(), entry.getValue());
                added.add(entry.getKey());
            }
        }
        if (!added.isEmpty()) {
            YamlFiles.writeAtomically(file, yaml.saveToString());
            out.warnings.add("lang/" + file.getFileName() + ": added " + added.size() + " new key(s): "
                    + String.join(", ", added.subList(0, Math.min(5, added.size())))
                    + (added.size() > 5 ? ", ..." : ""));
        }
    }

    private record Settings(Locale defaultLocale, boolean clientLocale, boolean addMissingKeys) {}

    private Settings readSettings() {
        Path file = langFolder.resolve(SETTINGS);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(langFolder);
                YamlFiles.writeAtomically(file, """
                        # Language used when a player's language has no translation file.
                        default-locale: en_US
                        # Use the language selected in each player's Minecraft client.
                        use-client-locale: true
                        # Add keys introduced by plugin updates to the files in this folder.
                        add-missing-keys: true
                        """);
            }
            YamlConfiguration yaml = YamlFiles.parse(Files.readString(file), "lang/" + SETTINGS);
            return new Settings(
                    Codecs.parseLocale(yaml.getString("default-locale", "en_US")),
                    yaml.getBoolean("use-client-locale", true),
                    yaml.getBoolean("add-missing-keys", true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TranslationTable loadFrameworkDefaults() {
        Map<Locale, Map<String, Object>> out = new LinkedHashMap<>();
        for (String name : FRAMEWORK_LOCALES) {
            try (InputStream in = LocalizationImpl.class.getResourceAsStream("/nookfall/lang/" + name + ".yml")) {
                if (in != null) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    out.put(TranslationTable.localeOf(name + ".yml"), TranslationTable.parse(text, name));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new TranslationTable(out);
    }

    // ---- lookups ----

    /** Ordered locales to try for a requested locale. */
    public List<Locale> chain(Locale locale) {
        Snapshot s = snapshot;
        Set<Locale> chain = new LinkedHashSet<>();
        chain.add(locale);
        chain.add(Locale.of(locale.getLanguage()));
        // Another region of the same language, for example pl_PL for a client set to pl.
        for (Locale available : s.plugin.available()) {
            if (available.getLanguage().equals(locale.getLanguage())) {
                chain.add(available);
            }
        }
        chain.add(s.defaultLocale);
        chain.add(Locale.of(s.defaultLocale.getLanguage()));
        chain.add(FALLBACK);
        chain.add(Locale.ENGLISH);
        return List.copyOf(chain);
    }

    /** Finds a raw template, consulting {@code extra} (for example GUI translations) first. */
    public @Nullable Object lookup(TranslationTable extra, Locale locale, String key) {
        Snapshot s = snapshot;
        List<Locale> chain = chain(locale);
        for (TranslationTable table : new TranslationTable[] {extra, s.plugin, s.framework}) {
            for (Locale candidate : chain) {
                Object value = table.get(candidate, key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    public Component render(TranslationTable extra, Locale locale, String key, Arg[] args, TagResolver resolvers) {
        Object template = lookup(extra, locale, key);
        if (template == null) {
            reportMissing(key);
            return Component.text(key);
        }
        String text = template instanceof List<?> list ? String.join("\n", stringList(list)) : (String) template;
        return MessageRenderer.render(text, locale, args, TagResolver.resolver(resolvers, prefix(extra, locale, text)));
    }

    public List<Component> renderLines(
            TranslationTable extra, Locale locale, String key, Arg[] args, TagResolver resolvers) {
        Object template = lookup(extra, locale, key);
        if (template == null) {
            reportMissing(key);
            return List.of(Component.text(key));
        }
        List<String> lines =
                template instanceof List<?> list ? stringList(list) : List.of(((String) template).split("\n", -1));
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(MessageRenderer.render(
                    line, locale, args, TagResolver.resolver(resolvers, prefix(extra, locale, line))));
        }
        return out;
    }

    /** Whether the template exists and is not empty. */
    public boolean isEmpty(TranslationTable extra, Locale locale, String key) {
        Object template = lookup(extra, locale, key);
        return template instanceof String s && s.isEmpty();
    }

    private TagResolver prefix(TranslationTable extra, Locale locale, String template) {
        if (!template.contains("<prefix>")) {
            return TagResolver.empty();
        }
        Object prefix = lookup(extra, locale, "prefix");
        return prefix instanceof String p ? Placeholder.parsed("prefix", p) : TagResolver.empty();
    }

    private void reportMissing(String key) {
        if (reportedMissing.add(key)) {
            logger.warning("Missing translation key '" + key + "' in every language file");
        }
    }

    private static List<String> stringList(List<?> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    // ---- Localization ----

    @Override
    public Locale defaultLocale() {
        return snapshot.defaultLocale;
    }

    @Override
    public Locale locale(@Nullable Audience audience) {
        if (audience instanceof Player player) {
            Locale override = overrides.get(player.getUniqueId());
            if (override != null) {
                return override;
            }
            if (snapshot.clientLocale) {
                return player.locale();
            }
        }
        return snapshot.defaultLocale;
    }

    @Override
    public void setLocale(UUID player, @Nullable Locale locale) {
        if (locale == null) {
            overrides.remove(player);
        } else {
            overrides.put(player, locale);
        }
    }

    @Override
    public Component message(Locale locale, String key, Arg... args) {
        return render(TranslationTable.EMPTY, locale, key, args, TagResolver.empty());
    }

    @Override
    public Component message(@Nullable Audience audience, String key, Arg... args) {
        return message(locale(audience), key, args);
    }

    @Override
    public List<Component> lines(Locale locale, String key, Arg... args) {
        return renderLines(TranslationTable.EMPTY, locale, key, args, TagResolver.empty());
    }

    @Override
    public void send(Audience recipient, String key, Arg... args) {
        Locale locale = locale(recipient);
        if (isEmpty(TranslationTable.EMPTY, locale, key)) {
            return;
        }
        recipient.sendMessage(message(locale, key, args));
    }

    @Override
    public boolean has(Locale locale, String key) {
        return lookup(TranslationTable.EMPTY, locale, key) != null;
    }

    @Override
    public Set<Locale> availableLocales() {
        return snapshot.plugin.available();
    }
}
