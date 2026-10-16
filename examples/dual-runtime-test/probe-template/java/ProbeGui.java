package __PKG__;

import com.nookfall.framework.gui.Gui;
import com.nookfall.framework.gui.GuiAction;
import com.nookfall.framework.gui.GuiClickContext;
import com.nookfall.framework.gui.GuiContext;
import com.nookfall.framework.gui.GuiEntry;
import com.nookfall.framework.gui.GuiProvider;
import java.util.ArrayList;
import java.util.List;

@Gui("probe")
public final class ProbeGui {
    @GuiProvider("numbers")
    public List<GuiEntry<Integer>> numbers(GuiContext context) {
        List<GuiEntry<Integer>> out = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            out.add(GuiEntry.of("n" + i, i).placeholder("number", i));
        }
        return out;
    }

    @GuiAction("pick")
    public void pick(GuiClickContext context, GuiEntry<Integer> entry) {
        context.state().put("picked", entry.value());
    }
}
