package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.nookfall.framework.message.Arg;
import com.nookfall.framework.message.Localization;
import com.nookfall.framework.testing.TestLocalization;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private final Localization localization = TestLocalization.fromResources(Path.of("src/main/resources"));

    @Test
    void welcomeShowsVisitCount() {
        assertThat(TestLocalization.plain(localization.message(Locale.US, "starter.welcome", Arg.number("visits", 3))))
                .isEqualTo("Starter » Welcome! This is visit number 3.");
    }

    @Test
    void playerNamesAreNotParsedAsMarkup() {
        assertThat(TestLocalization.plain(localization.message(Locale.US, "starter.visitors", Arg.text("names", "<red>Eve"))))
                .endsWith("Recent visitors: <red>Eve");
    }
}
