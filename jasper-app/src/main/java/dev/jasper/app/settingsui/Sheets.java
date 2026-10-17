package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Asks a small form in a sheet over the Settings dialog (plan 9b Rulings 14): its fields' values in order on its confirm
 * button, empty on Cancel, Escape or a closed sheet. EDT only; the dialog's implementation is modal.
 *
 * @param ask shows a request and returns its answer
 */
public record Sheets(Function<Sheets.Request, Optional<List<String>>> ask) {
    /** No dialog to ask over: every request is cancelled. */
    public static final Sheets NONE = new Sheets(request -> Optional.empty());

    /** The function is required. */
    public Sheets { Objects.requireNonNull(ask, "ask"); }

    /**
     * One text field of a sheet.
     *
     * @param id its id
     * @param label its label
     * @param value its initial text
     */
    public record Field(String id, String label, String value) {
        /** Every part is required. */
        public Field { Objects.requireNonNull(id); Objects.requireNonNull(label); Objects.requireNonNull(value); }
    }

    /**
     * A sheet.
     *
     * @param title the sheet's window title
     * @param heading the form's title
     * @param fields its fields, in order
     * @param confirm the primary button's label
     */
    public record Request(String title, String heading, List<Field> fields, String confirm) {
        /** Every part is required; the fields are copied. */
        public Request { Objects.requireNonNull(title); Objects.requireNonNull(heading); fields = List.copyOf(fields); Objects.requireNonNull(confirm); }
    }

    /** Asks {@code request}. */
    public Optional<List<String>> ask(Request request) { return ask.apply(request); }
}
