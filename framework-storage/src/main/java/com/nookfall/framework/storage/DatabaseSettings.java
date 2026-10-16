package com.nookfall.framework.storage;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.constraint.Range;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connection settings that can be embedded in a {@code @Config} class as a nested section:
 *
 * <pre>{@code
 * @Comment("Database connection.")
 * private DatabaseSettings database = new DatabaseSettings();
 * }</pre>
 */
public class DatabaseSettings {

    @Comment("sqlite, mysql or mariadb")
    private DatabaseType type = DatabaseType.SQLITE;

    @Comment("SQLite file, relative to the plugin folder.")
    private String file = "data.db";

    private String host = "localhost";

    @Range(min = 1, max = 65535)
    private int port = 3306;

    private String database = "minecraft";

    private String username = "root";

    private String password = "";

    @Comment("Maximum pooled connections (SQLite always uses one).")
    @Range(min = 1, max = 64)
    private int poolSize = 4;

    @Comment("Extra JDBC driver properties.")
    private Map<String, String> properties = new LinkedHashMap<>();

    public DatabaseType type() {
        return type;
    }

    public String file() {
        return file;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int poolSize() {
        return poolSize;
    }

    public Map<String, String> properties() {
        return properties;
    }
}
