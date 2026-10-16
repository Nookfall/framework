package com.nookfall.framework.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nookfall.framework.config.ConfigException;
import com.nookfall.framework.message.Localization;
import com.nookfall.framework.testing.fixture.FixtureCommand;
import com.nookfall.framework.testing.fixture.FixtureConfig;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TestingHelpersTest {

    private ServerMock server;
    private Localization localization;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        localization = TestLocalization.fromResources(Path.of("src/test/resources"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void configDefaultsAndValidation() {
        assertThat(ConfigTesting.defaults(FixtureConfig.class)).contains("# Greeting shown by /fixture.\ngreeting: hi");
        ConfigTesting.Loaded<FixtureConfig> loaded =
                ConfigTesting.load(FixtureConfig.class, "greeting: hey\nextra: 1\n");
        assertThat(loaded.value().greeting()).isEqualTo("hey");
        assertThat(loaded.value().times()).isEqualTo(2);
        assertThat(loaded.warnings()).anySatisfy(w -> assertThat(w).contains("unknown key 'extra'"));
        assertThatThrownBy(() -> ConfigTesting.load(FixtureConfig.class, "times: 50\n"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("times: 50 is above the maximum 10");
    }

    @Test
    void translations() {
        assertThat(TestLocalization.plain(localization.message(
                        Locale.US, "fixture.denied", com.nookfall.framework.message.Arg.text("who", "<b>Bob"))))
                .isEqualTo("No, <b>Bob.");
    }

    @Test
    void commandsRunThroughTheGeneratedTree() throws Exception {
        FixtureCommand command = new FixtureCommand();
        CommandTester tester = CommandTester.of(command, localization);
        PlayerMock player = server.addPlayer("Alex");
        player.setOp(false);

        assertThat(tester.execute(server.getConsoleSender(), "/fixture add 2")).isEqualTo(1);
        assertThat(tester.execute(player, "fixture add 2 5")).isEqualTo(1);
        assertThat(command.calls).containsExactly("add 3", "add 7");

        assertThatThrownBy(() -> tester.execute(player, "fixture add many")).isInstanceOf(CommandSyntaxException.class);

        tester.execute(player, "fixture paint green");
        assertThat(command.calls).contains("paint GREEN");
        assertThat(tester.execute(server.getConsoleSender(), "fixture paint red"))
                .isZero();
        assertThat(TestLocalization.plain(server.getConsoleSender().nextComponentMessage()))
                .isEqualTo("Only players can use this command.");

        tester.execute(player, "fixture paint purple");
        assertThat(TestLocalization.plain(player.nextComponentMessage()))
                .contains("Invalid color purple. Options: red, green");

        assertThat(tester.execute(player, "fixture deny")).isZero();
        assertThat(TestLocalization.plain(player.nextComponentMessage())).isEqualTo("No, Alex.");

        assertThat(tester.suggest(player, "fixture paint ")).containsExactly("green", "red");
        assertThat(tester.suggest(player, "fixture "))
                .contains("add", "paint", "help")
                .doesNotContain("admin");
        assertThatThrownBy(() -> tester.execute(player, "fixture admin")).isInstanceOf(CommandSyntaxException.class);
        player.addAttachment(MockBukkit.createMockPlugin(), "fixture.admin", true);
        tester.execute(player, "fixture admin");
        assertThat(command.calls).contains("admin");
    }

    @Test
    void helpIsGenerated() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandTester.of(new FixtureCommand(), localization).execute(player, "fixture help");
        assertThat(TestLocalization.plain(player.nextComponentMessage())).isEqualTo("Commands for /fixture:");
        assertThat(TestLocalization.plain(player.nextComponentMessage())).isEqualTo("/fixture add <a> [b]");
    }
}
