package dev.jasper.sdk.ui;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;

/** Shared descriptor validation, independent of UI and theme. */
final class ModelChecks {
    private ModelChecks() {}
    static String id(String value) { return label(value, "id"); }
    static String label(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
    static void button(String label, String icon, String tooltip) {
        Objects.requireNonNull(label); Objects.requireNonNull(icon); Objects.requireNonNull(tooltip);
        if (label.isBlank()) { label(icon, "icon"); label(tooltip, "icon-only tooltip"); }
    }
    static <T> void unique(Collection<T> values, Function<T, String> id) {
        var seen = new HashSet<String>();
        for (T value : values) if (!seen.add(id.apply(value))) throw new IllegalArgumentException("Duplicate id: " + id.apply(value));
    }
}
