package com.nookfall.framework.internal;

import com.nookfall.framework.integration.Integrations;
import com.nookfall.framework.spi.IntegrationSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates {@code @Integration} classes on first use, once their plugins are enabled. A class that cannot be linked or
 * constructed (the other plugin is a different version, say) is reported once and treated as unavailable, so a broken
 * optional plugin never takes this plugin down.
 */
public final class IntegrationsImpl implements Integrations {

    private final List<IntegrationSpec> specs;
    private final Predicate<String> enabled;
    private final Function<Class<?>, Object> factory;
    private final Logger logger;
    private final Map<IntegrationSpec, Object> created = new HashMap<>();
    private final Map<IntegrationSpec, Boolean> failed = new HashMap<>();

    public IntegrationsImpl(
            List<IntegrationSpec> specs, Predicate<String> enabled, Function<Class<?>, Object> factory, Logger logger) {
        this.specs = List.copyOf(specs);
        this.enabled = enabled;
        this.factory = factory;
        this.logger = logger;
    }

    @Override
    public synchronized <T> Optional<T> find(Class<T> type) {
        for (IntegrationSpec spec : specs) {
            if (spec.provides() != type || failed.containsKey(spec)) {
                continue;
            }
            Object existing = created.get(spec);
            if (existing != null) {
                return Optional.of(type.cast(existing));
            }
            List<String> missing = new ArrayList<>();
            for (String plugin : spec.requiredPlugins()) {
                if (!enabled.test(plugin)) {
                    missing.add(plugin);
                }
            }
            if (!missing.isEmpty()) {
                continue; // may be enabled later; not remembered
            }
            try {
                Object instance = factory.apply(spec.type());
                created.put(spec, instance);
                return Optional.of(type.cast(instance));
            } catch (RuntimeException | LinkageError e) {
                failed.put(spec, Boolean.TRUE);
                logger.log(
                        Level.WARNING,
                        "The integration " + spec.type().getSimpleName() + " with "
                                + String.join(", ", spec.requiredPlugins()) + " could not be started and is disabled",
                        e);
            }
        }
        return Optional.empty();
    }
}
