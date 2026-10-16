package com.nookfall.examples.homes;

import com.nookfall.framework.message.Messages;
import java.time.Duration;
import org.bukkit.entity.Player;

@Messages("homes")
public interface HomeMessages {
    void created(Player player, String home);

    void deleted(Player player, String home);

    void notFound(Player player, String home);

    void limitReached(Player player, int limit);

    void invalidName(Player player, String home);

    void teleporting(Player player, String home, Duration delay);

    void teleported(Player player, String home);

    void cancelled(Player player);

    void unsafe(Player player, String home);

    void worldMissing(Player player, String home);

    void error(Player player);
}
