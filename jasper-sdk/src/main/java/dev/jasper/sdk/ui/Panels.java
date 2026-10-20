package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;

/** Tool window panels. Each registered panel gets a stripe icon in every window. */
public interface Panels {
    /**
     * Registers a panel on the UI thread.
     *
     * @param spec what the panel is
     * @param factory builds one instance per window, lazily
     * @return the registration; closing it removes the panel from every window
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already registered
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    Subscription register(PanelSpec spec, PanelFactory factory);

    /**
     * Toggles this plugin's registered panel in a terminal window, creating it lazily.
     * Requests for a closed window have no effect.
     * Called from the panel's own toggle action ({@link PanelSpec#withToggleActionId}), as its handler runs, a toggle
     * that shows the panel also makes it the active tool window under a look with flush tool windows, as a stripe click
     * does (since 0.18.2).
     * @param panelId a registered panel id owned by this plugin
     * @param window the terminal window
     * @throws IllegalArgumentException when the panel is not registered by this plugin
     * @throws IllegalStateException when the context is closed or the caller is off the UI thread
     * @since 0.7.2
     */
    void toggle(String panelId, WindowHandle window);
}
