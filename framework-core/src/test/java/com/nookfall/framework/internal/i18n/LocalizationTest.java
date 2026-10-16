package com.nookfall.framework.internal.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.internal.ResourceIndex;
import com.nookfall.framework.message.Arg;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalizationTest {

    @TempDir
    Path jar;

    @TempDir
    Path data;

    private LocalizationImpl l10n;

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(jar.resolve("lang"));
        Files.writeString(jar.resolve("lang/en_US.yml"), """
                prefix: "<gray>[Homes]</gray> "
                homes:
                  created: "<prefix><green>Created <home>"
                  count: "You have <count> homes"
                  wait: "Wait <time>"
                  only-english: "English only"
                  lore:
                    - "Line one <home>"
                    - "Line two"
                  silent: ""
                """);
        Files.writeString(jar.resolve("lang/pl_PL.yml"), """
                homes:
                  created: "Utworzono <home>"
                """);
        l10n = new LocalizationImpl(data, ResourceIndex.ofDirectory(jar), Logger.getLogger("test"));
        l10n.loadInitial();
    }

    @Test
    void copiesBundledFilesAndSettings() {
        assertThat(data.resolve("lang/en_US.yml")).exists();
        assertThat(data.resolve("lang/pl_PL.yml")).exists();
        assertThat(data.resolve("lang/settings.yml")).content().contains("default-locale: en_US");
    }

    @Test
    void rendersWithPrefixAndEscapesUserText() {
        Component message = l10n.message(Locale.US, "homes.created", Arg.text("home", "<red>Base</red>"));
        assertThat(plain(message)).isEqualTo("[Homes] Created <red>Base</red>");
        assertThat(message.children()).anySatisfy(c -> assertThat(c.color()).isEqualTo(NamedTextColor.GREEN));
    }

    @Test
    void trustedMarkupIsParsedOnlyWhenRequested() {
        Component message = l10n.message(Locale.US, "homes.created", Arg.trusted("home", "<bold>Base"));
        assertThat(plain(message)).isEqualTo("[Homes] Created Base");
    }

    @Test
    void fallsBackThroughLanguageDefaultAndEnglish() {
        assertThat(plain(l10n.message(Locale.of("pl", "PL"), "homes.created", Arg.text("home", "Dom"))))
                .isEqualTo("Utworzono Dom");
        assertThat(plain(l10n.message(Locale.of("pl"), "homes.created", Arg.text("home", "Dom"))))
                .as("language-only locale")
                .isEqualTo("Utworzono Dom");
        assertThat(plain(l10n.message(Locale.of("pl", "PL"), "homes.only-english")))
                .as("missing key in pl falls back to en_US")
                .isEqualTo("English only");
        assertThat(plain(l10n.message(Locale.GERMANY, "homes.only-english"))).isEqualTo("English only");
    }

    @Test
    void missingKeysRenderTheKey() {
        assertThat(plain(l10n.message(Locale.US, "homes.nope"))).isEqualTo("homes.nope");
        assertThat(l10n.has(Locale.US, "homes.nope")).isFalse();
    }

    @Test
    void formatsTypedValues() {
        assertThat(plain(l10n.message(Locale.US, "homes.count", Arg.number("count", 12345.5))))
                .isEqualTo("You have 12,345.5 homes");
        assertThat(plain(l10n.message(Locale.GERMANY, "homes.count", Arg.of("count", 12345))))
                .isEqualTo("You have 12.345 homes");
        assertThat(plain(l10n.message(Locale.US, "homes.wait", Arg.of("time", Duration.ofSeconds(3725)))))
                .isEqualTo("Wait 1h 2m 5s");
    }

    @Test
    void rendersLists() {
        assertThat(l10n.lines(Locale.US, "homes.lore", Arg.text("home", "Base")))
                .extracting(LocalizationTest::plain)
                .containsExactly("Line one Base", "Line two");
    }

    @Test
    void frameworkMessagesAreAvailableAndOverridable() throws Exception {
        assertThat(plain(l10n.message(Locale.US, "nookfall.command.player-only")))
                .isEqualTo("Only players can use this command.");
        assertThat(plain(l10n.message(Locale.of("pl", "PL"), "nookfall.command.player-only")))
                .isEqualTo("Tej komendy mogą używać tylko gracze.");
        Files.writeString(
                data.resolve("lang/en_US.yml"),
                Files.readString(data.resolve("lang/en_US.yml"))
                        + "nookfall:\n  command:\n    player-only: Players only!\n");
        assertThat(l10n.reload().success()).isTrue();
        assertThat(plain(l10n.message(Locale.US, "nookfall.command.player-only")))
                .isEqualTo("Players only!");
    }

    @Test
    void reloadIsAtomic() throws Exception {
        Files.writeString(data.resolve("lang/pl_PL.yml"), "homes: [broken\n");
        ReloadResult result = l10n.reload();
        assertThat(result.success()).isFalse();
        assertThat(plain(l10n.message(Locale.of("pl", "PL"), "homes.created", Arg.text("home", "Dom"))))
                .isEqualTo("Utworzono Dom");
        Files.writeString(data.resolve("lang/pl_PL.yml"), "homes:\n  created: \"Nowy <home>\"\n");
        assertThat(l10n.reload().success()).isTrue();
        assertThat(plain(l10n.message(Locale.of("pl", "PL"), "homes.created", Arg.text("home", "Dom"))))
                .isEqualTo("Nowy Dom");
    }

    @Test
    void addsKeysIntroducedByUpdatesToUserFiles() throws Exception {
        Files.writeString(data.resolve("lang/en_US.yml"), "# custom\nhomes:\n  created: \"Made <home>\"\n");
        assertThat(l10n.reload().success()).isTrue();
        String text = Files.readString(data.resolve("lang/en_US.yml"));
        assertThat(text).contains("# custom").contains("created: Made <home>").contains("only-english: English only");
    }

    @Test
    void communityLocaleFilesWithMissingKeysFallBack() throws Exception {
        Files.writeString(data.resolve("lang/fr_FR.yml"), "homes:\n  count: \"Vous avez <count> maisons\"\n");
        assertThat(l10n.reload().success()).isTrue();
        assertThat(l10n.availableLocales()).contains(Locale.FRANCE);
        assertThat(plain(l10n.message(Locale.FRANCE, "homes.count", Arg.number("count", 2))))
                .isEqualTo("Vous avez 2 maisons");
        assertThat(plain(l10n.message(Locale.FRANCE, "homes.only-english"))).isEqualTo("English only");
    }

    @Test
    void defaultLocaleComesFromSettings() throws Exception {
        Files.writeString(data.resolve("lang/settings.yml"), "default-locale: pl_PL\nuse-client-locale: false\n");
        assertThat(l10n.reload().success()).isTrue();
        assertThat(l10n.defaultLocale()).isEqualTo(Locale.of("pl", "PL"));
        assertThat(plain(l10n.message(
                        (net.kyori.adventure.audience.Audience) null, "homes.created", Arg.text("home", "X"))))
                .isEqualTo("Utworzono X");
    }

    @Test
    void emptyMessagesAreNotSent() {
        StringBuilder received = new StringBuilder();
        net.kyori.adventure.audience.Audience audience = new net.kyori.adventure.audience.Audience() {
            @Override
            public void sendMessage(Component message) {
                received.append(plain(message));
            }
        };
        l10n.send(audience, "homes.silent");
        assertThat(received).isEmpty();
        l10n.send(audience, "homes.only-english");
        assertThat(received.toString()).isEqualTo("English only");
    }
}
