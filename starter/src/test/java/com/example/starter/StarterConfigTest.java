package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nookfall.framework.config.ConfigException;
import com.nookfall.framework.testing.ConfigTesting;
import org.junit.jupiter.api.Test;

class StarterConfigTest {

    @Test
    void defaultsAreDocumented() {
        String yaml = ConfigTesting.defaults(StarterConfig.class);
        assertThat(yaml).contains("# Greet players when they join.\ngreet-on-join: true").contains("interval: 30m");
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> ConfigTesting.load(StarterConfig.class, "visitors-shown: 500\n"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("visitors-shown: 500 is above the maximum 50");
    }

    @Test
    void readsCustomValues() {
        StarterConfig config = ConfigTesting.load(StarterConfig.class, "greet-on-join: false\n").value();
        assertThat(config.greetOnJoin()).isFalse();
        assertThat(config.visitorsShown()).isEqualTo(10);
    }
}
