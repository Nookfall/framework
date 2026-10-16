package __PKG__;

import com.nookfall.framework.message.Messages;
import java.util.Locale;
import net.kyori.adventure.text.Component;

@Messages("probe")
public interface ProbeMessages {
    Component greeting(Locale locale, String name);
}
