package com.nookfall.framework.util.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CooldownsTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Cooldowns cooldowns = new Cooldowns(now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    void countsDownAndRoundsUpToSeconds() {
        cooldowns.start(player, Duration.ofSeconds(5));
        assertThat(cooldowns.remaining(player)).isEqualTo(Duration.ofSeconds(5));
        now.addAndGet(4_001);
        assertThat(cooldowns.remaining(player)).isEqualTo(Duration.ofSeconds(1));
        now.addAndGet(999);
        assertThat(cooldowns.remaining(player)).isZero();
    }

    @Test
    void zeroCooldownIsNotRecorded() {
        cooldowns.start(player, Duration.ZERO);
        assertThat(cooldowns.remaining(player)).isZero();
        assertThat(cooldowns.size()).isZero();
    }

    @Test
    void expiredEntriesArePruned() {
        for (int i = 0; i < 100; i++) {
            cooldowns.start(UUID.randomUUID(), Duration.ofSeconds(1));
        }
        now.addAndGet(2_000);
        cooldowns.start(player, Duration.ofSeconds(1));
        assertThat(cooldowns.size()).isEqualTo(1);
    }

    @Test
    void resetEndsACooldown() {
        cooldowns.start(player, Duration.ofMinutes(1));
        assertThat(cooldowns.active(player)).isTrue();
        cooldowns.reset(player);
        assertThat(cooldowns.active(player)).isFalse();
    }
}
