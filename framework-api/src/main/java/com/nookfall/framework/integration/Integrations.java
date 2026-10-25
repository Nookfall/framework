package com.nookfall.framework.integration;

import java.util.Optional;

/**
 * The optional integrations of one plugin, declared with {@link Integration}. Inject it into any bean.
 *
 * <p>Integrations are looked up by the type they are offered as, so callers never mention the other plugin.
 */
public interface Integrations {

    /**
     * The integration offered as {@code type}, if its plugins are enabled and it could be created. Created on first use
     * and shared afterwards.
     */
    <T> Optional<T> find(Class<T> type);

    /** The integration offered as {@code type}, or {@code fallback} when it is not available. */
    default <T> T get(Class<T> type, T fallback) {
        return find(type).orElse(fallback);
    }

    /** Whether an integration offered as {@code type} is available. */
    default boolean available(Class<?> type) {
        return find(type).isPresent();
    }
}
