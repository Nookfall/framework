package com.nookfall.framework.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import org.jspecify.annotations.Nullable;

/**
 * One schema change. Migrations run once, in version order, each in its own transaction, and are recorded in the
 * {@code nookfall_migrations} table.
 *
 * @param checksum detects edits to already applied SQL migrations; {@code null} for code migrations
 */
public record Migration(
        int version,
        String description,
        SqlConsumer<Connection> action,
        @Nullable String checksum) {

    public Migration {
        if (version < 1) {
            throw new IllegalArgumentException("Migration versions start at 1");
        }
    }

    /** SQL statements separated by semicolons at line ends. */
    public static Migration sql(int version, String description, String sql) {
        List<String> statements = split(sql);
        return new Migration(
                version,
                description,
                connection -> {
                    try (Statement statement = connection.createStatement()) {
                        for (String part : statements) {
                            statement.execute(part);
                        }
                    }
                },
                sha256(sql));
    }

    /** SQL loaded from a resource bundled with the plugin, for example {@code db/V1__homes.sql}. */
    public static Migration resource(int version, String description, Class<?> owner, String path) {
        try (InputStream in = owner.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("Migration resource not found: " + path);
            }
            return sql(version, description, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Finds the SQL migrations bundled in a directory of the plugin, named {@code V<version>__<description>.sql}, for
     * example {@code db/migration/V1__create_homes.sql} (underscores in the description become spaces). New migrations
     * are picked up by adding a file; there is no list to keep in sync. Subdirectories are ignored.
     *
     * @param owner a class from the plugin; its jar and class loader are searched
     * @throws IllegalArgumentException if a file does not follow the naming scheme or two files share a version
     */
    public static List<Migration> discover(Class<?> owner, String directory) {
        CodeSource source = owner.getProtectionDomain().getCodeSource();
        URL jar = source != null
                        && source.getLocation() != null
                        && source.getLocation().getPath().endsWith(".jar")
                ? source.getLocation()
                : null;
        return discover(owner.getClassLoader(), jar, directory);
    }

    /** @param jar the plugin jar, listed directly because jars do not always contain directory entries */
    static List<Migration> discover(ClassLoader loader, @Nullable URL jar, String directory) {
        String dir = directory.replaceAll("^/+|/+$", "");
        Set<String> files = new TreeSet<>();
        try {
            if (jar != null) {
                files.addAll(listJar(Path.of(URI.create(jar.toString())), dir));
            }
            Enumeration<URL> urls = loader.getResources(dir);
            while (urls.hasMoreElements()) {
                files.addAll(listDirectory(urls.nextElement(), dir));
            }
            TreeMap<Integer, Migration> found = new TreeMap<>();
            for (String file : files) {
                if (!file.endsWith(".sql")) {
                    continue;
                }
                Matcher name = FILE_NAME.matcher(file);
                if (!name.matches()) {
                    throw new IllegalArgumentException(
                            "Migration " + dir + "/" + file + " must be named V<version>__<description>.sql");
                }
                int version = Integer.parseInt(name.group(1));
                String path = dir + "/" + file;
                try (InputStream in = loader.getResourceAsStream(path)) {
                    if (in == null) {
                        throw new IllegalArgumentException("Migration resource not found: " + path);
                    }
                    String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    if (found.put(version, sql(version, name.group(2).replace('_', ' '), sql)) != null) {
                        throw new IllegalArgumentException("Two migrations in " + dir + " have version " + version);
                    }
                }
            }
            return List.copyOf(found.values());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final java.util.regex.Pattern FILE_NAME = java.util.regex.Pattern.compile("V(\\d+)__(.+)\\.sql");

    /** Files directly inside {@code dir} of a jar. */
    private static List<String> listJar(Path jar, String dir) throws IOException {
        String prefix = dir + "/";
        List<String> out = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            file.stream()
                    .map(ZipEntry::getName)
                    .filter(n ->
                            n.startsWith(prefix) && n.length() > prefix.length() && n.indexOf('/', prefix.length()) < 0)
                    .forEach(n -> out.add(n.substring(prefix.length())));
        }
        return out;
    }

    /** Files directly inside a directory found on the class path, in a jar or on disk. */
    private static List<String> listDirectory(URL url, String dir) throws IOException {
        if (url.getProtocol().equals("jar")) {
            URL jar = ((JarURLConnection) url.openConnection()).getJarFileURL();
            return listJar(Path.of(URI.create(jar.toString())), dir);
        }
        if (url.getProtocol().equals("file")) {
            try (var entries = Files.list(Path.of(URI.create(url.toString())))) {
                return entries.filter(Files::isRegularFile)
                        .map(f -> f.getFileName().toString())
                        .toList();
            }
        }
        throw new IOException("Cannot list migrations at " + url);
    }

    /** A migration written in Java. */
    public static Migration code(int version, String description, SqlConsumer<Connection> action) {
        return new Migration(version, description, action, null);
    }

    static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().strip();
                out.add(statement.substring(0, statement.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            out.add(current.toString().strip());
        }
        return out;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(text.replace("\r\n", "\n").strip().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    void apply(Connection connection) throws SQLException {
        action.accept(connection);
    }
}
