package com.nookfall.framework.testing;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.config.ConfigException;
import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.internal.config.ConfigServiceImpl;
import com.nookfall.framework.spi.ConfigSpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Loads {@code @Config} classes exactly as the runtime does, without a server. */
public final class ConfigTesting {

    /** A loaded configuration and the warnings it produced (for example unknown keys). */
    public record Loaded<T>(T value, List<String> warnings, String writtenYaml) {}

    private ConfigTesting() {}

    /** The YAML file the runtime would generate for a fresh installation. */
    public static String defaults(Class<?> type) {
        return load(type, null).writtenYaml();
    }

    /**
     * Loads YAML into a configuration object.
     *
     * @throws ConfigException listing every problem if the YAML is invalid
     */
    public static <T> Loaded<T> load(Class<T> type, String yaml) {
        ConfigSpec<T> spec = spec(type);
        Path directory;
        try {
            directory = Files.createTempDirectory("nookfall-config-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Path file = directory.resolve(spec.file());
            if (yaml != null) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, yaml);
            }
            ConfigServiceImpl service =
                    new ConfigServiceImpl(directory, List.of(spec), Logger.getLogger("ConfigTesting"));
            ConfigRef<T> ref = service.ref(type);
            ReloadResult result = ref.reload();
            if (!result.success()) {
                throw new ConfigException(spec.file(), result.errors());
            }
            return new Loaded<>(ref.get(), result.warnings(), Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            delete(directory);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigSpec<T> spec(Class<T> type) {
        for (ConfigSpec<?> spec : Generated.<ConfigSpec<?>>specs(type, "configs")) {
            if (spec.type() == type) {
                return (ConfigSpec<T>) spec;
            }
        }
        throw new IllegalArgumentException(type.getName() + " is not annotated with @Config");
    }

    private static void delete(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // temporary directory
        }
    }
}
