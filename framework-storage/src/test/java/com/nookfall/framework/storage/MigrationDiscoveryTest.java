package com.nookfall.framework.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationDiscoveryTest {

    @Test
    void findsVersionedFilesInOrder() {
        var migrations = Migration.discover(MigrationDiscoveryTest.class, "db/migration");
        assertThat(migrations).extracting(Migration::version).containsExactly(1, 2);
        assertThat(migrations).extracting(Migration::description).containsExactly("create discovered", "add name");
    }

    @Test
    void findsFilesInsidePluginJars(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("plugin.jar");
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream zip = new JarOutputStream(out)) {
            for (String name : new String[] {"sql/V10__ten.sql", "sql/V2__two.sql", "sql/nested/V3__nested.sql"}) {
                zip.putNextEntry(new JarEntry(name));
                zip.write("SELECT 1;".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            assertThat(Migration.discover(loader, null, "sql"))
                    .as("no directory entries to find")
                    .isEmpty();
            assertThat(Migration.discover(loader, jar.toUri().toURL(), "sql"))
                    .as("sorted numerically, subdirectories ignored")
                    .extracting(Migration::version)
                    .containsExactly(2, 10);
        }
    }

    @Test
    void rejectsBadNamesAndDuplicateVersions(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("bad"));
        Files.writeString(dir.resolve("bad/create.sql"), "SELECT 1;");
        Files.createDirectories(dir.resolve("twice"));
        Files.writeString(dir.resolve("twice/V1__a.sql"), "SELECT 1;");
        Files.writeString(dir.resolve("twice/V01__b.sql"), "SELECT 2;");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, null)) {
            assertThatThrownBy(() -> Migration.discover(loader, null, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be named V<version>__<description>.sql");
            assertThatThrownBy(() -> Migration.discover(loader, null, "twice"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("have version 1");
            assertThat(Migration.discover(loader, null, "missing")).isEmpty();
        }
    }
}
