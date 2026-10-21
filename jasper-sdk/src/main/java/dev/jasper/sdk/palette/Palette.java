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
     * @throws IllegalArgumentException when the id lacks the plugin's prefix or is already registered, the shortcut
     *     action is not this plugin's, or the icon names no known icon
     */
    Subscription register(PaletteScope scope);

    /**
     * Opens the palette in {@code window} on {@code scopeId}. {@code query} replaces the query text and
     * {@code rowId} selects the first row with that id. When that scope is already showing, the command palette
     * dismisses instead and applies neither, while the Run window stays open, focuses its field and applies both.
     * Unknown scopes are ignored, and so is a call for a window that is not the active one while its palette is
     * closed.
     *
     * @param window  the window
     * @param scopeId any registered scope, including {@code jasper.commands} and other plugins' scopes, or
     *                {@code jasper.all} for no scope chosen
     * @param query   the query text to show
     * @param rowId   the row to select
     */
    void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId);
}
