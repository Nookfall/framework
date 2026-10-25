package com.nookfall.framework.util.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void smallNumbersAreLeftAlone() {
        assertThat(Numbers.compact(0)).isEqualTo("0");
        assertThat(Numbers.compact(999)).isEqualTo("999");
        assertThat(Numbers.compact(9_999)).isEqualTo("9999");
    }

    @Test
    void largeNumbersAreAbbreviated() {
        assertThat(Numbers.compact(10_000)).isEqualTo("10k");
        assertThat(Numbers.compact(12_345)).isEqualTo("12.3k");
        assertThat(Numbers.compact(200_000)).isEqualTo("200k");
        assertThat(Numbers.compact(1_000_000)).isEqualTo("1M");
        assertThat(Numbers.compact(1_500_000)).isEqualTo("1.5M");
        assertThat(Numbers.compact(2_000_000_000L)).isEqualTo("2B");
        assertThat(Numbers.compact(3_400_000_000_000L)).isEqualTo("3.4T");
        assertThat(Numbers.compact(Long.MAX_VALUE)).endsWith("Q");
    }

    @Test
    void roundingCarriesIntoTheNextUnit() {
        assertThat(Numbers.compact(999_950)).isEqualTo("1M");
        assertThat(Numbers.compact(999_940)).isEqualTo("999.9k");
    }

    @Test
    void negativeNumbersKeepTheirSign() {
        assertThat(Numbers.compact(-250_000)).isEqualTo("-250k");
    }

    @Test
    void theThresholdCanBeChosen() {
        assertThat(Numbers.compact(new BigDecimal("1500"), 1000)).isEqualTo("1.5k");
        assertThat(Numbers.compact(new BigDecimal("1500"), 100_000)).isEqualTo("1500");
        assertThat(Numbers.compact(new BigDecimal("12.50"), 10_000)).isEqualTo("12.5");
    }
}
