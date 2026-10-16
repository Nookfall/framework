package com.nookfall.framework.util.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parsesCompactForms() {
        assertThat(Durations.parse("30s")).isEqualTo(Duration.ofSeconds(30));
        assertThat(Durations.parse("5m")).isEqualTo(Duration.ofMinutes(5));
        assertThat(Durations.parse("1h30m")).isEqualTo(Duration.ofMinutes(90));
        assertThat(Durations.parse("2d 3h")).isEqualTo(Duration.ofHours(51));
        assertThat(Durations.parse("250ms")).isEqualTo(Duration.ofMillis(250));
        assertThat(Durations.parse("1M")).isEqualTo(Duration.ofMinutes(1));
        assertThat(Durations.parse("0")).isZero();
    }

    @Test
    void rejectsAmbiguousOrInvalidInput() {
        for (String bad : new String[] {"", "5", "five minutes", "5x", "m5", "1h-2m", "99999999999999999999s"}) {
            assertThatThrownBy(() -> Durations.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(Durations.isValid("10s")).isTrue();
        assertThat(Durations.isValid("10")).isFalse();
    }

    @Test
    void formatsRoundTrip() {
        Duration duration = Duration.ofHours(26).plusMinutes(3).plusSeconds(4).plusMillis(5);
        assertThat(Durations.toCompact(duration)).isEqualTo("1d2h3m4s5ms");
        assertThat(Durations.parse(Durations.toCompact(duration))).isEqualTo(duration);
        assertThat(Durations.toCompact(Duration.ZERO)).isEqualTo("0s");
    }

    @Test
    void humanAndCountdown() {
        assertThat(Durations.toHuman(Duration.ofSeconds(3725))).isEqualTo("1h 2m 5s");
        assertThat(Durations.toHuman(Duration.ofSeconds(3725), 2)).isEqualTo("1h 2m");
        assertThat(Durations.toHuman(Duration.ofSeconds(3605)))
                .as("stops at the first gap")
                .isEqualTo("1h");
        assertThat(Durations.toHuman(Duration.ofMillis(300))).isEqualTo("300ms");
        assertThat(Durations.toCountdown(Duration.ofSeconds(307))).isEqualTo("05:07");
        assertThat(Durations.toCountdown(Duration.ofSeconds(3723))).isEqualTo("1:02:03");
        assertThat(Durations.toCountdown(Duration.ofMillis(1))).as("rounds up").isEqualTo("00:01");
        assertThat(Durations.toCountdown(Duration.ofSeconds(-5))).isEqualTo("00:00");
    }

    @Test
    void ticksRoundUp() {
        assertThat(Durations.toTicks(Duration.ofMillis(1), 1)).isEqualTo(1);
        assertThat(Durations.toTicks(Duration.ofMillis(50), 0)).isEqualTo(1);
        assertThat(Durations.toTicks(Duration.ofMillis(51), 0)).isEqualTo(2);
        assertThat(Durations.toTicks(Duration.ZERO, 1)).isEqualTo(1);
        assertThat(Durations.toTicks(Duration.ofSeconds(5), 1)).isEqualTo(100);
    }
}
