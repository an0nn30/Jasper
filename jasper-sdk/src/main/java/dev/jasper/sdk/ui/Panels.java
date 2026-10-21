package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;

/** Tool window panels. Each registered panel gets a stripe button in the main window, the only window with tool windows. */
public interface Panels {
    /**
     * Registers a panel on the UI thread.
     *
     * @param spec what the panel is
     * @param factory builds the panel lazily, in the main window
     * @return the registration; closing it removes the panel
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription register(PanelSpec spec, PanelFactory factory);

    /**
     * Toggles this plugin's registered panel in the main window, creating it lazily; a child window's request shows it and
     * brings the main window to the front. Requests from a closed window have no effect.
     * Called from the panel's own toggle action ({@link PanelSpec#withToggleActionId}), as its handler runs, under a
     * look with flush tool windows a toggle that shows the panel also makes it the active tool window, as a stripe click
     * does, and a toggle of a showing panel that is not the active tool window activates it instead of hiding it
     * (since 0.18.2).
     * @param panelId a registered panel id owned by this plugin
     * @param window the window the request comes from, the main window or a child window
     * @throws IllegalArgumentException when the panel is not registered by this plugin
     * @throws IllegalStateException when the context is closed or the caller is off the UI thread
     * @since 0.7.2
     */
    void toggle(String panelId, WindowHandle window);
}
