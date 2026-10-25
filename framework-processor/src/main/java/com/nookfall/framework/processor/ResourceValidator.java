package com.nookfall.framework.processor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Checks bundled resources against declarations when the resource directory is known (option
 * {@code nookfall.resources}): message keys and placeholders must match the default locale files, and GUI layouts must
 * only reference declared handlers and translation keys.
 */
final class ResourceValidator {

    private final Env env;
    private final Path root;
    private final String defaultLocale;

    private static final Pattern ESCAPED_TAG = Pattern.compile("\\\\<[^<>]*>");
    private static final Pattern TAG = Pattern.compile("<([^<>]+)>");
    /** Matches files the runtime loads as locales; {@code settings.yml} and notes are ignored. */
    private static final Pattern LOCALE_FILE = Pattern.compile("[a-z]{2,3}(_[A-Z]{2})?\\.yml");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    ResourceValidator(Env env, Path root, String defaultLocale) {
        this.env = env;
        this.root = root;
        this.defaultLocale = defaultLocale;
    }

    void checkMessages(TypeElement type, Map<String, ExecutableElement> keys, Map<String, Set<String>> placeholders) {
        Path file = root.resolve("lang").resolve(defaultLocale + ".yml");
        if (!Files.exists(file)) {
            env.error(
                    type,
                    "@Messages " + type.getSimpleName() + " needs translations but " + root.relativize(file)
                            + " does not exist");
            return;
        }
        for (Path locale : yamlFiles(root.resolve("lang"))) {
            Map<String, Object> flat = flatten(load(type, locale));
            if (flat == null) {
                continue;
            }
            if (locale.equals(file)) {
                keys.forEach((key, method) -> {
                    if (!flat.containsKey(key)) {
                        env.error(method, "Translation key '" + key + "' is missing from " + root.relativize(file));
                    }
                });
            }
            checkPlaceholders(locale, flat, keys, placeholders);
        }
    }

    /**
     * Every MiniMessage {@code <placeholder>} that is not a known tag must be a parameter of the method that owns the
     * key. Parsing with MiniMessage keeps the check in step with tag support; unknown tags survive as literal text.
     */
    private void checkPlaceholders(
            Path file,
            Map<String, Object> flat,
            Map<String, ExecutableElement> keys,
            Map<String, Set<String>> placeholders) {
        for (Map.Entry<String, ExecutableElement> entry : keys.entrySet()) {
            Object value = flat.get(entry.getKey());
            if (value == null) {
                continue;
            }
            Set<String> used = new LinkedHashSet<>();
            collectPlaceholders(value, used);
            // Framework-provided tags: <prefix> inserts the language prefix, <empty> renders nothing.
            used.remove("prefix");
            used.remove("empty");
            for (String name : used) {
                if (!placeholders.getOrDefault(entry.getKey(), Set.of()).contains(name)) {
                    env.error(
                            entry.getValue(),
                            "Placeholder <" + name + "> in " + root.relativize(file) + " for key '" + entry.getKey()
                                    + "' is not a parameter; add a parameter or use @Param");
                }
            }
        }
    }

    private static void collectPlaceholders(Object value, Set<String> out) {
        if (value instanceof String text) {
            collectPlaceholders(text, out);
        } else if (value instanceof List<?> lines) {
            for (Object line : lines) {
                if (line instanceof String text) {
                    collectPlaceholders(text, out);
                }
            }
        }
    }

    private static void collectPlaceholders(String template, Set<String> out) {
        // Escaped tags are literal text, not placeholders.
        String text = ESCAPED_TAG.matcher(template).replaceAll("");
        try {
            Component parsed = MINI_MESSAGE.deserialize(text);
            Matcher tags = TAG.matcher(PlainTextComponentSerializer.plainText().serialize(parsed));
            while (tags.find()) {
                out.add(tags.group(1));
            }
        } catch (RuntimeException ignored) {
            // Malformed MiniMessage is reported at runtime; do not turn it into a compile-time placeholder error.
        }
    }

    /** Ids of GUIs bundled as {@code guis/<id>/layout.yml}, for generated constants. */
    Set<String> guiIds() {
        Path guis = root.resolve("guis");
        if (!Files.isDirectory(guis)) {
            return Set.of();
        }
        try (Stream<Path> entries = Files.list(guis)) {
            return entries.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("layout.yml")))
                    .map(dir -> dir.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            env.note("Could not list " + root.relativize(guis) + ": " + e.getMessage());
            return Set.of();
        }
    }

    private List<Path> yamlFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(file ->
                            LOCALE_FILE.matcher(file.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            env.note("Could not list " + root.relativize(dir) + ": " + e.getMessage());
            return List.of();
        }
    }

    void checkGui(TypeElement type, String id, GuiAnalyzer.Declared declared) {
        Path file = root.resolve("guis").resolve(id).resolve("layout.yml");
        if (!Files.exists(file)) {
            env.error(type, "@Gui(\"" + id + "\") has no layout file at " + root.relativize(file));
            return;
        }
        Object loaded = load(type, file);
        if (!(loaded instanceof Map<?, ?> yaml)) {
            if (loaded != null) {
                env.error(type, root.relativize(file) + " must be a YAML mapping");
            }
            return;
        }
        String where = root.relativize(file) + ": ";
        Set<String> translationKeys = guiTranslationKeys(type, id);
        String translationFiles =
                root.relativize(root.resolve("guis").resolve(id).resolve("lang").resolve(defaultLocale + ".yml"))
                        + " or " + root.relativize(root.resolve("lang").resolve(defaultLocale + ".yml"));
        Object rows = yaml.get("rows");
        if (!(rows instanceof Integer r) || r < 1 || r > 6) {
            env.error(type, where + "rows must be a whole number from 1 to 6");
        }
        Set<Character> symbols = new java.util.HashSet<>();
        if (yaml.get("elements") instanceof Map<?, ?> elements) {
            for (Map.Entry<?, ?> entry : elements.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> element)) {
                    env.error(type, where + "elements." + entry.getKey() + " must be a section");
                    continue;
                }
                String path = "elements." + entry.getKey();
                String symbol = String.valueOf(element.get("symbol"));
                if (symbol.length() != 1 || symbol.equals(" ")) {
                    env.error(type, where + path + ".symbol must be exactly one character other than a space");
                } else if (!symbols.add(symbol.charAt(0))) {
                    env.error(type, where + path + ".symbol '" + symbol + "' is used by more than one element");
                }
                reference(type, where + path + ".provider", element.get("provider"), declared.providers(), Set.of());
                reference(
                        type,
                        where + path + ".visible-if",
                        stripCondition(element.get("visible-if")),
                        declared.conditions(),
                        GuiAnalyzer.BUILTIN_CONDITIONS);
                actions(type, where + path, element, declared);
                templates(type, where + path, element, yaml.get("templates"));
                translations(type, where + path, element, translationKeys, translationFiles);
                if (element.get("item") instanceof Map<?, ?> item) {
                    actions(type, where + path + ".item", item, declared);
                    reference(
                            type,
                            where + path + ".item.renderer",
                            item.get("renderer"),
                            declared.renderers(),
                            Set.of());
                }
            }
        } else {
            env.error(type, where + "elements section is missing");
        }
        if (yaml.get("templates") instanceof Map<?, ?> templates) {
            for (Map.Entry<?, ?> entry : templates.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> template) {
                    actions(type, where + "templates." + entry.getKey(), template, declared);
                    translations(
                            type, where + "templates." + entry.getKey(), template, translationKeys, translationFiles);
                    reference(
                            type,
                            where + "templates." + entry.getKey() + ".renderer",
                            template.get("renderer"),
                            declared.renderers(),
                            Set.of());
                }
            }
        }
        if (yaml.get("layout") instanceof List<?> layout) {
            if (rows instanceof Integer r && layout.size() != r) {
                env.error(type, where + "layout has " + layout.size() + " row(s) but rows is " + r);
            }
            for (int i = 0; i < layout.size(); i++) {
                String row = String.valueOf(layout.get(i));
                if (row.codePointCount(0, row.length()) != 9) {
                    env.error(type, where + "layout row " + (i + 1) + " must have exactly 9 characters");
                }
                for (char c : row.toCharArray()) {
                    if (c != ' ' && !symbols.contains(c)) {
                        env.error(type, where + "layout row " + (i + 1) + " uses undeclared symbol '" + c + "'");
                    }
                }
            }
        } else {
            env.error(type, where + "layout must be a list of rows");
        }
    }

    /** A collection's item-template and variant templates must exist under templates. */
    private void templates(TypeElement type, String path, Map<?, ?> element, Object templates) {
        Map<?, ?> defined = templates instanceof Map<?, ?> map ? map : Map.of();
        if (element.get("item-template") instanceof String name && !(defined.get(name) instanceof Map<?, ?>)) {
            env.error(type, path + ".item-template '" + name + "' is not defined under templates");
        }
        if (element.get("variants") instanceof Map<?, ?> variants) {
            for (Map.Entry<?, ?> variant : variants.entrySet()) {
                if (!(defined.get(String.valueOf(variant.getValue())) instanceof Map<?, ?>)) {
                    env.error(
                            type,
                            path + ".variants." + variant.getKey() + " '" + variant.getValue()
                                    + "' is not defined under templates");
                }
            }
        }
    }

    /** Keys from the GUI's default-locale file and the plugin's default-locale file, which is the lookup order. */
    private Set<String> guiTranslationKeys(TypeElement type, String id) {
        Set<String> keys = new LinkedHashSet<>();
        for (Path file : List.of(
                root.resolve("guis").resolve(id).resolve("lang").resolve(defaultLocale + ".yml"),
                root.resolve("lang").resolve(defaultLocale + ".yml"))) {
            if (!Files.exists(file)) {
                continue;
            }
            Map<String, Object> flat = flatten(load(type, file));
            if (flat != null) {
                keys.addAll(flat.keySet());
            }
        }
        return keys;
    }

    /**
     * {@code translation: key} resolves {@code key.name} and {@code key.lore}; a bundled layout naming a key the
     * default locale cannot provide is almost always a typo. Framework-provided {@code nookfall.*} keys are exempt.
     */
    private void translations(TypeElement type, String path, Map<?, ?> section, Set<String> keys, String files) {
        if (section.get("translation") instanceof String key
                && !key.startsWith("nookfall.")
                && !keys.contains(key + ".name")
                && !keys.contains(key + ".lore")) {
            env.error(
                    type,
                    path + ".translation '" + key + "' has no " + key + ".name or " + key + ".lore entry in " + files);
        }
        for (String child : List.of("item", "empty-item", "loading-item")) {
            if (section.get(child) instanceof Map<?, ?> item) {
                translations(type, path + "." + child, item, keys, files);
            }
        }
    }

    private static Object stripCondition(Object value) {
        if (value instanceof String s) {
            return s.startsWith("!") ? s.substring(1) : s;
        }
        return value;
    }

    private void actions(TypeElement type, String path, Map<?, ?> section, GuiAnalyzer.Declared declared) {
        reference(type, path + ".action", section.get("action"), declared.actions(), GuiAnalyzer.BUILTIN_ACTIONS);
        if (section.get("click-actions") instanceof Map<?, ?> clicks) {
            for (Map.Entry<?, ?> click : clicks.entrySet()) {
                reference(
                        type,
                        path + ".click-actions." + click.getKey(),
                        click.getValue(),
                        declared.actions(),
                        GuiAnalyzer.BUILTIN_ACTIONS);
            }
        }
    }

    private void reference(TypeElement type, String path, Object value, Set<String> declared, Set<String> builtin) {
        if (value == null) {
            return;
        }
        String name = String.valueOf(value);
        String base = name.contains(":") ? name.substring(0, name.indexOf(':')) : name;
        if (!builtin.contains(base) && !declared.contains(name)) {
            env.error(type, path + ": '" + name + "' is not declared in " + type.getSimpleName());
        }
    }

    private Object load(Element element, Path file) {
        try {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
        } catch (IOException | YAMLException e) {
            env.error(element, root.relativize(file) + " is not valid YAML: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> flatten(Object yaml) {
        if (yaml == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        flatten("", yaml, out);
        return out;
    }

    private static void flatten(String prefix, Object value, Map<String, Object> out) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                flatten(
                        prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey(),
                        entry.getValue(),
                        out);
            }
        } else {
            out.put(prefix, value);
        }
    }
}
