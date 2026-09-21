package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;

/** Side and bottom panels. Each registered panel gets a rail icon in every window. */
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
}
