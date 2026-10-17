package dev.jasper.app.palette;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * What the palette's controller drives (Enterprise presentation spec 11.1): the query and chip, the list and its
 * selection, a step's small form and the view's own surface. Two implementations exist, the card ({@link CardView}) and
 * the Run window ({@link RunWindowView}), built by {@code PaletteViews}. EDT only.
 */
public sealed interface PaletteView extends AutoCloseable permits CardView, RunWindowView {
    /**
     * What a view calls back.
     *
     * @param queryChanged the query the person typed, after a scope prefix left it
     * @param execute run an entry with a verb index (0 is Enter's)
     * @param prefixes the scope a typed prefix names
     * @param chooseScope set the chip to a scope, or to none (All) for null
     * @param scopes the scopes in the order the mode list shows them
     * @param openerShortcut the opener's current shortcut text, blank when unbound
     * @param cancel close the palette
     * @param browse run File, Open File…
     */
    record Host(Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute, Function<String, Optional<ScopePrefix>> prefixes,
                Consumer<PaletteScope> chooseScope, Supplier<List<PaletteScope>> scopes, Supplier<String> openerShortcut,
                Runnable cancel, Runnable browse) {
        /** Every callback is required. */
        public Host {
            Objects.requireNonNull(queryChanged); Objects.requireNonNull(execute); Objects.requireNonNull(prefixes);
            Objects.requireNonNull(chooseScope); Objects.requireNonNull(scopes); Objects.requireNonNull(openerShortcut);
            Objects.requireNonNull(cancel); Objects.requireNonNull(browse);
        }
    }

    /** The Swing surface its host mounts. */
    JComponent component();
    /** Whether the view stays open while the person works elsewhere (the Run window); the card dismisses as before. */
    boolean modeless();
    /** The controller opened the view afresh. */
    void opened();
    /** Moves keyboard focus to the query field. */
    void focusQuery();
    /** Whether an input method is composing in the query field. */
    boolean composing();
    /** The query. */
    String queryText();
    /** Replaces the query as if typed; the same text changes nothing. */
    void setQueryText(String text);
    /** Shows {@code scope}'s chip, or none (All) for null. */
    void setChip(PaletteScope scope);
    /** The query field's placeholder. */
    void setPlaceholder(String text);
    /** Replaces the list, selecting {@code selectionKey} when present and otherwise the first selectable entry. */
    void setEntries(List<PaletteEntry> entries, String selectionKey, String empty);
    /** The selected entry, or null. */
    PaletteEntry selectedEntry();
    /** The selected entry's key, or null. */
    String selectedKey();
    /** Selects the first row with {@code rowId}. */
    void selectRow(String rowId);
    /** Moves {@code delta} selectable entries. */
    void selectRelative(int delta);
    /** Runs the selected entry with the view's verb for {@code verb}: 0 is the view's primary verb (spec 11.4). */
    void executeSelected(int verb);
    /** How many rows the list shows before it scrolls. */
    void setVisibleRows(int rows);
    /** How many rows the list shows before it scrolls. */
    int visibleRows();
    /** Shows a step's form in the list's place; its first field takes focus. */
    void showStep(String title, List<PaletteStep.Field> fields);
    /** Leaves a step. */
    void hideStep();
    /** Whether a step is showing. */
    boolean stepShowing();
    /** Moves focus to the next or previous step field, wrapping. */
    void focusStepField(int delta);
    /** The step's values by field name. */
    Map<String, String> stepValues();
    /** Shows a step's error, or none for null. */
    void setStepError(String message);
    /** Follows a theme change. */
    void refreshTheme();
    /** Shows only the top {@code fraction} of the view while it is revealed; 1 when fully shown. */
    void setReveal(double fraction);
    /** The height currently shown. */
    int revealedHeight();
    /** Closes the view's kit views. */
    @Override void close();
}
