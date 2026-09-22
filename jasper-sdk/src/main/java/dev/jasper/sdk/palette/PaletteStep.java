package dev.jasper.sdk.palette;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A small form the palette shows instead of its list. A scope returns one from
 * {@link PaletteScope#step}; the palette collects the field values and hands them to
 * {@code complete}, which answers with a {@link Result} from any thread: an error keeps the form
 * open, anything else dismisses the palette, optionally reopening it in another scope.
 *
 * @param title    the form's heading
 * @param fields   at least one field, in display order
 * @param complete receives the values by field name and a callback for the result, called exactly once
 */
public record PaletteStep(String title, List<Field> fields, BiConsumer<Map<String, String>, Consumer<Result>> complete) {
    /**
     * One text field.
     *
     * @param name    the key in the values map
     * @param label   what the user sees
     * @param prefill the initial text, or null for empty
     */
    public record Field(String name, String label, String prefill) {
        /** Validates the name and label; a null {@code prefill} becomes empty. */
        public Field {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("A field needs a name");
            if (label == null || label.isBlank()) throw new IllegalArgumentException("A field needs a label");
            prefill = prefill == null ? "" : prefill;
        }
    }

    /**
     * How a step ended.
     *
     * @param error         a message that keeps the form open
     * @param reopenScopeId a scope to reopen the palette in, after dismissing
     * @param reopenRowId   the row to select there
     * @param reopenQuery   the query text to show there
     */
    public record Result(Optional<String> error, Optional<String> reopenScopeId, Optional<String> reopenRowId, Optional<String> reopenQuery) {
        /** Rejects nulls. */
        public Result {
            Objects.requireNonNull(error, "error"); Objects.requireNonNull(reopenScopeId, "reopenScopeId");
            Objects.requireNonNull(reopenRowId, "reopenRowId"); Objects.requireNonNull(reopenQuery, "reopenQuery");
        }

        /**
     * The work is done; dismiss.
     *
     * @return the work is done; dismiss
     */
        public static Result done() { return new Result(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()); }

        /**
     * Derives a value.
     *
     * @param message what went wrong
         *  @return keep the form open with that message */
        public static Result error(String message) {
            return new Result(Optional.of(Objects.requireNonNull(message, "message")), Optional.empty(), Optional.empty(), Optional.empty());
        }

        /**
         * Dismiss, then reopen the palette elsewhere.
         *
         * @param scopeId the scope to reopen in
         * @param rowId   the row to select, if any
         * @param query   the query text, if any
         * @return dismiss, then reopen there
         */
        public static Result reopen(String scopeId, Optional<String> rowId, Optional<String> query) {
            return new Result(Optional.empty(), Optional.of(Objects.requireNonNull(scopeId, "scopeId")), rowId, query);
        }
    }

    /** Validates the title, fields and completion. */
    public PaletteStep {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A step needs a title");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) throw new IllegalArgumentException("A step needs at least one field");
        Objects.requireNonNull(complete, "complete");
    }
}
