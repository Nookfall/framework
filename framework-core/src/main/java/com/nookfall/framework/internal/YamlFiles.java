package com.nookfall.framework.internal;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML file helpers shared by configuration, translations and GUIs. */
public final class YamlFiles {

    private YamlFiles() {}

    /**
     * Parses YAML text, keeping comments.
     *
     * @throws IllegalArgumentException with the parser's message if the YAML is malformed
     */
    public static YamlConfiguration parse(String text, String source) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            String message =
                    e.getMessage() == null ? e.toString() : e.getMessage().strip();
            throw new IllegalArgumentException(source + " is not valid YAML: " + message, e);
        }
        return yaml;
    }

    /** Writes the file through a temporary file and an atomic rename, so readers never see partial content. */
    public static void writeAtomically(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
