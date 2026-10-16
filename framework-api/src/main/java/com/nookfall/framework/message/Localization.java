package com.nookfall.framework.message;

import com.nookfall.framework.ReloadResult;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * Translations for one plugin, loaded from {@code lang/<locale>.yml} in the data folder with the bundled files as
 * defaults. Lookups fall back from the requested locale to its language, then the default locale, then the bundled
 * {@code en_US} file.
 */
public interface Localization {

    Locale defaultLocale();

    /** The locale used for an audience: an explicit override, the client locale (if enabled) or the default. */
    Locale locale(@Nullable Audience audience);

    /** Overrides the locale for a player. Pass {@code null} to clear the override. */
    void setLocale(UUID player, @Nullable Locale locale);

    Component message(Locale locale, String key, Arg... args);

    /** Renders a message in the audience's locale. */
    Component message(@Nullable Audience audience, String key, Arg... args);

    /** Renders a list-valued message (for example item lore); a single string becomes one line. */
    List<Component> lines(Locale locale, String key, Arg... args);

    /** Sends a message in the recipient's locale. Empty messages are not sent. */
    void send(Audience recipient, String key, Arg... args);

    /** Whether the key exists in the locale's fallback chain. */
    boolean has(Locale locale, String key);

    Set<Locale> availableLocales();

    /** Reloads translation files. On failure the previous translations stay active. */
    ReloadResult reload();
}
