package com.nookfall.examples.homes;

import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.task.TaskHandle;
import com.nookfall.framework.task.Tasks;
import com.nookfall.framework.util.location.Locations;
import com.nookfall.framework.util.location.SafeLocations;
import com.nookfall.framework.util.location.StoredLocation;
import com.nookfall.framework.util.player.Players;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Home logic. Database work runs asynchronously; results are handed back to the player's own thread before any
 * Bukkit API is touched.
 */
@Singleton
public final class HomeService {

    private final HomeRepository repository;
    private final ConfigRef<HomesConfig> config;
    private final HomeMessages messages;
    private final Tasks tasks;
    private final GuiManager guis;
    private final Logger logger;
    private final Map<UUID, List<Home>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, TaskHandle> pendingTeleports = new ConcurrentHashMap<>();

    public HomeService(
            HomeRepository repository,
            ConfigRef<HomesConfig> config,
            HomeMessages messages,
            Tasks tasks,
            GuiManager guis,
            Logger logger) {
        this.repository = repository;
        this.config = config;
        this.messages = messages;
        this.tasks = tasks;
        this.guis = guis;
        this.logger = logger;
    }

    public void openMenu(Player player) {
        guis.open(player, "homes");
    }

    /** Loads a player's homes into the cache. */
    public CompletableFuture<List<Home>> load(UUID owner) {
        return repository.findHomes(owner).thenApply(homes -> {
            cache.put(owner, homes);
            return homes;
        });
    }

    public void forget(UUID owner) {
        cache.remove(owner);
        TaskHandle pending = pendingTeleports.remove(owner);
        if (pending != null) {
            pending.cancel();
        }
    }

    /** Cached home names for tab completion. */
    public List<String> names(UUID owner) {
        return cache.getOrDefault(owner, List.of()).stream().map(Home::name).toList();
    }

    /** GUI entries, loaded from the database. */
    public CompletableFuture<List<GuiEntry<Home>>> entries(UUID owner) {
        return load(owner).thenApply(homes -> homes.stream()
                .map(home -> GuiEntry.of(home.name(), home)
                        .placeholder("home_name", home.name())
                        .placeholder("world", home.location().world())
                        .placeholder("x", (long) home.location().x())
                        .placeholder("y", (long) home.location().y())
                        .placeholder("z", (long) home.location().z()))
                .toList());
    }

    public void create(Player player, String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            messages.invalidName(player, name);
            return;
        }
        UUID owner = player.getUniqueId();
        int limit = Players.numericLimit(player, "homes.limit", config.get().maxHomes());
        List<Home> current = cache.getOrDefault(owner, List.of());
        boolean replacing = current.stream().anyMatch(h -> h.name().equalsIgnoreCase(name));
        if (!replacing && current.size() >= limit) {
            messages.limitReached(player, limit);
            return;
        }
        Home home = new Home(owner, name, StoredLocation.of(player.getLocation()), System.currentTimeMillis());
        repository.save(home)
                .thenCompose(rows -> load(owner))
                .whenCompleteAsync((homes, error) -> {
                    if (error != null) {
                        fail(player, "save home " + name, error);
                    } else {
                        messages.created(player, name);
                    }
                }, tasks.entity(player).executor());
    }

    public void delete(Player player, String name) {
        UUID owner = player.getUniqueId();
        repository.delete(owner, name)
                .thenCompose(deleted -> load(owner).thenApply(homes -> deleted))
                .whenCompleteAsync((deleted, error) -> {
                    if (error != null) {
                        fail(player, "delete home " + name, error);
                    } else if (deleted) {
                        messages.deleted(player, name);
                        guis.refresh(player);
                    } else {
                        messages.notFound(player, name);
                    }
                }, tasks.entity(player).executor());
    }

    public void teleport(Player player, String name) {
        Optional<Home> home = cache.getOrDefault(player.getUniqueId(), List.of()).stream()
                .filter(h -> h.name().equalsIgnoreCase(name))
                .findFirst();
        if (home.isEmpty()) {
            messages.notFound(player, name);
            return;
        }
        teleport(player, home.get());
    }

    /** Teleports after the configured delay; runs on the player's thread and stops if the player leaves. */
    public void teleport(Player player, Home home) {
        HomesConfig settings = config.get();
        Optional<Location> target = home.location().resolve();
        if (target.isEmpty()) {
            messages.worldMissing(player, home.name());
            return;
        }
        player.closeInventory();
        messages.teleporting(player, home.name(), settings.teleportDelay());
        TaskHandle previous = pendingTeleports.put(player.getUniqueId(), tasks.entity(player).later(settings.teleportDelay(), () -> {
            pendingTeleports.remove(player.getUniqueId());
            Location destination = target.get();
            if (settings.safeTeleport()) {
                Optional<Location> safe = SafeLocations.findNearby(destination, 4);
                if (safe.isEmpty()) {
                    messages.unsafe(player, home.name());
                    return;
                }
                destination = safe.get();
            }
            player.teleportAsync(destination).thenAccept(success -> {
                if (success) {
                    messages.teleported(player, home.name());
                }
            });
        }));
        if (previous != null) {
            previous.cancel();
        }
    }

    /** Cancels a pending teleport when the player moves to another block. */
    public void moved(Player player, Location from, Location to) {
        if (!config.get().cancelOnMove() || Locations.sameBlock(from, to)) {
            return;
        }
        TaskHandle pending = pendingTeleports.remove(player.getUniqueId());
        if (pending != null) {
            pending.cancel();
            messages.cancelled(player);
        }
    }

    private void fail(Player player, String action, Throwable error) {
        logger.log(Level.SEVERE, "Could not " + action + " for " + player.getName(), error);
        if (player.isOnline()) {
            messages.error(player);
        }
    }
}
