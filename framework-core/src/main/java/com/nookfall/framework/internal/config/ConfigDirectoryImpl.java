package com.nookfall.framework.internal.config;

import com.nookfall.framework.ReloadResult;
import com.nookfall.framework.config.ConfigDirectory;
import com.nookfall.framework.internal.YamlFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/** A folder of files read into the same class. Loads are serialized; reads are lock-free. */
final class ConfigDirectoryImpl<T> implements ConfigDirectory<T> {

    private final Class<T> type;
    private final String folder;
    private final Path directory;
    private final @Nullable Supplier<Map<String, String>> defaults;
    private final ConfigBinder binder;
    private final Logger logger;
    private final List<Consumer<Map<String, T>>> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, T> snapshot;

    ConfigDirectoryImpl(
            Class<T> type,
            String folder,
            Path dataFolder,
            @Nullable Supplier<Map<String, String>> defaults,
            ConfigBinder binder,
            Logger logger) {
        this.type = type;
        this.folder = folder;
        this.directory = dataFolder.resolve(folder).toAbsolutePath().normalize();
        if (!directory.startsWith(dataFolder.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Configuration folder escapes the data folder: " + folder);
        }
        this.defaults = defaults;
        this.binder = binder;
        this.logger = logger;
    }

    @Override
    public Map<String, T> get() {
        Map<String, T> current = snapshot;
        if (current == null) {
            throw new IllegalStateException(folder + "/ has not been loaded");
        }
        return current;
    }

    @Override
    public Path directory() {
        return directory;
    }

    @Override
    public synchronized ReloadResult reload() {
        List<Path> files;
        try {
            files = yamlFiles();
            if (files.isEmpty() && defaults != null) {
                writeDefaults();
                files = yamlFiles();
            }
        } catch (IOException e) {
            return fail(List.of("cannot read the folder: " + e.getMessage()));
        }
        Map<String, T> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            String key = name.substring(0, name.length() - ".yml".length());
            String source = folder + "/" + name;
            try {
                YamlConfiguration yaml = YamlFiles.parse(Files.readString(file), source);
                T value = type.cast(ConfigBinder.instantiate(type));
                ConfigBinder.Pass pass = new ConfigBinder.Pass();
                binder.read(value, yaml, "", true, pass);
                pass.errors.forEach(error -> errors.add(source + ": " + error));
                pass.warnings.forEach(warning -> warnings.add(source + ": " + warning));
                if (pass.errors.isEmpty()) {
                    if (pass.changed) {
                        try {
                            YamlFiles.writeAtomically(file, yaml.saveToString());
                        } catch (IOException e) {
                            warnings.add(source + ": could not write default values: " + e.getMessage());
                        }
                    }
                    loaded.put(key, value);
                }
            } catch (IOException | RuntimeException e) {
                errors.add(source + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            return fail(errors);
        }
        boolean initial = snapshot == null;
        snapshot = Collections.unmodifiableMap(loaded);
        warnings.forEach(logger::warning);
        if (!initial) {
            for (Consumer<Map<String, T>> listener : listeners) {
                try {
                    listener.accept(snapshot);
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE, "Configuration reload listener for " + folder + "/ failed", e);
                }
            }
        }
        return ReloadResult.ok(warnings);
    }

    private List<Path> yamlFiles() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(directory)) {
            return list.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    private void writeDefaults() throws IOException {
        Files.createDirectories(directory);
        for (Map.Entry<String, String> file : defaults.get().entrySet()) {
            String name = file.getKey();
            if (name.isBlank() || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
                throw new IOException("invalid default file name '" + name + "'");
            }
            YamlFiles.writeAtomically(directory.resolve(name + ".yml"), file.getValue());
        }
    }

    private ReloadResult fail(List<String> problems) {
        List<String> errors = new ArrayList<>(problems);
        if (snapshot != null) {
            errors.add(folder + "/: keeping the previously loaded files");
        }
        return ReloadResult.failed(errors);
    }

    @Override
    public void onReload(Consumer<Map<String, T>> listener) {
        listeners.add(listener);
    }
}
