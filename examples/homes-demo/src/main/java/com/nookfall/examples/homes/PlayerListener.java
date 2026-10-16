package com.nookfall.examples.homes;

import com.nookfall.framework.listener.ListenerComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@ListenerComponent
public final class PlayerListener implements Listener {

    private final HomeService homes;

    public PlayerListener(HomeService homes) {
        this.homes = homes;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        homes.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homes.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        homes.moved(event.getPlayer(), event.getFrom(), event.getTo());
    }
}
