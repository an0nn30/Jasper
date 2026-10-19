package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;

/** Tool window panels. Each registered panel gets a stripe icon in the main window, the only window with tool windows. */
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
     * Toggles this plugin's registered panel in the main window, creating it lazily; a child window's request shows it.
     * The main window comes to the front. Requests from a closed window have no effect.
     * @param panelId a registered panel id owned by this plugin
     * @param window the window the request comes from, the main window or a child window
     * @throws IllegalArgumentException when the panel is not registered by this plugin
     * @throws IllegalStateException when the context is closed or the caller is off the UI thread
     * @since 0.7.2
     */
    void toggle(String panelId, WindowHandle window);
}
