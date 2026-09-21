package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.function.Consumer;

/** One panel instance's place in one window. All methods are UI-thread only. */
public interface PanelHost {
    /**
     * The window that holds this instance.
     *
     * @return the window
     */
    WindowHandle window();

    /** Shows the panel in this window, hiding whatever else occupied its region. */
    void show();

    /** Hides the panel in this window. */
    void hide();

    /**
     * Whether the panel is showing in this window.
     *
     * @return true while visible
     */
    boolean visible();

    /**
     * Runs the handler after each change of visibility in this window.
     *
     * @param handler receives the new visibility
     * @return the registration
     */
    Subscription onVisibility(Consumer<Boolean> handler);

    /**
     * Runs the handler when this instance is discarded because its window closed or the panel was removed.
     *
     * @param handler cleanup for this instance
     * @return the registration
     */
    Subscription onClosed(Runnable handler);
}
