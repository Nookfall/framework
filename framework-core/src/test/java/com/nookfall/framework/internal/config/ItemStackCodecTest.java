package com.nookfall.framework.internal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nookfall.framework.util.item.ItemBuilder;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemStackCodecTest {

    private final ItemStackCodec codec = new ItemStackCodec();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void plainMaterialNamesRoundTrip() {
        ItemStack item = codec.decode("diamond");
        assertThat(item).isEqualTo(new ItemStack(Material.DIAMOND));
        assertThat(codec.encode(item)).isEqualTo("DIAMOND");
    }

    @Test
    void readableSectionsRoundTrip() {
        ItemStack item = codec.decode(Map.of(
                "material",
                "DIAMOND_SWORD",
                "name",
                "<red>Blade",
                "lore",
                List.of("line one", "<gray>line two"),
                "flags",
                List.of("hide_attributes"),
                "glow",
                true,
                "custom-model-data",
                7));
        assertThat(item.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(item.getItemMeta().displayName())
                .isEqualTo(ItemBuilder.of(Material.STONE)
                        .name(Component.text("Blade", NamedTextColor.RED))
                        .build()
                        .getItemMeta()
                        .displayName());
        assertThat(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)).isTrue();

        Object encoded = codec.encode(item);
        assertThat(encoded).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) encoded;
        assertThat(map)
                .containsEntry("material", "DIAMOND_SWORD")
                .containsEntry("name", "<red>Blade")
                .containsEntry("lore", List.of("line one", "<gray>line two"))
                .containsEntry("glow", true)
                .containsEntry("custom-model-data", 7)
                .doesNotContainKey("data");
        assertThat(codec.decode(encoded)).isEqualTo(item);
    }

    @Test
    void amountsAreWrittenOnlyWhenNotOne() {
        ItemStack item = codec.decode(Map.of("material", "arrow", "amount", 16));
        assertThat(item.getAmount()).isEqualTo(16);
        assertThat(codec.encode(item)).isEqualTo(Map.of("material", "ARROW", "amount", 16));
    }

    @Test
    void rejectsBadInputWithReadableMessages() {
        assertThatThrownBy(() -> codec.decode("not_a_thing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not_a_thing");
        assertThatThrownBy(() -> codec.decode(Map.of("amount", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("material");
        assertThatThrownBy(() -> codec.decode(Map.of("material", "STONE", "colour", "red")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown item key 'colour'");
        assertThatThrownBy(() -> codec.decode(Map.of("material", "STONE", "amount", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 99");
        assertThatThrownBy(() -> codec.decode(Map.of("material", "STONE", "data", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'data' cannot be combined");
    }
}
