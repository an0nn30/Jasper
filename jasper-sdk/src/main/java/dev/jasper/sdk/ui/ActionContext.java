package dev.jasper.sdk.ui;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.net.URI;
import java.util.Optional;

/** Where an action was invoked. */
public interface ActionContext {
    /**
     * The window whose palette, menu, toolbar, status bar or shortcut invoked the action.
     *
     * @return the window
     */
    WindowHandle window();

    /**
     * The pane the action concerns: the pane under a terminal context menu, otherwise the window's focused pane.
     *
     * @return the pane, or empty when the window has none
     */
    Optional<PaneHandle> pane();

    /**
     * The web address selected in the pane when the action was chosen from a {@link PluginMenu#addForWebAddress} item of
     * the terminal's right-click menu. An action invoked any other way (a plain item of the same menu, a shortcut, the
     * palette) receives none, even while a web address is selected. It needs no capability: the user chose the item with
     * that selection.
     *
     * @return the address, or empty
     * @since 0.23.0
     */
    default Optional<URI> webAddress() { return Optional.empty(); }
}
