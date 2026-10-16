package com.nookfall.probe.gui;

import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.plugin.Plugin;

/** Records GUI actions and writes PASS/FAIL lines for the harness. */
@Singleton
public final class Recorder {

    final List<String> actions = new CopyOnWriteArrayList<>();
    final List<String> notes = new CopyOnWriteArrayList<>();
    private final Plugin plugin;

    public Recorder(Plugin plugin) {
        this.plugin = plugin;
    }

    void action(String action) {
        actions.add(action);
        plugin.getLogger().info("GUI action " + action);
    }

    void line(String line) {
        try {
            Path file = plugin.getDataFolder().toPath().resolve("results.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void check(String name, boolean ok, String detail) {
        line(ok ? "PASS " + name : "FAIL " + name + ": " + detail);
    }
}
