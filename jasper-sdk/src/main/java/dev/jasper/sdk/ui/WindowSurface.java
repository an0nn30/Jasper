package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import java.util.function.BooleanSupplier;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * What plugin windows and dialogs share. The application owns the frame, title bar, icon, menu bar,
 * theme tracking and saved bounds (overlays use their owner’s chrome); the plugin fills the content. UI thread only. {@link #close()}
 * closes at once, without consulting {@link #onClosing} guards, and is idempotent.
 */
public interface WindowSurface extends Subscription {
    /**
     * Replaces the content.
     *
     * @param content ordinary Swing components
     */
    void setContent(JComponent content);

    /** Shows the surface; overlays return immediately. For a modal dialog this returns after the dialog has closed. */
    void show();

    /** Brings the window to the front, restoring it if minimized; overlays focus their content. */
    void toFront();

    /**
     * Changes the title.
     *
     * @param title non-blank title
     */
    void setTitle(String title);

    /**
     * Opens a native chooser for one existing file, owned by this window or dialog.
     * Blocks on the UI thread while the platform pumps events. If the owner closes during
     * selection, the result is discarded. This does not read the selected file.
     *
     * @param title non-blank chooser title
     * @param initialPath optional file or directory to start from; never null
     * @return an absolute normalized path, or empty on cancellation or owner closure
     * @throws IllegalStateException if the plugin is stopped or this surface is not shown or is closed
     * @since 0.7.1
     */
    Optional<Path> chooseFile(String title, Optional<Path> initialPath);

    /**
     * Adds a guard consulted when the user tries to close the window. Overlays have no implicit user-close action. Any guard returning false keeps
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
