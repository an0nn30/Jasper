package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.UiColorKeys;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies the shared color-only policy after imported color references have been resolved. */
public final class ImportedColors {
    private ImportedColors() {}

    /** An ordered immutable color snapshot and one diagnostic for each discarded override key. */
    public record Result(Map<String, Object> colors, List<String> warnings) {
        public Result {
            colors = Collections.unmodifiableMap(new LinkedHashMap<>(colors));
            warnings = List.copyOf(warnings);
        }
    }

    /** Filters non-color data, preserving valid platform choices and diagnosing a partially discarded key once. */
    public static Result filter(Map<String, Object> source) {
        Objects.requireNonNull(source);
        var colors = new LinkedHashMap<String, Object>();
        var warnings = new ArrayList<String>();
        source.forEach((key, value) -> {
            Objects.requireNonNull(key);
            Object kept = null;
            boolean discarded = false;
            if (UiColorKeys.supports(key)) {
                if (value instanceof Map<?, ?> choices) {
                    var retained = new LinkedHashMap<String, Object>();
                    for (var entry : choices.entrySet()) {
                        if (entry.getKey() instanceof String os && os.startsWith("os.") && usable(key, entry.getValue()))
                            retained.put(os, entry.getValue());
                        else discarded = true;
                    }
                    if (!retained.isEmpty()) kept = Collections.unmodifiableMap(retained);
                } else if (usable(key, value)) kept = value;
            }
            if (kept != null) colors.put(key, kept);
            if (kept == null || discarded) warnings.add("discarded non-color override \"" + key + "\"");
        });
        return new Result(colors, warnings);
    }

    private static boolean usable(String key, Object value) {
        // IntelliJ null/blank colors historically mean absent, never a runtime delegate specification.
        if (value instanceof String text && (text.isBlank() || text.strip().equalsIgnoreCase("null"))) return false;
        return !(value instanceof Map<?, ?>) && UiColorKeys.supports(key, value);
    }
}
