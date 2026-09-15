package dev.jasper.app;

import java.util.List;
import javax.swing.Icon;

/**
 * One kind of searchable thing. The palette shows exactly one scope at a time; a scope sees only its own
 * query and produces only its own rows. Every method runs on the EDT; search and availability do no I/O.
 */
interface PaletteScope {
    String COMMANDS_ID = "jasper.commands";
    String HISTORY_ID = "jasper.history";

    String id();
    String label();
    default Icon icon() { return null; }
    default String description() { return ""; }
    String placeholder();
    default List<String> aliases() { return List.of(); }
    List<PaletteVerb> verbs();
    default int preferredRows() { return 5; }
    default boolean monospaceRows() { return false; }
    /** The scope became active in an open palette; a scope may ask its index for a background refresh here. */
    default void activated(PaletteContext context) {}
    PaletteResults search(String query, PaletteContext context);
    /** Rechecked immediately before execution; a false answer refreshes the list instead of executing. */
    default boolean available(PaletteRow row, PaletteContext context) { return row.enabled(); }
    void execute(PaletteRow row, PaletteVerb verb, PaletteContext context);
    CommandRegistry.Subscription onChanged(Runnable listener);

    static String requireValidId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid scope ID: " + id);
        return id;
    }
}
