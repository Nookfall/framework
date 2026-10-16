package com.nookfall.framework.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Version of the framework classes that are currently loaded, and compatibility rules. */
public final class FrameworkVersion {

    public static final String CURRENT = load();

    private FrameworkVersion() {}

    private static String load() {
        // Resolved relative to this class so a relocated copy reads the properties of its own jar.
        try (InputStream in = FrameworkVersion.class.getResourceAsStream("/META-INF/nookfall/framework.properties")) {
            if (in == null) {
                return "0.0.0-unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "0.0.0-unknown");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Whether a plugin compiled against {@code compiled} can run on {@code runtime}. Releases with the same major
     * version are compatible when the runtime is not older; during 0.x the minor version must also match.
     */
    public static boolean compatible(String compiled, String runtime) {
        int[] c = parse(compiled);
        int[] r = parse(runtime);
        if (c[0] != r[0]) {
            return false;
        }
        if (c[0] == 0) {
            return c[1] == r[1] && r[2] >= c[2];
        }
        return r[1] > c[1] || (r[1] == c[1] && r[2] >= c[2]);
    }

    static int[] parse(String version) {
        String core = version.split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
