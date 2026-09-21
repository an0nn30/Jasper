package dev.jasper.sdk.ui;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
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
}
