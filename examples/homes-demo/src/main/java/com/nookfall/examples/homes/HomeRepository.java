package com.nookfall.examples.homes;

import com.nookfall.framework.storage.Database;
import com.nookfall.framework.storage.DatabaseType;
import com.nookfall.framework.util.location.StoredLocation;
import jakarta.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** SQL access for homes. Every method runs on database threads. */
@Singleton
public final class HomeRepository {

    private final Database database;

    public HomeRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<List<Home>> findHomes(UUID owner) {
        return database.listAsync(
                "SELECT owner, name, location, created_at FROM homes WHERE owner = ? ORDER BY name",
                HomeRepository::map,
                owner);
    }

    public CompletableFuture<Integer> save(Home home) {
        String sql = database.type() == DatabaseType.SQLITE
                ? "INSERT INTO homes (owner, name, location, created_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(owner, name) DO UPDATE SET location = excluded.location"
                : "INSERT INTO homes (owner, name, location, created_at) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE location = VALUES(location)";
        return database.updateAsync(sql, home.owner(), home.name(), home.location().serialize(), home.createdAt());
    }

    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        return database.updateAsync("DELETE FROM homes WHERE owner = ? AND name = ?", owner, name)
                .thenApply(rows -> rows > 0);
    }

    public CompletableFuture<List<String>> worlds() {
        return database.listAsync("SELECT DISTINCT location FROM homes", row -> {
            String location = row.getString(1);
            return location.substring(0, location.indexOf(';'));
        });
    }

    public CompletableFuture<Integer> deleteInWorld(String world) {
        return database.updateAsync("DELETE FROM homes WHERE location LIKE ?", world + ";%");
    }

    private static Home map(ResultSet row) throws SQLException {
        return new Home(
                UUID.fromString(row.getString("owner")),
                row.getString("name"),
                StoredLocation.parse(row.getString("location")),
                row.getLong("created_at"));
    }
}
