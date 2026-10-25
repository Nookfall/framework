package com.nookfall.framework.storage;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.constraint.Range;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ready-made storage section of a configuration. Embed it as a field; it documents itself in the generated file, so
 * there is nothing to write:
 *
 * <pre>{@code
 * private DatabaseSettings database = new DatabaseSettings();
 * }</pre>
 *
 * <p>Open the database with {@link Databases#open}. Settings compare by value, so after a reload
 * {@code !before.equals(after)} tells that a restart is needed.
 */
@Comment({
    "Where data is stored. SQLITE needs no setup; MYSQL and MARIADB let several servers share it.",
    "Changes take effect after a server restart."
})
public class DatabaseSettings {

    private DatabaseType type = DatabaseType.SQLITE;

    @Comment("The file used by SQLITE, relative to the plugin folder.")
    private String file = "data.db";

    @Comment("The server used by MYSQL and MARIADB: host, port, database name and login.")
    private String host = "localhost";

    @Range(min = 1, max = 65535)
    private int port = 3306;

    private String database = "minecraft";

    private String username = "root";

    private String password = "";

    @Comment("Maximum pooled connections (SQLITE always uses one).")
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

    @Override
    public boolean equals(Object other) {
        return other instanceof DatabaseSettings that
                && type == that.type
                && port == that.port
                && poolSize == that.poolSize
                && java.util.Objects.equals(file, that.file)
                && java.util.Objects.equals(host, that.host)
                && java.util.Objects.equals(database, that.database)
                && java.util.Objects.equals(username, that.username)
                && java.util.Objects.equals(password, that.password)
                && java.util.Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, file, host, port, database, username, password, poolSize, properties);
    }
}
