package dev.jasper.sdk.palette;

import dev.jasper.sdk.Subscription;
import java.util.Optional;

/**
 * One kind of searchable thing, implemented by a plugin and registered through {@link Palette}.
 * Every method runs on the UI thread and must do no I/O; a scope that reads files keeps an index it
 * refreshes in the background and publishes through its {@link #onChanged} listener. The host contains
 * every call: a failing method yields no rows, an unavailable row, no step or nothing done.
 */
public interface PaletteScope {
    /**
     * Read once, at registration.
     *
     * @return what this scope is
     */
    ScopeSpec spec();

    /**
     * Answers a query. An empty query asks for the rows to show before anything is typed: the scope's own list while it
     * is chosen, which may carry a section label, and rows that top up the palette's suggestions when the scope is
     * offered among recent items.
     *
     * @param query   the raw query text
     * @param context the window, origin pane and row budget
     * @return at most {@code context.maxResults()} rows
     */
    PaletteResults search(String query, PaletteQuery context);

    /**
     * Rechecked immediately before a verb runs; {@code false} refreshes the list instead of executing.
     *
     * @param row     a row this scope returned
     * @param verb    one of this scope's verbs
     * @param context the query context
     * @return whether the verb may run now
     */
    default boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) { return row.enabled(); }

    /**
     * Consulted before {@link #execute}: a present step is shown as a form instead of running the verb,
     * and {@code execute} is not called for that action; the step's completion does the work.
     *
     * @param row     a row this scope returned
     * @param verb    the verb chosen
     * @param context the query context
     * @return a form to fill first, or empty to run the verb at once
     */
    default Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) { return Optional.empty(); }

    /**
     * Runs a verb on a row.
     *
     * @param row     a row this scope returned
     * @param verb    the verb chosen
     * @param context the query context
     */
    void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context);

    /**
     * Called when the palette opens with this scope chosen or the user chooses it, and when the palette opens with no
     * scope chosen or returns to that while this scope is in All; a scope may ask its index for a background refresh.
     *
     * @param context the query context
     */
    default void activated(PaletteQuery context) { }

    /**
     * Tells the palette its results may have changed; an open query is re-run. Each window's palette registers a
     * listener when it starts showing this scope's rows and closes it when it stops, so several may be registered at
     * once.
     *
     * @param listener called on the UI thread
     * @return the registration
     */
    Subscription onChanged(Runnable listener);
}
