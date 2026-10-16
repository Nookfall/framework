package com.nookfall.framework.internal.gui;

import com.nookfall.framework.internal.gui.GuiDefinition.Element;
import com.nookfall.framework.internal.gui.GuiDefinition.Type;
import com.nookfall.framework.util.item.Materials;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.jspecify.annotations.Nullable;

/**
 * Parses and strictly validates {@code layout.yml}. All problems are collected and reported together so a server
 * owner can fix a file in one pass.
 */
final class GuiDefinitionParser {

    static final Set<String> BUILTIN_ACTIONS = Set.of("close", "next_page", "previous_page", "refresh", "open");
    static final Set<String> BUILTIN_CONDITIONS = Set.of("has_next_page", "has_previous_page", "permission", "loading");

    private static final Set<String> ELEMENT_KEYS = Set.of(
            "symbol",
            "type",
            "item",
            "action",
            "click-actions",
            "visible-if",
            "provider",
            "item-template",
            "page-size",
            "empty-item",
            "loading-item");
    private static final Set<String> ITEM_KEYS = Set.of(
            "material",
            "amount",
            "name",
            "lore",
            "translation",
            "flags",
            "enchantments",
            "glow",
            "custom-model-data",
            "renderer",
            "action",
            "click-actions");

    /** Names the Java side provides, used to validate references. */
    record Names(Set<String> providers, Set<String> actions, Set<String> conditions, Set<String> renderers) {
        static final Names NONE = new Names(Set.of(), Set.of(), Set.of(), Set.of());
    }

    private final String id;
    private final Names names;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private GuiDefinitionParser(String id, Names names) {
        this.id = id;
        this.names = names;
    }

    /**
     * @throws GuiDefinitionException if the layout is invalid
     */
    static GuiDefinition parse(String id, ConfigurationSection yaml, Names names, List<String> warningsOut) {
        GuiDefinitionParser parser = new GuiDefinitionParser(id, names);
        GuiDefinition definition = parser.parse(yaml);
        warningsOut.addAll(parser.warnings);
        if (!parser.errors.isEmpty()) {
            throw new GuiDefinitionException(id, parser.errors);
        }
        return definition;
    }

    private GuiDefinition parse(ConfigurationSection yaml) {
        int rows = yaml.getInt("rows", -1);
        if (!yaml.isInt("rows") || rows < 1 || rows > 6) {
            errors.add("rows must be a whole number from 1 to 6");
            rows = Math.max(1, Math.min(6, rows));
        }
        String title = yaml.getString("title", "");
        List<String> layout = yaml.getStringList("layout");
        if (!yaml.isList("layout")) {
            errors.add("layout must be a list of rows");
        } else if (layout.size() != rows) {
            errors.add("layout has " + layout.size() + " row(s) but rows is " + rows);
        }
        for (int r = 0; r < layout.size(); r++) {
            int length = layout.get(r).codePointCount(0, layout.get(r).length());
            if (length != 9) {
                errors.add("layout row " + (r + 1) + " has " + length + " character(s); every row needs exactly 9");
            }
        }

        Map<String, Element> elements = new LinkedHashMap<>();
        Map<Character, String> bySymbol = new LinkedHashMap<>();
        ConfigurationSection templates = yaml.getConfigurationSection("templates");
        ConfigurationSection elementSection = yaml.getConfigurationSection("elements");
        if (elementSection == null) {
            errors.add("elements section is missing");
        } else {
            for (String key : elementSection.getKeys(false)) {
                ConfigurationSection section = elementSection.getConfigurationSection(key);
                if (section == null) {
                    errors.add("elements." + key + " must be a section");
                    continue;
                }
                Element element = element(key, section, templates);
                if (element == null) {
                    continue;
                }
                String previous = bySymbol.putIfAbsent(element.symbol(), key);
                if (previous != null) {
                    errors.add("elements." + key + " uses symbol '" + element.symbol() + "' which is already used by "
                            + previous);
                }
                elements.put(key, element);
            }
        }
        if (templates != null) {
            for (String key : templates.getKeys(false)) {
                boolean used = elements.values().stream()
                        .anyMatch(e -> e.type() == Type.COLLECTION && key.equals(templateName(elementSection, e.id())));
                if (!used) {
                    warnings.add("templates." + key + " is not used by any collection");
                }
            }
        }

        Map<String, List<Integer>> slotLists = new LinkedHashMap<>();
        for (int r = 0; r < layout.size(); r++) {
            int[] codePoints = layout.get(r).codePoints().toArray();
            for (int c = 0; c < Math.min(9, codePoints.length); c++) {
                int cp = codePoints[c];
                if (cp == ' ') {
                    continue;
                }
                String elementId = cp <= Character.MAX_VALUE ? bySymbol.get((char) cp) : null;
                if (elementId == null) {
                    errors.add("layout row " + (r + 1) + " column " + (c + 1) + " uses symbol '"
                            + new String(Character.toChars(cp)) + "' which no element declares");
                    continue;
                }
                slotLists.computeIfAbsent(elementId, k -> new ArrayList<>()).add(r * 9 + c);
            }
        }
        Map<String, int[]> slots = new LinkedHashMap<>();
        for (Element element : List.copyOf(elements.values())) {
            List<Integer> list = slotLists.get(element.id());
            if (list == null) {
                warnings.add("elements." + element.id() + " (symbol '" + element.symbol()
                        + "') does not appear in the layout");
                continue;
            }
            slots.put(element.id(), list.stream().mapToInt(Integer::intValue).toArray());
            if (element.type() == Type.COLLECTION && element.pageSize() > list.size()) {
                errors.add("elements." + element.id() + " page-size " + element.pageSize() + " exceeds its "
                        + list.size() + " slot(s)");
            }
        }
        return new GuiDefinition(id, rows, title, List.copyOf(layout), Map.copyOf(elements), Map.copyOf(slots));
    }

    private static @Nullable String templateName(@Nullable ConfigurationSection elements, String elementId) {
        return elements == null ? null : elements.getString(elementId + ".item-template");
    }

    private @Nullable Element element(
            String key, ConfigurationSection section, @Nullable ConfigurationSection templates) {
        String path = "elements." + key;
        unknownKeys(section, ELEMENT_KEYS, path);
        String symbol = section.getString("symbol", "");
        if (symbol.length() != 1 || symbol.equals(" ")) {
            errors.add(path + ".symbol must be exactly one character other than a space");
            return null;
        }
        String typeName = section.getString("type", "item").toLowerCase(Locale.ROOT);
        Type type;
        try {
            type = Type.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(path + ".type must be item, button or collection");
            return null;
        }
        String visibleIf = section.getString("visible-if");
        if (visibleIf != null) {
            checkCondition(visibleIf, path + ".visible-if");
        }
        if (type == Type.COLLECTION) {
            String provider = section.getString("provider");
            if (provider == null) {
                errors.add(path + ".provider is required for a collection");
            } else if (!names.providers().contains(provider)) {
                errors.add(path + ".provider '" + provider + "' has no @GuiProvider(\"" + provider + "\") method");
            }
            String templateName = section.getString("item-template");
            ItemSpec template = null;
            if (templateName == null) {
                errors.add(path + ".item-template is required for a collection");
            } else if (templates == null || !templates.isConfigurationSection(templateName)) {
                errors.add(path + ".item-template '" + templateName + "' is not defined under templates");
            } else {
                template = item(templates.getConfigurationSection(templateName), "templates." + templateName, true);
            }
            int pageSize = 0;
            Object rawPageSize = section.get("page-size", "auto");
            if (!"auto".equals(String.valueOf(rawPageSize))) {
                if (rawPageSize instanceof Integer size && size > 0) {
                    pageSize = size;
                } else {
                    errors.add(path + ".page-size must be auto or a positive whole number");
                }
            }
            ItemSpec empty = optionalItem(section, "empty-item", path);
            ItemSpec loading = optionalItem(section, "loading-item", path);
            return new Element(key, symbol.charAt(0), type, template, visibleIf, provider, pageSize, empty, loading);
        }
        ConfigurationSection itemSection = section.getConfigurationSection("item");
        if (itemSection == null) {
            errors.add(path + ".item is required");
            return null;
        }
        ItemSpec item = item(itemSection, path + ".item", false);
        if (item != null && (section.contains("action") || section.contains("click-actions"))) {
            item = withActions(item, section, path);
        }
        if (type == Type.BUTTON
                && item != null
                && item.action() == null
                && item.clickActions().isEmpty()) {
            errors.add(path + " is a button but has no action");
        }
        return new Element(key, symbol.charAt(0), type, item, visibleIf, null, 0, null, null);
    }

    private @Nullable ItemSpec optionalItem(ConfigurationSection section, String key, String path) {
        ConfigurationSection child = section.getConfigurationSection(key);
        return child == null ? null : item(child, path + "." + key, false);
    }

    private ItemSpec withActions(ItemSpec item, ConfigurationSection section, String path) {
        String action = section.getString("action", item.action());
        if (action != null) {
            checkAction(action, path + ".action", false);
        }
        Map<ClickType, String> clicks = new LinkedHashMap<>(item.clickActions());
        clicks.putAll(clickActions(section, path, false));
        return new ItemSpec(
                item.material(),
                item.amount(),
                item.name(),
                item.lore(),
                item.translation(),
                item.flags(),
                item.enchantments(),
                item.glow(),
                item.customModelData(),
                item.renderer(),
                action,
                Map.copyOf(clicks));
    }

    private Map<ClickType, String> clickActions(ConfigurationSection section, String path, boolean entryAvailable) {
        Map<ClickType, String> out = new LinkedHashMap<>();
        ConfigurationSection clicks = section.getConfigurationSection("click-actions");
        if (clicks == null) {
            return out;
        }
        for (String click : clicks.getKeys(false)) {
            ClickType type = clickType(click);
            if (type == null) {
                errors.add(path + ".click-actions." + click + " is not a click type (left, right, shift-left, "
                        + "shift-right, middle, number-key, drop, control-drop, double-click, swap-offhand)");
                continue;
            }
            String action = clicks.getString(click, "");
            checkAction(action, path + ".click-actions." + click, entryAvailable);
            out.put(type, action);
        }
        return out;
    }

    static @Nullable ClickType clickType(String name) {
        String normalized = name.toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("SWAP_OFFHAND")) {
            return ClickType.SWAP_OFFHAND;
        }
        try {
            return ClickType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private @Nullable ItemSpec item(@Nullable ConfigurationSection section, String path, boolean template) {
        if (section == null) {
            errors.add(path + " must be a section");
            return null;
        }
        unknownKeys(section, ITEM_KEYS, path);
        String materialName = section.getString("material");
        Material material = null;
        if (materialName == null) {
            errors.add(path + ".material is required");
        } else {
            material = Materials.findItem(materialName).orElse(null);
            if (material == null) {
                errors.add(path + ".material '" + materialName + "' is not an item material");
            }
        }
        int amount = section.getInt("amount", 1);
        if (amount < 1 || amount > 99) {
            errors.add(path + ".amount must be between 1 and 99");
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (String flag : section.getStringList("flags")) {
            try {
                flags.add(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                errors.add(path + ".flags: unknown item flag '" + flag + "'");
            }
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants != null) {
            for (String key : enchants.getKeys(false)) {
                int level = enchants.getInt(key, 1);
                if (!key.matches("[a-z0-9_.:-]+") || level < 1) {
                    errors.add(path + ".enchantments." + key + " must be a lowercase key with a level of at least 1");
                } else {
                    enchantments.put(key, level);
                }
            }
        }
        Integer customModelData = section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null;
        if (section.contains("custom-model-data") && customModelData == null) {
            errors.add(path + ".custom-model-data must be a whole number");
        }
        String renderer = section.getString("renderer");
        if (renderer != null && !names.renderers().contains(renderer)) {
            errors.add(path + ".renderer '" + renderer + "' has no @GuiRenderer(\"" + renderer + "\") method");
        }
        String action = section.getString("action");
        if (action != null) {
            checkAction(action, path + ".action", template);
        }
        Map<ClickType, String> clicks = clickActions(section, path, template);
        List<String> lore = section.isList("lore") ? List.copyOf(section.getStringList("lore")) : null;
        Boolean glow = section.isBoolean("glow") ? section.getBoolean("glow") : null;
        if (material == null) {
            return null;
        }
        return new ItemSpec(
                material,
                amount,
                section.getString("name"),
                lore,
                section.getString("translation"),
                List.copyOf(flags),
                Map.copyOf(enchantments),
                glow,
                customModelData,
                renderer,
                action,
                Map.copyOf(clicks));
    }

    private void checkAction(String action, String path, boolean entryAvailable) {
        String name = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        if (BUILTIN_ACTIONS.contains(name)) {
            if (name.equals("open") && !action.contains(":")) {
                errors.add(path + ": use open:<gui id>");
            }
            return;
        }
        if (!names.actions().contains(action)) {
            errors.add(path + ": action '" + action + "' is neither built in (" + String.join(", ", BUILTIN_ACTIONS)
                    + ") nor a @GuiAction method");
        }
    }

    private void checkCondition(String expression, String path) {
        String condition = expression.startsWith("!") ? expression.substring(1) : expression;
        String name = condition.contains(":") ? condition.substring(0, condition.indexOf(':')) : condition;
        if (!BUILTIN_CONDITIONS.contains(name) && !names.conditions().contains(condition)) {
            errors.add(path + ": condition '" + condition + "' is neither built in ("
                    + String.join(", ", BUILTIN_CONDITIONS) + ") nor a @GuiCondition method");
        }
    }

    private void unknownKeys(ConfigurationSection section, Set<String> known, String path) {
        for (String key : section.getKeys(false)) {
            if (!known.contains(key)) {
                warnings.add(path + "." + key + " is not a recognised option");
            }
        }
    }
}
