package com.nookfall.framework.storage;

/** Supported databases. */
public enum DatabaseType {
    /** A local file; the driver ships with Paper. */
    SQLITE,
    /** MySQL; the driver ships with Paper. */
    MYSQL,
    /**
     * MariaDB. Uses the MariaDB driver when the plugin provides it (for example via plugin.yml {@code libraries}),
     * otherwise the MySQL driver that ships with Paper.
     */
    MARIADB
}
