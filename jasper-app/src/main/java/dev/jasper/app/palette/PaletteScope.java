package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;

import java.util.List;
import javax.swing.Icon;
import java.util.Optional;

/**
 * One kind of searchable thing. The palette shows exactly one scope at a time; a scope sees only its own
 * query and produces only its own rows. Every method runs on the EDT; search and availability do no I/O.
 */
public interface PaletteScope {
    String COMMANDS_ID = "jasper.commands";

    String id();
    String label();
    default Icon icon() { return null; }
    default String description() { return ""; }
    String placeholder();
    default List<String> aliases() { return List.of(); }
    /** One to three verbs, bound in order to Enter, Cmd/Ctrl+Enter and Shift+Enter. */
    List<PaletteVerb> verbs();
    default boolean monospaceRows() { return false; }

        /**
         * A contributed action whose shortcut, while the palette is open, switches to or dismisses this
         * scope instead of being swallowed; the built-in scopes are routed by {@code ActionId} instead.
         */
        default Optional<String> shortcutActionId() { return Optional.empty(); }
    /** The scope became active in an open palette; a scope may ask its index for a background refresh here. */
    default void activated(PaletteContext context) {}
    /** At most {@code context.maxResults()} rows; the palette is a hard-capped list, never a scrolling one. */
    PaletteResults search(String query, PaletteContext context);
    /** Rechecked immediately before execution; a false answer refreshes the list instead of executing. */
    default boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) { return row.enabled(); }
    /**
     * Consulted before {@link #execute}: a non-null step is shown in the card instead of running the verb,
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
