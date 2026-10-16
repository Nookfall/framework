package __PKG__;

import com.nookfall.framework.command.Command;
import com.nookfall.framework.command.Execute;
import org.bukkit.command.CommandSender;

/** Every probe registers this label, so all but one must fall back to the namespaced form. */
@Command("nookfall-probe-shared")
public final class SharedNameCommand {
    @Execute
    void run(CommandSender sender) {
        sender.sendMessage("shared-name:__NAME__");
    }
}
