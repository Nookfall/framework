package com.nookfall.framework.internal.config;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.internal.YamlFiles;
import com.nookfall.framework.spi.ConfigSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Holds the current snapshot of one configuration file. Loads are serialized; reads are lock-free. */
final class ConfigRefImpl<T> implements ConfigRef<T> {

    private record Snapshot<T>(T value, Map<String, Object> values) {}

    private final ConfigSpec<T> spec;
    private final Path file;
    private final ConfigBinder binder;
    private final Logger logger;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot<T> snapshot;

    ConfigRefImpl(ConfigSpec<T> spec, Path dataFolder, ConfigBinder binder, Logger logger) {
        this.spec = spec;
        this.file = dataFolder.resolve(spec.file()).toAbsolutePath().normalize();
        if (!file.startsWith(dataFolder.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Configuration path escapes the data folder: " + spec.file());
        }
        this.binder = binder;
        this.logger = logger;
    }

    @Override
    public T get() {
        Snapshot<T> current = snapshot;
        if (current == null) {
            throw new IllegalStateException(spec.file() + " has not been loaded");
        }
        return current.value();
    }

    @Override
    public Class<T> type() {
        return spec.type();
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public synchronized ReloadResult reload() {
        String text;
        try {
            text = Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            return fail(List.of("cannot read file: " + e.getMessage()));
        }
        YamlConfiguration yaml;
        try {
            yaml = text == null ? new YamlConfiguration() : YamlFiles.parse(text, spec.file());
        } catch (IllegalArgumentException e) {
            return fail(List.of(e.getMessage()));
        }
        if (text == null && !spec.header().isEmpty()) {
            yaml.options().setHeader(spec.header());
        }
        T value = spec.factory().get();
        ConfigBinder.Pass pass = new ConfigBinder.Pass();
        try {
            binder.read(value, yaml, "", true, pass);
        } catch (RuntimeException e) {
            return fail(List.of("cannot bind " + spec.type().getSimpleName() + ": " + e.getMessage()));
        }
        if (!pass.errors.isEmpty()) {
            return fail(pass.errors);
        }
        List<String> warnings = new ArrayList<>();
        for (String warning : pass.warnings) {
            warnings.add(spec.file() + ": " + warning);
        }
        if (text == null || pass.changed) {
            try {
                YamlFiles.writeAtomically(file, yaml.saveToString());
            } catch (IOException e) {
                warnings.add(spec.file() + ": could not write default values: " + e.getMessage());
            }
        }
        publish(value, yaml);
        warnings.forEach(logger::warning);
        return ReloadResult.ok(warnings);
    }

    @Override
    public synchronized ReloadResult save(T value) {
        Objects.requireNonNull(value, "value");
        YamlConfiguration yaml;
        try {
            yaml = Files.exists(file) ? YamlFiles.parse(Files.readString(file), spec.file()) : new YamlConfiguration();
        } catch (IOException | IllegalArgumentException e) {
            return fail(List.of("cannot save, current file is unreadable: " + e.getMessage()));
        }
        try {
            binder.write(value, yaml);
        } catch (RuntimeException e) {
            return fail(List.of("cannot encode value: " + e.getMessage()));
        }
        // Re-read the written YAML into a fresh object so the published snapshot is validated and not shared with
        // the caller.
        T fresh = spec.factory().get();
        ConfigBinder.Pass pass = new ConfigBinder.Pass();
        binder.read(fresh, yaml, "", true, pass);
        if (!pass.errors.isEmpty()) {
            return fail(pass.errors);
        }
        try {
            YamlFiles.writeAtomically(file, yaml.saveToString());
        } catch (IOException e) {
            return fail(List.of("cannot write file: " + e.getMessage()));
        }
        publish(fresh, yaml);
        return ReloadResult.ok();
    }

    private void publish(T value, YamlConfiguration yaml) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        boolean initial = snapshot == null;
        snapshot = new Snapshot<>(value, Collections.unmodifiableMap(values));
        if (!initial) {
            for (Consumer<T> listener : listeners) {
                try {
                    listener.accept(value);
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE, "Configuration reload listener for " + spec.file() + " failed", e);
                }
            }
        }
    }

    private ReloadResult fail(List<String> problems) {
        List<String> errors = new ArrayList<>();
        for (String problem : problems) {
            errors.add(problem.startsWith(spec.file()) ? problem : spec.file() + ": " + problem);
        }
        if (snapshot != null) {
            errors.add(spec.file() + ": keeping the previously loaded values");
        }
        return ReloadResult.failed(errors);
    }

    @Override
    public void onReload(Consumer<T> listener) {
        listeners.add(listener);
    }

    @Override
    public Optional<Object> value(String path) {
        Snapshot<T> current = snapshot;
        return current == null
                ? Optional.empty()
                : Optional.ofNullable(current.values().get(path));
    }

    String fileName() {
        return spec.file();
    }
}
