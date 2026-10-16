package com.nookfall.framework.storage;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/** Runs the same contract against a real MariaDB server when Docker is available. */
@Testcontainers(disabledWithoutDocker = true)
class MariaDbDatabaseTest extends DatabaseContract {

    private static MariaDBContainer container;

    @TempDir
    Path directory;

    @BeforeAll
    static void start() {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MariaDBContainer("mariadb:11.4");
            container.start();
        }
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    protected Database open() throws Exception {
        try (var connection =
                        java.sql.DriverManager.getConnection(container.getJdbcUrl(), "root", container.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS nookfall_test");
            statement.execute("CREATE DATABASE nookfall_test");
        }
        DatabaseSettings settings = new DatabaseSettings();
        set(settings, "type", DatabaseType.MARIADB);
        set(settings, "host", container.getHost());
        set(settings, "port", container.getMappedPort(3306));
        set(settings, "database", "nookfall_test");
        set(settings, "username", "root");
        set(settings, "password", container.getPassword());
        return Database.create("test", directory, settings);
    }

    private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        var f = DatabaseSettings.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
