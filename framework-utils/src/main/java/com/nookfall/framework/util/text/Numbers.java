package com.nookfall.framework.util.text;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Number formatting for places where space is short: menus, tooltips, titles. */
public final class Numbers {

    /** From this value on, {@link #compact(long)} abbreviates. */
    public static final long COMPACT_FROM = 10_000;

    private static final String[] SUFFIXES = {"", "k", "M", "B", "T", "Q"};

    private Numbers() {}

    /**
     * Abbreviates large numbers: {@code 9999} stays {@code 9999}, then {@code 10k}, {@code 12.3k}, {@code 200k},
     * {@code 1M}, {@code 1.5M}, {@code 2B}, {@code 3T}. One decimal at most, never a trailing {@code .0}.
     */
    public static String compact(long value) {
        return compact(BigDecimal.valueOf(value), COMPACT_FROM);
    }

    /** Like {@link #compact(long)}, abbreviating from {@code threshold} on. */
    public static String compact(BigDecimal value, long threshold) {
        BigDecimal abs = value.abs();
        if (abs.compareTo(BigDecimal.valueOf(Math.max(threshold, 1000))) < 0) {
            return value.stripTrailingZeros().toPlainString();
        }
        int unit = 0;
        BigDecimal scaled = abs;
        BigDecimal thousand = BigDecimal.valueOf(1000);
        while (scaled.compareTo(thousand) >= 0 && unit < SUFFIXES.length - 1) {
            scaled = scaled.divide(thousand);
            unit++;
        }
        BigDecimal rounded = scaled.setScale(1, RoundingMode.HALF_UP);
        if (rounded.compareTo(thousand) >= 0 && unit < SUFFIXES.length - 1) {
            rounded = BigDecimal.ONE.setScale(1);
            unit++;
        }
        String number = rounded.stripTrailingZeros().toPlainString();
        return (value.signum() < 0 ? "-" : "") + number + SUFFIXES[unit];
    }
}
