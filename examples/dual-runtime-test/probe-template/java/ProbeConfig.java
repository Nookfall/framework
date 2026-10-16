package __PKG__;

import com.nookfall.framework.config.Comment;
import com.nookfall.framework.config.Config;
import com.nookfall.framework.constraint.Range;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Config(value = "config.yml", header = "Probe configuration")
public class ProbeConfig {

    @Comment("The answer.")
    @Range(min = 0, max = 1000)
    private int answer = 42;

    @Comment("Delay used by tests.")
    private Duration delay = Duration.ofSeconds(2);

    private List<String> words = List.of("alpha", "beta");

    private Map<String, Integer> limits = Map.of("default", 3);

    private Tick tick = new Tick();

    public static class Tick {
        @Comment("How often the tick job runs.")
        private String interval = "1s";
    }

    public int answer() {
        return answer;
    }

    public Duration delay() {
        return delay;
    }

    public List<String> words() {
        return words;
    }

    public Map<String, Integer> limits() {
        return limits;
    }
}
