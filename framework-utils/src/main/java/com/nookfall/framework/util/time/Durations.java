package com.nookfall.framework.util.time;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats compact durations such as {@code 1h30m}, {@code 5m}, {@code 30s}, {@code 250ms} or {@code 2d}.
 * Units: {@code d}, {@code h}, {@code m}, {@code s}, {@code ms}. A plain number is not accepted, so a missing unit is
 * reported instead of guessed.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)(ms|d|h|m|s)");

    private Durations() {}

    /**
     * Parses a duration.
     *
     * @throws IllegalArgumentException if the text is not a valid duration
     */
    public static Duration parse(String text) {
        String input = text.strip().toLowerCase(Locale.ROOT).replace(" ", "");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("Empty duration");
        }
        if (input.equals("0")) {
            return Duration.ZERO;
        }
        Matcher matcher = PART.matcher(input);
        Duration total = Duration.ZERO;
        int position = 0;
        while (matcher.find()) {
            if (matcher.start() != position) {
                break;
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Duration value too large: " + text);
            }
            total = total.plus(
                    switch (matcher.group(2)) {
                        case "d" -> Duration.ofDays(amount);
                        case "h" -> Duration.ofHours(amount);
                        case "m" -> Duration.ofMinutes(amount);
                        case "s" -> Duration.ofSeconds(amount);
                        default -> Duration.ofMillis(amount);
                    });
            position = matcher.end();
        }
        if (position != input.length()) {
            throw new IllegalArgumentException(
                    "Invalid duration '" + text + "'. Use forms like 30s, 5m, 1h30m or 250ms");
        }
        return total;
    }

    /** Returns true if {@link #parse(String)} would accept the text. */
    public static boolean isValid(String text) {
        try {
            parse(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Formats in the compact form accepted by {@link #parse(String)}, for example {@code 1h30m}. */
    public static String toCompact(Duration duration) {
        if (duration.isZero()) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        long days = duration.toDays();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();
        int millis = duration.toMillisPart();
        if (days > 0) out.append(days).append('d');
        if (hours > 0) out.append(hours).append('h');
        if (minutes > 0) out.append(minutes).append('m');
        if (seconds > 0) out.append(seconds).append('s');
        if (millis > 0) out.append(millis).append("ms");
        return out.toString();
    }

    /**
     * Formats for people, for example {@code 1h 5m 3s}. Shows at most {@code maxUnits} of the largest non-zero units;
     * durations under a second show milliseconds.
     */
    public static String toHuman(Duration duration, int maxUnits) {
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }
        if (duration.compareTo(Duration.ofSeconds(1)) < 0) {
            return duration.toMillis() + "ms";
        }
        long[] values = {duration.toDays(), duration.toHoursPart(), duration.toMinutesPart(), duration.toSecondsPart()};
        String[] units = {"d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < values.length && shown < maxUnits; i++) {
            if (values[i] > 0) {
                if (!out.isEmpty()) out.append(' ');
                out.append(values[i]).append(units[i]);
                shown++;
            } else if (shown > 0) {
                break;
            }
        }
        return out.toString();
    }

    public static String toHuman(Duration duration) {
        return toHuman(duration, 3);
    }

    /** Formats as a clock-style countdown: {@code 05:07}, or {@code 1:02:03} when at least an hour remains. */
    public static String toCountdown(Duration remaining) {
        long total = Math.max(0, (remaining.toMillis() + 999) / 1000);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    /** Converts to server ticks (50 ms), rounding up; at least {@code minimum}. */
    public static long toTicks(Duration duration, long minimum) {
        long ticks = (duration.toMillis() + 49) / 50;
        return Math.max(minimum, ticks);
    }
}
