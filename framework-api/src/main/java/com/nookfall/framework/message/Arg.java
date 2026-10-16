package com.nookfall.framework.message;

import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.text.ComponentLike;

/**
 * A named placeholder value for a translated message, referenced in translations as {@code <name>}.
 *
 * <p>Text values are always inserted literally: MiniMessage tags inside them are not interpreted. Use
 * {@link #trusted(String, String)} only for markup that comes from a trusted source such as your own configuration.
 */
public final class Arg {

    /** How the value is inserted into the message. */
    public enum Kind {
        TEXT,
        COMPONENT,
        NUMBER,
        DURATION,
        TRUSTED_MINIMESSAGE
    }

    private final String name;
    private final Kind kind;
    private final Object value;

    private Arg(String name, Kind kind, Object value) {
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Placeholder name must match [a-z0-9_-]+: " + name);
        }
        this.name = name;
        this.kind = kind;
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Plain text, inserted without parsing. */
    public static Arg text(String name, Object value) {
        return new Arg(name, Kind.TEXT, String.valueOf(value));
    }

    public static Arg component(String name, ComponentLike value) {
        return new Arg(name, Kind.COMPONENT, value.asComponent());
    }

    /** A number formatted for the recipient's locale. */
    public static Arg number(String name, Number value) {
        return new Arg(name, Kind.NUMBER, value);
    }

    /** A duration formatted in a short human-readable form such as {@code 1h 5m}. */
    public static Arg duration(String name, Duration value) {
        return new Arg(name, Kind.DURATION, value);
    }

    /** MiniMessage markup that is parsed. Never pass player-provided text here. */
    public static Arg trusted(String name, String miniMessage) {
        return new Arg(name, Kind.TRUSTED_MINIMESSAGE, miniMessage);
    }

    /** Chooses the kind from the value type; anything that is not a component, number or duration becomes text. */
    public static Arg of(String name, Object value) {
        if (value instanceof ComponentLike c) {
            return component(name, c);
        }
        if (value instanceof Number n) {
            return number(name, n);
        }
        if (value instanceof Duration d) {
            return duration(name, d);
        }
        return text(name, value);
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public Object value() {
        return value;
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
