package com.nookfall.probe.gui;

import com.nookfall.framework.gui.Gui;
import com.nookfall.framework.gui.GuiAction;
import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiProvider;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.inventory.ClickType;

@Gui("probe")
public final class ProbeMenu {

    private final Recorder recorder;

    public ProbeMenu(Recorder recorder) {
        this.recorder = recorder;
    }

    @GuiProvider("numbers")
    public List<GuiEntry<Integer>> numbers(GuiContext context) {
        List<GuiEntry<Integer>> out = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            out.add(GuiEntry.of("n" + i, i).placeholder("number", i));
        }
        return out;
    }

    @GuiAction("pick")
    public void pick(GuiEntry<Integer> entry) {
        recorder.action("pick:" + entry.id());
    }

    @GuiAction("inspect")
    public void inspect(GuiClickContext context, GuiEntry<Integer> entry, ClickType click) {
        recorder.action("inspect:" + entry.id() + ":" + click);
    }

    @GuiAction("mark")
    public void mark(GuiEntry<Integer> entry) {
        recorder.action("mark:" + entry.id());
    }
}
