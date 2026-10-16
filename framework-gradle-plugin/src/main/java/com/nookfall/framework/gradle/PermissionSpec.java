package com.nookfall.framework.gradle;

import org.gradle.api.Named;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * A permission declared in plugin.yml, so the server knows its description, default and children:
 *
 * <pre>{@code
 * nookfall {
 *     permissions {
 *         register("myplugin.use") {
 *             description = "Use the plugin"
 *             default = "true" // true, false, op or "not op"
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Undeclared permissions default to operators only.
 */
public abstract class PermissionSpec implements Named {

    @Override
    public abstract String getName();

    public abstract Property<String> getDescription();

    /** {@code true}, {@code false}, {@code op} or {@code not op}. Defaults to {@code op}. */
    public abstract Property<String> getDefault();

    /** Child permissions granted (true) or denied (false) with this one. */
    public abstract MapProperty<String, Boolean> getChildren();
}
