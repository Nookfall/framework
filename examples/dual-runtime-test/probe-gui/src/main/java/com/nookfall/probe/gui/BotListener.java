package com.nookfall.probe.gui;

import com.nookfall.framework.gui.GuiManager;
import com.nookfall.framework.internal.gui.GuiManagerImpl;
import com.nookfall.framework.listener.ListenerComponent;
import com.nookfall.framework.task.Tasks;
import java.time.Duration;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** When the bot leaves, verify the recorded interactions and finish. */
@ListenerComponent
public final class BotListener implements Listener {

    private final Recorder recorder;
    private final GuiManager guis;
    private final Tasks tasks;

    public BotListener(Recorder recorder, GuiManager guis, Tasks tasks) {
        this.recorder = recorder;
        this.guis = guis;
        this.tasks = tasks;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tasks.global().later(Duration.ofSeconds(3), this::report);
    }

    private void report() {
        List<String> expected = List.of(
                "pick:n2", "inspect:n3:RIGHT", "mark:n4", "pick:n10", "pick:n10", "pick:n10", "slow-complete");
        List<String> actual = recorder.actions.stream().toList();
        recorder.line("INFO actions " + actual);
        recorder.check("gui-actions", actual.equals(expected), "expected " + expected + " but got " + actual);
        recorder.check("gui-sessions-closed", ((GuiManagerImpl) guis).openSessions() == 0,
                "open sessions: " + ((GuiManagerImpl) guis).openSessions());
        recorder.line("DONE run=1");
    }
}
