package com.nookfall.framework.testing;

import com.nookfall.framework.internal.ResourceIndex;
import com.nookfall.framework.internal.i18n.LocalizationImpl;
import com.nookfall.framework.message.Localization;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Translations loaded from a resource directory, for testing messages without a server. */
public final class TestLocalization {

    private TestLocalization() {}

    /** Loads {@code lang/*.yml} from a resource directory such as {@code src/main/resources}. */
    public static Localization fromResources(Path resources) {
        try {
            Path data = Files.createTempDirectory("nookfall-l10n-test");
            LocalizationImpl localization = new LocalizationImpl(
                    data, ResourceIndex.ofDirectory(resources), Logger.getLogger("TestLocalization"));
            localization.loadInitial();
            return localization;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Plain text of a component, convenient for assertions. */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
