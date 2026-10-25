package com.nookfall.framework.internal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.ConfigDirectory;
import com.nookfall.framework.config.ConfigException;
import com.nookfall.framework.config.Describe;
import com.nookfall.framework.config.Lenient;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class ConfigDirectoryTest {

    public enum Mode {
        @Describe("everything except what is listed")
        BLACKLIST,
        @Describe("only what is listed")
        WHITELIST
    }

    public enum Plain {
        ONE,
        TWO
    }

    @Comment("Prices of one thing.")
    public static class Price {
        private BigDecimal buy = BigDecimal.ONE;
    }

    public static class Category {
        @Comment("Position in the menu.")
        private int position = 100;

        private Material icon = Material.CHEST;
        private Mode mode = Mode.BLACKLIST;
        private List<Plain> plains = new ArrayList<>(List.of(Plain.ONE));
        private Price price = new Price();

        @Lenient
        private Map<Material, Price> items = new LinkedHashMap<>();

        @Lenient
        private Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
    }

    @TempDir
    Path data;

    private final List<String> logged = new ArrayList<>();
    private ConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Logger logger = Logger.getLogger("config-directory-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        service = new ConfigServiceImpl(data, List.of(), logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void write(String name, String yaml) throws IOException {
        Files.createDirectories(data.resolve("categories"));
        Files.writeString(data.resolve("categories").resolve(name + ".yml"), yaml);
    }

    @Test
    void defaultsAreWrittenOnlyWhenTheFolderHasNoFiles() throws IOException {
        int[] calls = {0};
        ConfigDirectory<Category> directory = service.directory(Category.class, "categories", () -> {
            calls[0]++;
            return Map.of("blocks", "position: 1\n", "food", "position: 2\n");
        });
        assertThat(directory.get().keySet()).containsExactly("blocks", "food");
        assertThat(directory.get().get("blocks").position).isEqualTo(1);
        assertThat(Files.exists(data.resolve("categories/blocks.yml"))).isTrue();

        directory.reload();
        assertThat(calls[0])
                .as("defaults are only used to seed an empty folder")
                .isEqualTo(1);

        // Deleting one file does not bring it back.
        Files.delete(data.resolve("categories/food.yml"));
        directory.reload();
        assertThat(directory.get().keySet()).containsExactly("blocks");
    }

    @Test
    void filesAreKeyedByNameInOrderAndMissingKeysAreFilledIn() throws IOException {
        write("b", "position: 5\n");
        write("a", "position: 9\nicon: APPLE\n");
        Files.writeString(data.resolve("categories/notes.txt"), "ignored");
        ConfigDirectory<Category> directory = service.directory(Category.class, "categories", null);
        assertThat(directory.get().keySet()).containsExactly("a", "b");
        assertThat(directory.get().get("a").icon).isEqualTo(Material.APPLE);
        assertThat(directory.get().get("b").icon).isEqualTo(Material.CHEST);
        String rewritten = Files.readString(data.resolve("categories/b.yml"));
        assertThat(rewritten).contains("icon: CHEST").contains("mode: BLACKLIST");
    }

    @Test
    void invalidFilesAreAllReportedAndTheOldFilesStay() throws IOException {
        write("a", "position: 1\n");
        ConfigDirectory<Category> directory = service.directory(Category.class, "categories", null);
        write("a", "position: x\n");
        write("b", "mode: SOMETIMES\n");
        ReloadResult result = directory.reload();
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("categories/a.yml", "position"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("categories/b.yml", "mode"));
        assertThat(directory.get().keySet()).containsExactly("a");
        assertThat(directory.get().get("a").position).isEqualTo(1);
    }

    @Test
    void anInvalidFirstLoadFailsWithEveryProblem() throws IOException {
        write("a", "position: x\n");
        assertThatThrownBy(() -> service.directory(Category.class, "categories", null))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("categories/a.yml");
    }

    @Test
    void lenientMapsSkipWhatThisVersionDoesNotKnow() throws IOException {
        write("a", """
                items:
                  DIAMOND:
                    buy: 5
                  NOT_A_MATERIAL:
                    buy: 9
                enchantments:
                  sharpness: 3
                  FUTURE_ENCHANT: 1
                """);
        ConfigDirectory<Category> directory = service.directory(Category.class, "categories", null);
        Category category = directory.get().get("a");
        assertThat(category.items).containsOnlyKeys(Material.DIAMOND);
        assertThat(category.items.get(Material.DIAMOND).buy).isEqualByComparingTo("5");
        assertThat(category.enchantments).hasSize(1).containsValue(3);
        assertThat(logged).anySatisfy(w -> assertThat(w).contains("NOT_A_MATERIAL", "skipped"));
        assertThat(logged).anySatisfy(w -> assertThat(w).contains("FUTURE_ENCHANT", "skipped"));
    }

    @Test
    void mistakesInsideAnEntryAreStillErrors() throws IOException {
        write("a", "items:\n  DIAMOND:\n    buy: lots\n");
        assertThatThrownBy(() -> service.directory(Category.class, "categories", null))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("items.DIAMOND.buy");
    }

    @Test
    void enumFieldsListTheirOptionsAndDescriptions() {
        assertThat(EnumDocs.lines(Mode.class))
                .containsExactly(
                        "Options:",
                        "  BLACKLIST: everything except what is listed",
                        "  WHITELIST: only what is listed");
        assertThat(EnumDocs.lines(Plain.class)).containsExactly("Options: ONE, TWO");
        assertThat(EnumDocs.lines(Material.class)).as("too many to list").isEmpty();
        assertThat(EnumDocs.lines(String.class)).isEmpty();
    }

    @Test
    void generatedFilesCarryOptionsAndTypeComments() throws IOException {
        write("a", "# my notes\n");
        service.directory(Category.class, "categories", null);
        String yaml = Files.readString(data.resolve("categories/a.yml"));
        assertThat(yaml).contains("# Position in the menu.");
        assertThat(yaml)
                .contains("# Options:\n# BLACKLIST: everything except what is listed"
                        .replace("# BLACKLIST", "#   BLACKLIST"));
        assertThat(yaml).contains("# Options: ONE, TWO");
        assertThat(yaml)
                .as("a type-level comment is the default for fields of that type")
                .contains("# Prices of one thing.\nprice:");
    }
}
