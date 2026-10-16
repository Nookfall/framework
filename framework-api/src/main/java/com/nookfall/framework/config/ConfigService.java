package com.nookfall.framework.config;

import com.nookfall.framework.ReloadResult;
import java.util.Collection;

/** Manages the configuration classes declared with {@link Config} for one plugin. */
public interface ConfigService {

    /** Returns the reference for a declared configuration class. */
    <T> ConfigRef<T> ref(Class<T> type);

    Collection<ConfigRef<?>> all();

    /** Reloads every configuration. Each file is replaced only if it loads and validates. */
    ReloadResult reloadAll();

    /** Registers a codec used for every field of the given type. Call before configurations are reloaded. */
    <T> void registerCodec(Class<T> type, ConfigCodec<T> codec);
}
