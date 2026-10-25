package com.nookfall.framework.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.nookfall.framework.spi.IntegrationSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class IntegrationsImplTest {

    interface Hook {
        String name();
    }

    static final class Real implements Hook {
        @Override
        public String name() {
            return "real";
        }
    }

    private final Set<String> enabled = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();
    private int created;
    private boolean broken;

    private IntegrationsImpl integrations(IntegrationSpec... specs) {
        Logger logger = Logger.getLogger("integrations-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return new IntegrationsImpl(
                List.of(specs),
                enabled::contains,
                type -> {
                    created++;
                    if (broken) {
                        throw new NoClassDefFoundError("other/Plugin");
                    }
                    return new Real();
                },
                logger);
    }

    private static IntegrationSpec spec(String... plugins) {
        return new IntegrationSpec(Real.class, Hook.class, List.of(plugins));
    }

    @Test
    void notAvailableUntilItsPluginsAreEnabled() {
        IntegrationsImpl integrations = integrations(spec("A", "B"));
        assertThat(integrations.find(Hook.class)).isEmpty();
        enabled.add("A");
        assertThat(integrations.find(Hook.class)).isEmpty();
        assertThat(created)
                .as("nothing is constructed while a plugin is missing")
                .isZero();
        enabled.add("B");
        assertThat(integrations.find(Hook.class))
                .hasValueSatisfying(h -> assertThat(h.name()).isEqualTo("real"));
    }

    @Test
    void createdOnceAndShared() {
        enabled.add("A");
        IntegrationsImpl integrations = integrations(spec("A"));
        Hook first = integrations.find(Hook.class).orElseThrow();
        Hook second = integrations.find(Hook.class).orElseThrow();
        assertThat(second).isSameAs(first);
        assertThat(created).isEqualTo(1);
    }

    @Test
    void aBrokenIntegrationIsReportedOnceAndTreatedAsMissing() {
        enabled.add("A");
        broken = true;
        IntegrationsImpl integrations = integrations(spec("A"));
        assertThat(integrations.find(Hook.class)).isEmpty();
        assertThat(integrations.find(Hook.class)).isEmpty();
        assertThat(created).isEqualTo(1);
        assertThat(warnings).hasSize(1).first().asString().contains("Real").contains("A");
    }

    @Test
    void fallbackAndAvailability() {
        IntegrationsImpl integrations = integrations(spec("A"));
        Hook none = () -> "none";
        assertThat(integrations.get(Hook.class, none)).isSameAs(none);
        assertThat(integrations.available(Hook.class)).isFalse();
        enabled.add("A");
        assertThat(integrations.available(Hook.class)).isTrue();
        assertThat(integrations.get(Hook.class, none).name()).isEqualTo("real");
    }

    @Test
    void unknownTypesAreAbsent() {
        assertThat(integrations().find(Hook.class)).isEmpty();
    }
}
