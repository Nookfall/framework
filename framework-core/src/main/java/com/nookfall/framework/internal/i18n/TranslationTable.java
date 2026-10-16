package com.nookfall.framework.internal.i18n;

import com.nookfall.framework.internal.YamlFiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/** Immutable translations per locale. Values are {@code String} or {@code List<String>}. */
public record TranslationTable(Map<Locale, Map<String, Object>> locales) {

    public static final TranslationTable EMPTY = new TranslationTable(Map.of());

    public TranslationTable {
        Map<Locale, Map<String, Object>> copy = new LinkedHashMap<>();
        locales.forEach((locale, values) -> copy.put(locale, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        locales = Collections.unmodifiableMap(copy);
    }

    public @Nullable Object get(Locale locale, String key) {
        Map<String, Object> values = locales.get(locale);
        return values == null ? null : values.get(key);
    }

    public Set<Locale> available() {
        return locales.keySet();
    }

    /** Flattens YAML into dotted keys. */
    public static Map<String, Object> flatten(YamlConfiguration yaml) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection) {
                continue;
            }
            if (value instanceof List<?> list) {
                List<String> lines = new ArrayList<>(list.size());
                for (Object line : list) {
                    lines.add(line == null ? "" : String.valueOf(line));
                }
                out.put(entry.getKey(), List.copyOf(lines));
            } else if (value != null) {
                out.put(entry.getKey(), String.valueOf(value));
            }
        }
        return out;
    }

    public static Map<String, Object> parse(String text, String source) {
        return flatten(YamlFiles.parse(text, source));
    }

    /** Locale from a file name such as {@code en_US.yml} or {@code pl.yml}; {@code null} if not a locale file. */
    public static @Nullable Locale localeOf(String fileName) {
        String base = fileName.substring(fileName.lastIndexOf('/') + 1);
        if (!base.endsWith(".yml")) {
            return null;
        }
        base = base.substring(0, base.length() - 4);
        if (!base.matches("[a-z]{2,3}(_[A-Z]{2})?")) {
            return null;
        }
        String[] parts = base.split("_");
        return parts.length == 1 ? Locale.of(parts[0]) : Locale.of(parts[0], parts[1]);
    }

    public static String fileName(Locale locale) {
        return locale.getCountry().isEmpty()
                ? locale.getLanguage() + ".yml"
                : locale.getLanguage() + "_" + locale.getCountry() + ".yml";
    }
}
