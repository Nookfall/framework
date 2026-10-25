package com.nookfall.framework.storage;

import com.nookfall.framework.config.Describe;

/** Supported databases. */
public enum DatabaseType {
    /** A local file; the driver ships with Paper. */
    @Describe("a file in the plugin folder, no setup needed (one server only)")
    SQLITE,
    /** MySQL; the driver ships with Paper. */
    @Describe("a MySQL server, which several servers can share")
    MYSQL,
    /**
     * MariaDB. Uses the MariaDB driver when the plugin provides it (for example via plugin.yml {@code libraries}),
     * otherwise the MySQL driver that ships with Paper.
     */
    @Describe("a MariaDB server, which several servers can share")
    MARIADB
}
