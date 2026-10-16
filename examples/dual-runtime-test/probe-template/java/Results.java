package __PKG__;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.bukkit.plugin.Plugin;

/** Appends lines the integration harness reads: PASS/FAIL/INFO/DONE. */
final class Results {

    private Results() {}

    static synchronized void append(Plugin plugin, String line) {
        try {
            Path file = plugin.getDataFolder().toPath().resolve("results.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
