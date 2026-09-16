package dev.jasper.app;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A small form the palette shows instead of its list. A scope returns one from {@link PaletteScope#step};
 * the palette collects the field values and hands them to {@code complete}, which answers with a
 * {@link Result}: an error keeps the step open, anything else dismisses the palette, optionally
 * reopening it in another scope with a row selected.
 */
record PaletteStep(String title, List<Field> fields, BiConsumer<Map<String, String>, Consumer<Result>> complete) {
    record Field(String name, String label, String prefill) {
        Field {
            Objects.requireNonNull(name);
            Objects.requireNonNull(label);
            prefill = prefill == null ? "" : prefill;
        }
    }

    record Result(String error, String reopenScopeId, String reopenRowId) {
        static Result done() { return new Result(null, null, null); }
        static Result error(String message) { return new Result(Objects.requireNonNull(message), null, null); }
        static Result reopen(String scopeId, String rowId) { return new Result(null, Objects.requireNonNull(scopeId), rowId); }
    }

    PaletteStep {
        Objects.requireNonNull(title);
        fields = List.copyOf(fields);
        if (fields.isEmpty()) throw new IllegalArgumentException("A step needs at least one field");
        Objects.requireNonNull(complete);
    }
}
