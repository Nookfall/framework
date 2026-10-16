package com.nookfall.framework.util.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * UUID-oriented player helpers. Prefer storing UUIDs over {@link Player} references: a Player object becomes invalid
 * when the player disconnects.
 */
public final class Players {

    private Players() {}

    /** The online player with this UUID. */
    public static Optional<Player> online(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player != null && player.isOnline() ? Optional.of(player) : Optional.empty();
    }

    /** Exact (case-insensitive) online name lookup; does not match name prefixes. */
    public static Optional<Player> onlineByName(String name) {
        return Optional.ofNullable(Bukkit.getPlayerExact(name));
    }

    /**
     * A player that has joined this server before, looked up from the server's cache without blocking on a web
     * request.
     */
    public static Optional<OfflinePlayer> known(String name) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return Optional.ofNullable(cached);
    }

    /** True if the reference still points at a connected player. */
    public static boolean isStillOnline(Player player) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }

    /** Online player names that start with the input, ignoring case, for tab completion. */
    public static List<String> namesStartingWith(String input) {
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        return online.stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public static boolean hasAny(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAll(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (!sender.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a numeric limit from permissions such as {@code homes.limit.5}: returns the largest number granted, or
     * {@code fallback} if none.
     */
    public static int numericLimit(Player player, String prefix, int fallback) {
        String base = prefix.endsWith(".") ? prefix : prefix + ".";
        int best = Integer.MIN_VALUE;
        for (var info : player.getEffectivePermissions()) {
            String permission = info.getPermission();
            if (info.getValue() && permission.startsWith(base)) {
                try {
                    best = Math.max(best, Integer.parseInt(permission.substring(base.length())));
                } catch (NumberFormatException ignored) {
                    // not a numeric suffix
                }
            }
        }
        return best == Integer.MIN_VALUE ? fallback : best;
    }
}
