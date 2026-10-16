package com.nookfall.probe.gui;

import com.nookfall.framework.gui.Gui;
import com.nookfall.framework.gui.GuiContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiProvider;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** An asynchronous provider that takes two seconds, used to test results arriving after the GUI closed. */
@Gui("slow")
public final class SlowMenu {

    private final Recorder recorder;

    public SlowMenu(Recorder recorder) {
        this.recorder = recorder;
    }

    @GuiProvider("slow")
    public CompletableFuture<List<GuiEntry<Integer>>> slow(GuiContext context) {
        return CompletableFuture.supplyAsync(
                () -> {
                    recorder.action("slow-complete");
                    return List.of(GuiEntry.of("late", 1));
                },
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));
    }
}
