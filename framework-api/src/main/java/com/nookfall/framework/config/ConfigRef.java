package com.nookfall.framework.config;

import com.nookfall.framework.ReloadResult;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handle to the current successfully loaded snapshot of a configuration class.
 *
 * <p>Snapshots are never modified after publication. Reloading builds and validates a new instance and swaps it in
 * atomically; on failure the previous snapshot stays active.
 *
 * @param <T> configuration class
 */
public interface ConfigRef<T> {

    /** The current snapshot. Do not cache it in long-lived fields if the configuration can be reloaded. */
    T get();

    Class<T> type();

    /** Absolute path of the YAML file. */
    Path file();

    /** Re-reads the file. */
    ReloadResult reload();

    /** Encodes and atomically writes the given value, then publishes it as the current snapshot. */
    ReloadResult save(T value);

    /** Runs the listener after each successful reload or save. */
    void onReload(Consumer<T> listener);

    /** Reads a value by its dotted YAML path from the current snapshot, for example {@code "cleanup.interval"}. */
    Optional<Object> value(String path);
}
