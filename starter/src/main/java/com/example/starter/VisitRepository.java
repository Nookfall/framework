package com.example.starter;

import com.nookfall.framework.storage.Database;
import com.nookfall.framework.storage.DatabaseType;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public final class VisitRepository {

    private final Database database;

    public VisitRepository(Database database) {
        this.database = database;
    }

    /** Records a visit and returns the player's total visits. */
    public CompletableFuture<Integer> recordVisit(UUID player, String name) {
        String upsert = database.type() == DatabaseType.SQLITE
                ? "INSERT INTO visits (player, name, visits, last_seen) VALUES (?, ?, 1, ?) "
                        + "ON CONFLICT(player) DO UPDATE SET visits = visits + 1, name = excluded.name, last_seen = excluded.last_seen"
                : "INSERT INTO visits (player, name, visits, last_seen) VALUES (?, ?, 1, ?) "
                        + "ON DUPLICATE KEY UPDATE visits = visits + 1, name = VALUES(name), last_seen = VALUES(last_seen)";
        return database.updateAsync(upsert, player, name, System.currentTimeMillis())
                .thenCompose(rows -> database.findAsync("SELECT visits FROM visits WHERE player = ?", r -> r.getInt(1), player))
                .thenApply(visits -> visits.orElse(1));
    }

    public CompletableFuture<List<String>> recentVisitors(int limit) {
        return database.listAsync("SELECT name FROM visits ORDER BY last_seen DESC LIMIT ?", r -> r.getString(1), limit);
    }

    public CompletableFuture<Integer> total() {
        return database.findAsync("SELECT COUNT(*) FROM visits", r -> r.getInt(1)).thenApply(c -> c.orElse(0));
    }
}
