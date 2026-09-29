package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;

import java.util.List;
import javax.swing.Icon;
import java.util.Optional;

/**
 * One kind of searchable thing. The palette shows no chip (All) or one scope's chip; a scope sees only
 * its own query and produces only its own rows. Every method runs on the EDT; search and availability
 * do no I/O.
 */
public interface PaletteScope {
    String COMMANDS_ID = "jasper.commands";
    /** No chip: the mode list, or All's grouped results; reserved, so no scope registers under it. */
    String ALL_ID = "jasper.all";

    String id();
    String label();
    default Icon icon() { return null; }
    /** One line, shown in the hint bar under the scope's mode row. */
    default String description() { return ""; }
    String placeholder();
    /** One to three verbs, bound in order to Enter, Cmd/Ctrl+Enter and Shift+Enter. */
    List<PaletteVerb> verbs();
    default boolean monospaceRows() { return false; }
    /** Whether a query with no chip (All) searches this scope too; true unless the scope opts out. */
    default boolean inAll() { return true; }
    /**
     * Whether the palette may offer this scope's rows before anything is typed, among the recently and most used
     * items; a scope that stays out of All stays out of these too.
     */
    default boolean inRecents() { return inAll(); }

    /**
     * A contributed action whose shortcut, while the palette is open, switches to or dismisses this
     * scope's chip instead of being swallowed; the palette itself is routed by {@code ActionId} instead.
     */
    default Optional<String> shortcutActionId() { return Optional.empty(); }
    /** This scope's chip set, or the palette opened with no chip; a scope may ask its index for a background refresh here. */
    default void activated(PaletteContext context) {}
    /** At most {@code context.maxResults()} rows. */
    PaletteResults search(String query, PaletteContext context);
    /** Rechecked immediately before execution; a false answer refreshes the list instead of executing. */
    default boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) { return row.enabled(); }
    /**
     * Consulted before {@link #execute}: a non-null step is shown in the palette instead of running the verb,
     * and {@code execute} is not called for that action. The step's completion does the work.
     */
    default PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) { return null; }
    void execute(PaletteRow row, PaletteVerb verb, PaletteContext context);
    Subscription onChanged(Runnable listener);

    static String requireValidId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid scope ID: " + id);
        return id;
    }
}
