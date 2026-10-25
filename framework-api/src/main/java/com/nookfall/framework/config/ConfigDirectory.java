package com.nookfall.framework.config;

import com.nookfall.framework.ReloadResult;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A folder of YAML files that all have the same shape, one value per file: a shop category per file, a kit per file. Create
 * it with {@link ConfigService#directory}. The file name without {@code .yml} is the key.
 *
 * <p>Like {@link ConfigRef}, the current value is an immutable snapshot, and a reload replaces it atomically: if any file
 * is invalid the previous snapshot stays and every problem is reported. Missing keys are added to the files, unknown keys
 * are kept and warned about.
 *
 * @param <T> the class each file is read into
 */
public interface ConfigDirectory<T> {

    /** The current files by name (without {@code .yml}), in file name order. */
    Map<String, T> get();

    /** Absolute path of the folder. */
    Path directory();

    /** Re-reads every file in the folder. */
    ReloadResult reload();

    /** Runs the listener after each successful reload. */
    void onReload(Consumer<Map<String, T>> listener);
}
