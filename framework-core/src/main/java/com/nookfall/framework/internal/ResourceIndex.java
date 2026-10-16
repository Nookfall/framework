package com.nookfall.framework.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads resources bundled in a plugin's own jar (or classes directory during tests). Lookups never fall through to
 * other plugins or to the framework jar.
 */
public final class ResourceIndex {

    private final Path root;
    private final boolean jar;

    private ResourceIndex(Path root, boolean jar) {
        this.root = root;
        this.jar = jar;
    }

    public static ResourceIndex of(Class<?> owner) {
        CodeSource source = owner.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IllegalStateException("Cannot locate the jar of " + owner.getName());
        }
        try {
            Path path = Path.of(source.getLocation().toURI());
            return new ResourceIndex(path, Files.isRegularFile(path));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid code source for " + owner.getName(), e);
        }
    }

    /** Creates an index over a directory, for tests. */
    public static ResourceIndex ofDirectory(Path directory) {
        return new ResourceIndex(directory, false);
    }

    /** Resource paths under the prefix (for example {@code "lang/"}), not descending into subdirectories. */
    public List<String> list(String prefix, String suffix) {
        List<String> out = new ArrayList<>();
        if (jar) {
            try (ZipFile zip = new ZipFile(root.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(prefix) && name.endsWith(suffix) && name.indexOf('/', prefix.length()) < 0) {
                        out.add(name);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            Path dir = root.resolve(prefix);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(Files::isRegularFile)
                            .map(p -> prefix + p.getFileName())
                            .filter(n -> n.endsWith(suffix))
                            .forEach(out::add);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        out.sort(null);
        return out;
    }

    /** Subdirectory names directly under the prefix. */
    public List<String> directories(String prefix) {
        List<String> out = new ArrayList<>();
        if (jar) {
            try (ZipFile zip = new ZipFile(root.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(prefix)) {
                        int slash = name.indexOf('/', prefix.length());
                        if (slash > prefix.length()) {
                            String dir = name.substring(prefix.length(), slash);
                            if (!out.contains(dir)) {
                                out.add(dir);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            Path dir = root.resolve(prefix);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(Files::isDirectory)
                            .map(p -> p.getFileName().toString())
                            .forEach(out::add);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        out.sort(null);
        return out;
    }

    /** Reads a resource as UTF-8 text. */
    public Optional<String> read(String path) {
        try {
            if (jar) {
                try (ZipFile zip = new ZipFile(root.toFile())) {
                    ZipEntry entry = zip.getEntry(path);
                    if (entry == null) {
                        return Optional.empty();
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        return Optional.of(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
            Path file = root.resolve(path);
            return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
