package com.nookfall.framework.internal.i18n;

import com.nookfall.framework.message.Arg;
import com.nookfall.framework.util.time.Durations;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Renders trusted translation templates with MiniMessage. Placeholder values are inserted without being parsed
 * unless they are explicitly {@link Arg.Kind#TRUSTED_MINIMESSAGE}.
 */
public final class MessageRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final TagResolver EMPTY_TAG =
            TagResolver.resolver("empty", Tag.selfClosingInserting(Component.empty()));

    private MessageRenderer() {}

    public static Component render(String template, Locale locale, Arg[] args, TagResolver extra) {
        TagResolver.Builder resolvers = TagResolver.builder();
        for (Arg arg : args) {
            resolvers.resolver(resolver(arg, locale));
        }
        resolvers.resolver(extra);
        resolvers.resolver(EMPTY_TAG);
        return MINI_MESSAGE.deserialize(template, resolvers.build());
    }

    public static TagResolver resolver(Arg arg, Locale locale) {
        return switch (arg.kind()) {
            case TEXT -> Placeholder.unparsed(arg.name(), (String) arg.value());
            case COMPONENT -> Placeholder.component(arg.name(), (Component) arg.value());
            case NUMBER -> Placeholder.unparsed(arg.name(), formatNumber((Number) arg.value(), locale));
            case DURATION -> Placeholder.unparsed(arg.name(), Durations.toHuman((Duration) arg.value()));
            case TRUSTED_MINIMESSAGE -> Placeholder.parsed(arg.name(), (String) arg.value());
        };
    }

    public static String formatNumber(Number number, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(2);
        return format.format(number);
    }

    /** Escapes text so it can be embedded in a MiniMessage template literally. */
    public static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text);
    }
}
