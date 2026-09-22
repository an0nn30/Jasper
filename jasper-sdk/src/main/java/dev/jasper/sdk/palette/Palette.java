package dev.jasper.sdk.palette;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Optional;

/**
 * The command palette as a plugin sees it. Both methods need {@code palette.contribute}, the UI thread
 * and an open context; implemented by the application and by the testkit.
 */
public interface Palette {
    /**
     * Registers an application-wide scope; every window shows it. The spec is read once; its id must
     * start with the plugin's id, and its {@code shortcutActionId}, if any, must be an action this
     * plugin registered earlier.
     *
     * @param scope the scope
     * @return the registration; closing removes the scope from every window
     */
    Subscription register(PaletteScope scope);

    /**
     * Opens the palette in {@code window} on {@code scopeId}, or dismisses it when that scope is already
     * showing. {@code query} replaces the query text and {@code rowId} selects a row of the first list;
     * neither is applied when dismissing. Unknown scopes are ignored.
     *
     * @param window  the window
     * @param scopeId any registered scope, including {@code jasper.commands} and other plugins' scopes
     * @param query   the query text to show
     * @param rowId   the row to select
     */
    void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId);
}
