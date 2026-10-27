package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;

/**
 * Tool window panels. Every window has a dock, status bar and toolbar: each registered panel gets a stripe button in every
 * window, and each window's dock shows its own instance of the panel, docked there or, as the user chooses, in a window of
 * its own.
 */
public interface Panels {
    /**
     * Registers a panel on the UI thread.
     *
     * @param spec what the panel is
     * @param factory builds the panel lazily, when it first shows
     * @return the registration; closing it removes the panel
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription register(PanelSpec spec, PanelFactory factory);

    /**
     * Toggles this plugin's registered panel in {@code window}'s dock, building that window's instance lazily. Requests
     * from a closed window have no effect.
     * Called from the panel's own toggle action ({@link PanelSpec#withToggleActionId}), as its handler runs, under a
     * look with flush tool windows a toggle that shows the panel also makes it the active tool window, as a stripe click
     * does, and a toggle of a showing panel that is not the active tool window activates it instead of hiding it
     * (since 0.18.2).
     * @param panelId a registered panel id owned by this plugin
     * @param window the window whose dock shows or hides the panel
     * @throws IllegalArgumentException when the panel is not registered by this plugin
     * @throws IllegalStateException when the context is closed or the caller is off the UI thread
     * @since 0.7.2
     */
    void toggle(String panelId, WindowHandle window);

    /**
     * Shows this plugin's registered panel in {@code window}'s dock, or in its own window when it is detached there,
     * building that window's instance on first show. A closed window has no effect.
     * @param panelId a registered panel id owned by this plugin
     * @param window the window whose dock shows the panel
     * @throws IllegalArgumentException when the panel is not registered by this plugin
     * @throws IllegalStateException when the context is closed or the caller is off the UI thread
     * @since 0.23.0
     */
    void show(String panelId, WindowHandle window);
}
