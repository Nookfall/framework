package com.nookfall.framework.internal.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nookfall.framework.internal.YamlFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GuiDefinitionParserTest {

    // Material.isItem() consults the item registry, which needs a server.
    @BeforeAll
    static void server() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    static final String HOMES = """
            rows: 6
            title: homes.title
            layout:
              - "#########"
              - "#HHHHHHH#"
              - "#HHHHHHH#"
              - "#HHHHHHH#"
              - "#P#####N#"
              - "####C####"
            elements:
              border:
                symbol: "#"
                type: item
                item:
                  material: GRAY_STAINED_GLASS_PANE
                  name: "<empty>"
              homes:
                symbol: "H"
                type: collection
                provider: homes
                item-template: home
                page-size: auto
              previous:
                symbol: "P"
                type: button
                action: previous_page
                visible-if: has_previous_page
                item:
                  material: ARROW
                  translation: items.previous
              next:
                symbol: "N"
                type: button
                action: next_page
                visible-if: has_next_page
                item:
                  material: ARROW
                  translation: items.next
              close:
                symbol: "C"
                type: button
                action: close
                item:
                  material: BARRIER
                  translation: items.close
            templates:
              home:
                material: RED_BED
                translation: items.home
                action: home_click
                click-actions:
                  right: delete_home
                  shift-left: info
                flags: [HIDE_ATTRIBUTES]
                enchantments: { unbreaking: 1 }
                glow: true
                custom-model-data: 1001
            """;

    static final GuiDefinitionParser.Names NAMES = new GuiDefinitionParser.Names(
            Set.of("homes"), Set.of("home_click", "delete_home", "info"), Set.of("can_delete"), Set.of("icon"));

    private static GuiDefinition parse(String yaml, List<String> warnings) {
        return GuiDefinitionParser.parse("homes", YamlFiles.parse(yaml, "test"), NAMES, warnings);
    }

    private static List<String> errors(String yaml) {
        try {
            parse(yaml, new ArrayList<>());
        } catch (GuiDefinitionException e) {
            return e.problems();
        }
        throw new AssertionError("expected errors");
    }

    @Test
    void parsesTheDocumentedExample() {
        List<String> warnings = new ArrayList<>();
        GuiDefinition definition = parse(HOMES, warnings);
        assertThat(warnings).isEmpty();
        assertThat(definition.rows()).isEqualTo(6);
        assertThat(definition.slotsOf("homes")).hasSize(21).startsWith(10, 11, 12);
        assertThat(definition.slotsOf("close")).containsExactly(49);
        assertThat(definition.slotsOf("border")).hasSize(9 + 3 * 2 + 7 + 8);
        GuiDefinition.Element homes = definition.elements().get("homes");
        assertThat(homes.type()).isEqualTo(GuiDefinition.Type.COLLECTION);
        assertThat(homes.item().material()).isEqualTo(Material.RED_BED);
        assertThat(homes.item().actionFor(ClickType.LEFT)).isEqualTo("home_click");
        assertThat(homes.item().actionFor(ClickType.RIGHT)).isEqualTo("delete_home");
        assertThat(homes.item().actionFor(ClickType.SHIFT_LEFT)).isEqualTo("info");
        assertThat(homes.item().actionFor(ClickType.NUMBER_KEY)).isNull();
        assertThat(homes.item().customModelData()).isEqualTo(1001);
        assertThat(definition.defaultCollection()).isEqualTo("homes");
    }

    @Test
    void rearrangedLayoutNeedsNoCodeChanges() {
        String rearranged = HOMES.replace("""
                  - "#########"
                  - "#HHHHHHH#"
                  - "#HHHHHHH#"
                  - "#HHHHHHH#"
                  - "#P#####N#"
                  - "####C####"
                """, """
                  - "HHHHHHHHH"
                  - "HHHHHHHHH"
                  - "PC     N#"
                  - "#########"
                  - "#########"
                  - "#########"
                """);
        GuiDefinition definition = parse(rearranged, new ArrayList<>());
        assertThat(definition.slotsOf("homes")).hasSize(18).startsWith(0, 1);
        assertThat(definition.slotsOf("previous")).containsExactly(18);
        assertThat(definition.slotsOf("close")).containsExactly(19);
    }

    @Test
    void rejectsWrongRowWidthsAndCounts() {
        String yaml = HOMES.replace("\"#P#####N#\"", "\"#P####N#\"").replace("rows: 6", "rows: 7");
        assertThat(errors(yaml))
                .anySatisfy(e -> assertThat(e).contains("rows must be a whole number from 1 to 6"))
                .anySatisfy(e -> assertThat(e).contains("layout row 5 has 8 character(s)"));
    }

    @Test
    void rejectsUndeclaredAndDuplicateSymbols() {
        String yaml = HOMES.replace("\"####C####\"", "\"####Z####\"").replace("symbol: \"N\"", "symbol: \"P\"");
        assertThat(errors(yaml))
                .anySatisfy(e -> assertThat(e).contains("uses symbol 'Z' which no element declares"))
                .anySatisfy(e -> assertThat(e).contains("uses symbol 'P' which is already used by previous"));
    }

    @Test
    void rejectsUnknownHandlersAndBadItems() {
        String yaml = HOMES.replace("action: home_click", "action: teleport")
                .replace("provider: homes", "provider: houses")
                .replace("visible-if: has_next_page", "visible-if: can_fly")
                .replace("material: BARRIER", "material: NOT_A_BLOCK")
                .replace("[HIDE_ATTRIBUTES]", "[HIDE_EVERYTHING]")
                .replace("right: delete_home", "sideways: delete_home");
        assertThat(errors(yaml))
                .anySatisfy(e -> assertThat(e).contains("action 'teleport' is neither built in"))
                .anySatisfy(e -> assertThat(e).contains("provider 'houses' has no @GuiProvider"))
                .anySatisfy(e -> assertThat(e).contains("condition 'can_fly' is neither built in"))
                .anySatisfy(e -> assertThat(e).contains("'NOT_A_BLOCK' is not an item material"))
                .anySatisfy(e -> assertThat(e).contains("unknown item flag 'HIDE_EVERYTHING'"))
                .anySatisfy(e -> assertThat(e).contains("click-actions.sideways is not a click type"));
    }

    @Test
    void rejectsPageSizeLargerThanRegion() {
        assertThat(errors(HOMES.replace("page-size: auto", "page-size: 30")))
                .anySatisfy(e -> assertThat(e).contains("page-size 30 exceeds its 21 slot(s)"));
    }

    @Test
    void warnsAboutUnusedElementsAndUnknownOptions() {
        String yaml = HOMES.replace(
                "  close:\n",
                "  unused:\n    symbol: \"U\"\n    item:\n      material: STONE\n      colour: red\n  close:\n");
        List<String> warnings = new ArrayList<>();
        parse(yaml, warnings);
        assertThat(warnings)
                .anySatisfy(w -> assertThat(w).contains("elements.unused (symbol 'U') does not appear in the layout"))
                .anySatisfy(w -> assertThat(w).contains("colour is not a recognised option"));
    }

    @Test
    void negatedAndParameterisedConditions() {
        String yaml = HOMES.replace("visible-if: has_next_page", "visible-if: \"!permission:homes.admin\"");
        assertThat(parse(yaml, new ArrayList<>()).elements().get("next").visibleIf())
                .isEqualTo("!permission:homes.admin");
    }

    @Test
    void missingElementsSectionIsAnError() {
        assertThatThrownBy(() -> parse("rows: 1\nlayout: [\"         \"]\n", new ArrayList<>()))
                .isInstanceOf(GuiDefinitionException.class)
                .hasMessageContaining("elements section is missing");
    }
}
