package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.function.Consumer;

/** The panel's place in the main window. All methods are UI-thread only. */
public interface PanelHost {
    /**
     * The main window, which holds the panel.
     *
     * @return the window
     */
    WindowHandle window();

    /**
     * Replaces this instance's header metadata and icon actions. Callbacks become inert when the instance closes.
     * @param header the header description
     * @since 0.14.2
     */
    void setHeader(PanelHeader header);

    /** Shows the panel, hiding whatever else occupied its slot. */
    void show();

    /** Hides the panel. */
    void hide();

    /**
     * Whether the panel is showing.
     *
     * @return true while visible
     */
    boolean visible();

    /**
     * Runs the handler after each change of the panel's visibility.
     *
     * @param handler receives the new visibility
     * @return the registration
     */
    Subscription onVisibility(Consumer<Boolean> handler);

    /**
     * Runs the handler when this instance is discarded because the main window closed or the panel was removed.
     *
     * @param handler cleanup for this instance
     * @return the registration
     */
    Subscription onClosed(Runnable handler);
}
