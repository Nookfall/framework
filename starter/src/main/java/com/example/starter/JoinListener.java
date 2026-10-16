package com.example.starter;

import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.listener.ListenerComponent;
import com.nookfall.framework.task.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@ListenerComponent
public final class JoinListener implements Listener {

    private final VisitRepository visits;
    private final StarterMessages messages;
    private final ConfigRef<StarterConfig> config;
    private final Tasks tasks;

    public JoinListener(VisitRepository visits, StarterMessages messages, ConfigRef<StarterConfig> config, Tasks tasks) {
        this.visits = visits;
        this.messages = messages;
        this.config = config;
        this.tasks = tasks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        visits.recordVisit(player.getUniqueId(), player.getName()).thenAcceptAsync(count -> {
            if (config.get().greetOnJoin()) {
                messages.welcome(player, count);
            }
        }, tasks.entity(player).executor());
    }
}
