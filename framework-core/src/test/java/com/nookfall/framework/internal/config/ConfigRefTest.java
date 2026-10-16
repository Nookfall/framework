package com.nookfall.framework.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.config.Codec;
import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.ConfigCodec;
import com.nookfall.framework.config.ConfigKey;
import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.constraint.NotBlank;
import com.nookfall.framework.constraint.Pattern;
import com.nookfall.framework.constraint.Range;
import com.nookfall.framework.constraint.Size;
import com.nookfall.framework.spi.ConfigSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigRefTest {

    enum Mode {
        FAST,
        SAFE
    }

    static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class PointCodec implements ConfigCodec<Point> {
        @Override
        public Point decode(Object raw) {
            String[] parts = String.valueOf(raw).split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("expected x,y");
            }
            return new Point(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()));
        }

        @Override
        public Object encode(Point value) {
            return value.x + "," + value.y;
        }
    }

    /** A type-level codec applies everywhere the type appears, including inside lists. */
    @Codec(ColorCodec.class)
    record Color(int rgb) {}

    public static final class ColorCodec implements ConfigCodec<Color> {
        @Override
        public Color decode(Object raw) {
            String text = String.valueOf(raw);
            if (!text.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("expected #RRGGBB but got " + text);
            }
            return new Color(Integer.parseInt(text.substring(1), 16));
        }

        @Override
        public Object encode(Color value) {
            return String.format("#%06x", value.rgb());
        }
    }

    static class Reward {
        private String item = "DIAMOND";
        private int amount = 1;
    }

    static class Database {
        @Comment("Host name.")
        private String host = "localhost";

        @Range(min = 1, max = 65535)
        private int port = 3306;
    }

    static class TestConfig {
        @Comment({"Maximum number of homes.", "Between 1 and 100."})
        @Range(min = 1, max = 100)
        private int maxHomes = 3;

        @Comment("Delay before teleporting.")
        private Duration teleportDelay = Duration.ofSeconds(5);

        private boolean safeTeleport = true;
        private Mode mode = Mode.SAFE;
        private List<String> worlds = List.of("world", "world_nether");
        private Map<String, Integer> limits = new LinkedHashMap<>(Map.of("vip", 10));

        @Size(max = 5)
        private List<Reward> rewards = new ArrayList<>(List.of(new Reward()));

        private Database database = new Database();

        @ConfigKey("message-prefix")
        @NotBlank
        @Pattern("[^\\n]*")
        private String prefix = "[Homes]";

        @Codec(PointCodec.class)
        private Point spawn = new Point(0, 64);

        private List<Color> palette = List.of(new Color(0xff0000));

        private transient int notSaved = 5;
        private static final int CONSTANT = 1;
    }

    @TempDir
    Path dir;

    private ConfigRef<TestConfig> ref() {
        ConfigServiceImpl service = new ConfigServiceImpl(
                dir,
                List.of(new ConfigSpec<>(TestConfig.class, "homes.yml", List.of("Homes settings"), TestConfig::new)),
                Logger.getLogger("test"));
        service.loadInitial();
        return service.ref(TestConfig.class);
    }

    @Test
    void writesDefaultsWithCommentsAndHeader() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        String text = Files.readString(dir.resolve("homes.yml"));
        assertThat(text)
                .contains("# Homes settings")
                .contains("# Maximum number of homes.\n# Between 1 and 100.\nmax-homes: 3")
                .contains("teleport-delay: 5s")
                .contains("mode: SAFE")
                .contains("message-prefix: '[Homes]'")
                .contains("spawn: 0,64")
                .contains("database:\n  # Host name.\n  host: localhost")
                .doesNotContain("not-saved")
                .doesNotContain("constant");
        assertThat(ref.get().maxHomes).isEqualTo(3);
        assertThat(ref.value("database.port")).contains(3306);
    }

    @Test
    void readsUserValuesIncludingNestedListsAndMaps() throws Exception {
        Files.writeString(dir.resolve("homes.yml"), """
                max-homes: 7
                teleport-delay: 1m30s
                safe-teleport: no
                mode: fast
                worlds: [a, b]
                limits:
                  vip: 20
                  mvp: 30
                rewards:
                  - item: EMERALD
                    amount: 4
                  - item: GOLD_INGOT
                database:
                  host: db.example
                message-prefix: "<red>Homes"
                spawn: "10, 70"
                """);
        TestConfig config = ref().get();
        assertThat(config.maxHomes).isEqualTo(7);
        assertThat(config.teleportDelay).isEqualTo(Duration.ofSeconds(90));
        assertThat(config.safeTeleport).isFalse();
        assertThat(config.mode).isEqualTo(Mode.FAST);
        assertThat(config.worlds).containsExactly("a", "b");
        assertThat(config.limits).containsEntry("vip", 20).containsEntry("mvp", 30);
        assertThat(config.rewards).hasSize(2);
        assertThat(config.rewards.get(0).item).isEqualTo("EMERALD");
        assertThat(config.rewards.get(0).amount).isEqualTo(4);
        assertThat(config.rewards.get(1).amount).isEqualTo(1);
        assertThat(config.database.host).isEqualTo("db.example");
        assertThat(config.database.port).isEqualTo(3306);
        assertThat(config.spawn.x).isEqualTo(10);
        assertThat(config.spawn.y).isEqualTo(70);
    }

    @Test
    void addsMissingKeysWithoutTouchingUserValuesOrComments() throws Exception {
        Files.writeString(dir.resolve("homes.yml"), """
                # My own note about homes
                max-homes: 9
                """);
        ref();
        String text = Files.readString(dir.resolve("homes.yml"));
        assertThat(text).contains("# My own note about homes\nmax-homes: 9").contains("teleport-delay: 5s");
    }

    @Test
    void doesNotRewriteCompleteFiles() throws Exception {
        ref();
        Path file = dir.resolve("homes.yml");
        String custom = Files.readString(file).replace("max-homes: 3", "max-homes:    4   # spacing kept");
        Files.writeString(file, custom);
        ConfigRef<TestConfig> ref = ref();
        assertThat(ref.get().maxHomes).isEqualTo(4);
        assertThat(Files.readString(file)).isEqualTo(custom);
    }

    @Test
    void reportsEveryInvalidValueWithPaths() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        Path file = dir.resolve("homes.yml");
        Files.writeString(file, """
                max-homes: 500
                teleport-delay: soon
                mode: turbo
                database:
                  port: 0
                rewards:
                  - amount: many
                message-prefix: "  "
                spawn: nope
                """);
        ReloadResult result = ref.reload();
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("max-homes: 500 is above the maximum 100"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("teleport-delay: Invalid duration 'soon'"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("mode: 'turbo' is not one of FAST, SAFE"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("database.port: 0 is below the minimum 1"));
        assertThat(result.errors())
                .anySatisfy(e -> assertThat(e).contains("rewards[0].amount: expected a whole number"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("message-prefix: must not be blank"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("spawn: expected x,y"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("keeping the previously loaded values"));
        assertThat(ref.get().maxHomes).isEqualTo(3);
    }

    @Test
    void malformedYamlKeepsPreviousSnapshot() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        TestConfig before = ref.get();
        Files.writeString(dir.resolve("homes.yml"), "max-homes: [unclosed\n");
        ReloadResult result = ref.reload();
        assertThat(result.success()).isFalse();
        assertThat(result.errors().getFirst()).contains("homes.yml is not valid YAML");
        assertThat(ref.get()).isSameAs(before);
    }

    @Test
    void reloadPublishesNewSnapshotAndNotifiesListeners() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        TestConfig before = ref.get();
        List<Integer> seen = new ArrayList<>();
        ref.onReload(config -> seen.add(config.maxHomes));
        Path file = dir.resolve("homes.yml");
        Files.writeString(file, Files.readString(file).replace("max-homes: 3", "max-homes: 8"));
        assertThat(ref.reload().success()).isTrue();
        assertThat(ref.get()).isNotSameAs(before);
        assertThat(before.maxHomes).as("old snapshot is never mutated").isEqualTo(3);
        assertThat(ref.get().maxHomes).isEqualTo(8);
        assertThat(seen).containsExactly(8);
    }

    @Test
    void unknownKeysAreReportedAndKept() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        Path file = dir.resolve("homes.yml");
        Files.writeString(file, Files.readString(file) + "legacy-option: true\ndatabase-extra:\n  a: 1\n");
        ReloadResult result = ref.reload();
        assertThat(result.success()).isTrue();
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("unknown key 'legacy-option'"));
        assertThat(Files.readString(file)).contains("legacy-option: true");
    }

    @Test
    void saveValidatesWritesAtomicallyAndPublishes() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        Path file = dir.resolve("homes.yml");
        Files.writeString(file, Files.readString(file) + "kept-key: 1\n");
        ref.reload();
        TestConfig changed = new TestConfig();
        changed.maxHomes = 12;
        assertThat(ref.save(changed).success()).isTrue();
        assertThat(ref.get().maxHomes).isEqualTo(12);
        assertThat(ref.get()).isNotSameAs(changed);
        assertThat(Files.readString(file))
                .contains("max-homes: 12")
                .contains("kept-key: 1")
                .contains("# Maximum number of homes.");

        TestConfig invalid = new TestConfig();
        invalid.maxHomes = 1000;
        assertThat(ref.save(invalid).success()).isFalse();
        assertThat(Files.readString(file)).contains("max-homes: 12");
        assertThat(ref.get().maxHomes).isEqualTo(12);
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("homes.yml");
        }
    }

    @Test
    void typeLevelCodecsWorkInsideCollections() throws Exception {
        ConfigRef<TestConfig> ref = ref();
        assertThat(Files.readString(dir.resolve("homes.yml"))).contains("- '#ff0000'");
        Path file = dir.resolve("homes.yml");
        Files.writeString(file, Files.readString(file).replace("- '#ff0000'", "- '#00ff00'\n- '#0000ff'"));
        assertThat(ref.reload().success()).isTrue();
        assertThat(ref.get().palette).containsExactly(new Color(0x00ff00), new Color(0x0000ff));
        Files.writeString(file, Files.readString(file).replace("'#0000ff'", "blue"));
        assertThat(ref.reload().errors())
                .anySatisfy(e -> assertThat(e).contains("palette[1]: expected #RRGGBB but got blue"));
    }

    @Test
    void resolvesConfigurationExpressions() {
        ConfigServiceImpl service = new ConfigServiceImpl(
                dir,
                List.of(new ConfigSpec<>(TestConfig.class, "homes.yml", List.of(), TestConfig::new)),
                Logger.getLogger("test"));
        service.loadInitial();
        assertThat(service.resolve("teleport-delay").value()).isEqualTo("5s");
        assertThat(service.resolve("homes.yml:database.host").value()).isEqualTo("localhost");
    }
}
