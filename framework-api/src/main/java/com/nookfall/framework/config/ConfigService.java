package com.nookfall.framework.config;

import com.nookfall.framework.ReloadResult;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Manages the configuration classes declared with {@link Config} for one plugin. */
public interface ConfigService {

    /** Returns the reference for a declared configuration class. */
    <T> ConfigRef<T> ref(Class<T> type);

    Collection<ConfigRef<?>> all();

    /** Reloads every configuration. Each file is replaced only if it loads and validates. */
    ReloadResult reloadAll();

    /**
     * A folder of files that are each read into {@code type}, for data with one file per item (see
     * {@link ConfigDirectory}). The class needs a no-argument constructor and uses the same annotations as a
     * {@code @Config} class. It is read when the plugin starts and again on every reload.
     *
     * @param folder relative to the plugin's data folder
     * @param defaults the files to create (name without {@code .yml} to YAML text) when the folder does not exist or has no
     *     {@code .yml} files yet; called at most once. May be null.
     * @throws ConfigException if a file is invalid on the first load
     */
    <T> ConfigDirectory<T> directory(Class<T> type, String folder, @Nullable Supplier<Map<String, String>> defaults);

    /** Registers a codec used for every field of the given type. Call before configurations are reloaded. */
    <T> void registerCodec(Class<T> type, ConfigCodec<T> codec);
}
