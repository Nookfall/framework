package com.nookfall.framework.processor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Checks bundled resources against declarations when the resource directory is known (option
 * {@code nookfall.resources}): message keys must exist in the default locale file and GUI layouts must only reference
 * declared handlers.
 */
final class ResourceValidator {

    private final Env env;
    private final Path root;
    private final String defaultLocale;

    ResourceValidator(Env env, Path root, String defaultLocale) {
        this.env = env;
        this.root = root;
        this.defaultLocale = defaultLocale;
    }

    void checkMessages(TypeElement type, Map<String, ExecutableElement> keys) {
        Path file = root.resolve("lang").resolve(defaultLocale + ".yml");
        if (!Files.exists(file)) {
            env.error(
                    type,
                    "@Messages " + type.getSimpleName() + " needs translations but " + root.relativize(file)
                            + " does not exist");
            return;
        }
        Map<String, Object> flat = flatten(load(type, file));
        if (flat == null) {
            return;
        }
        keys.forEach((key, method) -> {
            if (!flat.containsKey(key)) {
                env.error(method, "Translation key '" + key + "' is missing from " + root.relativize(file));
            }
        });
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
