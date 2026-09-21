package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;

/**
 * What plugin windows and dialogs share. The application owns the frame, title bar, icon, menu bar,
 * theme tracking and saved bounds; the plugin fills the content. UI thread only. {@link #close()}
 * closes at once, without consulting {@link #onClosing} guards, and is idempotent.
 */
public interface WindowSurface extends Subscription {
    /**
     * Replaces the content.
     *
     * @param content ordinary Swing components
     */
    void setContent(JComponent content);

    /** Shows the window. For a modal dialog this returns after the dialog has closed. */
    void show();

    /** Brings the window to the front, restoring it if minimized. */
    void toFront();

    /**
     * Changes the title.
     *
     * @param title non-blank title
     */
    void setTitle(String title);

    /**
     * Adds a guard consulted when the user tries to close the window. Any guard returning false keeps
     * it open; a guard that throws is treated as allowing the close. Quit does not consult guards.
     *
     * @param guard returns whether the window may close
     * @return the registration
     */
    Subscription onClosing(BooleanSupplier guard);

    /**
     * Runs the handler once, after the window has closed for any reason.
     *
     * @param handler cleanup
     * @return the registration
     */
    Subscription onClosed(Runnable handler);
}
