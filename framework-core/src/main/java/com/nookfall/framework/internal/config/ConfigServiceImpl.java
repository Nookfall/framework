package com.nookfall.framework.internal.config;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.config.ConfigCodec;
import com.nookfall.framework.config.ConfigDirectory;
import com.nookfall.framework.config.ConfigException;
import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.config.ConfigService;
import com.nookfall.framework.spi.ConfigSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Configuration registry of one plugin. */
public final class ConfigServiceImpl implements ConfigService {

    private final Codecs codecs = new Codecs();
    private final ConfigBinder binder = new ConfigBinder(codecs);
    private final Map<Class<?>, ConfigRefImpl<?>> refs = new LinkedHashMap<>();
    private final List<ConfigDirectoryImpl<?>> directories = new ArrayList<>();
    private final Path dataFolder;
    private final Logger logger;

    public ConfigServiceImpl(Path dataFolder, List<ConfigSpec<?>> specs, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        for (ConfigSpec<?> spec : specs) {
            refs.put(spec.type(), new ConfigRefImpl<>(spec, dataFolder, binder, logger));
        }
    }

    /**
     * Loads every configuration for the first time.
     *
     * @throws ConfigException listing all problems if any file is invalid
     */
    public void loadInitial() {
        List<String> errors = new ArrayList<>();
        for (ConfigRefImpl<?> ref : refs.values()) {
            errors.addAll(ref.reload().errors());
        }
        if (!errors.isEmpty()) {
            throw new ConfigException("configuration", errors);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ConfigRef<T> ref(Class<T> type) {
        ConfigRefImpl<?> ref = refs.get(type);
        if (ref == null) {
            throw new IllegalArgumentException(type.getName() + " is not a @Config class of this plugin");
        }
        return (ConfigRef<T>) ref;
    }

    @Override
    public Collection<ConfigRef<?>> all() {
        return List.copyOf(refs.values());
    }

    @Override
    public ReloadResult reloadAll() {
        ReloadResult result = ReloadResult.ok();
        for (ConfigRefImpl<?> ref : refs.values()) {
            result = result.merge(ref.reload());
        }
        for (ConfigDirectoryImpl<?> directory : directories) {
            result = result.merge(directory.reload());
        }
        return result;
    }

    @Override
    public synchronized <T> ConfigDirectory<T> directory(
            Class<T> type, String folder, @Nullable Supplier<Map<String, String>> defaults) {
        ConfigDirectoryImpl<T> directory =
                new ConfigDirectoryImpl<>(type, folder, dataFolder, defaults, binder, logger);
        ReloadResult result = directory.reload();
        if (!result.success()) {
            throw new ConfigException(folder + "/", result.errors());
        }
        directories.add(directory);
        return directory;
    }

    @Override
    public <T> void registerCodec(Class<T> type, ConfigCodec<T> codec) {
        codecs.register(type, codec);
    }

    /**
     * Resolves {@code path} or {@code file.yml:path} against the loaded configurations.
     *
     * @throws IllegalArgumentException if the path does not exist or is ambiguous
     */
    public ResolvedValue resolve(String expression) {
        int colon = expression.indexOf(':');
        if (colon > 0) {
            String file = expression.substring(0, colon);
            String path = expression.substring(colon + 1);
            for (ConfigRefImpl<?> ref : refs.values()) {
                if (ref.fileName().equals(file)) {
                    Object value = ref.value(path)
                            .orElseThrow(() -> new IllegalArgumentException("'" + path + "' is not set in " + file));
                    return new ResolvedValue(ref, value);
                }
            }
            throw new IllegalArgumentException("no configuration file named " + file);
        }
        ConfigRefImpl<?> match = null;
        Object value = null;
        for (ConfigRefImpl<?> ref : refs.values()) {
            Optional<Object> candidate = ref.value(expression);
            if (candidate.isPresent()) {
                if (match != null) {
                    throw new IllegalArgumentException("'" + expression + "' exists in both " + match.fileName()
                            + " and " + ref.fileName() + "; use ${file.yml:" + expression + "}");
                }
                match = ref;
                value = candidate.get();
            }
        }
        if (match == null) {
            throw new IllegalArgumentException("'" + expression + "' is not set in any configuration file");
        }
        return new ResolvedValue(match, value);
    }

    /** A configuration value and the reference it came from. */
    public record ResolvedValue(ConfigRef<?> ref, Object value) {}
}
