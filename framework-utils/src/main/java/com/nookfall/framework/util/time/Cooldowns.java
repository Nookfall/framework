package com.nookfall.framework.util.time;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory cooldowns keyed by UUID, for example per player. Thread-safe. Entries survive reconnects (so logging out
 * does not skip a cooldown) but not restarts. Expired entries are removed when they are looked at or a new cooldown
 * starts, so the map stays small.
 */
public final class Cooldowns {

    private final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public Cooldowns() {
        this(System::currentTimeMillis);
    }

    /** @param clock current time in milliseconds, replaceable in tests */
    public Cooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    public void start(UUID key, Duration cooldown) {
        long now = clock.getAsLong();
        until.values().removeIf(end -> end <= now);
        if (!cooldown.isZero() && !cooldown.isNegative()) {
            until.put(key, now + cooldown.toMillis());
        }
    }

    /** Time left, rounded up to whole seconds, or {@link Duration#ZERO} when the cooldown is over. */
    public Duration remaining(UUID key) {
        Long end = until.get(key);
        if (end == null) {
            return Duration.ZERO;
        }
        long left = end - clock.getAsLong();
        if (left <= 0) {
            until.remove(key, end);
            return Duration.ZERO;
        }
        // Round up to whole seconds so messages never say "0s".
        return Duration.ofSeconds((left + 999) / 1000);
    }

    public boolean active(UUID key) {
        return !remaining(key).isZero();
    }

    public void reset(UUID key) {
        until.remove(key);
    }

    public void clear() {
        until.clear();
    }

    /** Number of tracked entries, including expired ones not yet pruned. */
    public int size() {
        return until.size();
    }
}
