package com.nookfall.framework;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a reload. When {@link #success()} is false the previous working state stays active.
 *
 * @param errors problems that prevented the reload from being applied
 * @param warnings problems that were tolerated, such as unknown configuration keys
 */
public record ReloadResult(List<String> errors, List<String> warnings) {

    public ReloadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public static ReloadResult ok() {
        return new ReloadResult(List.of(), List.of());
    }

    public static ReloadResult ok(List<String> warnings) {
        return new ReloadResult(List.of(), warnings);
    }

    public static ReloadResult failed(List<String> errors) {
        return new ReloadResult(errors, List.of());
    }

    public boolean success() {
        return errors.isEmpty();
    }

    public ReloadResult merge(ReloadResult other) {
        List<String> e = new ArrayList<>(errors);
        e.addAll(other.errors);
        List<String> w = new ArrayList<>(warnings);
        w.addAll(other.warnings);
        return new ReloadResult(e, w);
    }
}
