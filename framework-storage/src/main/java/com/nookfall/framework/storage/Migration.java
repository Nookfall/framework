package com.nookfall.framework.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
