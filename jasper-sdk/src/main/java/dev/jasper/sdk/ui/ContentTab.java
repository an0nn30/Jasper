package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** App-owned document tab, also a dynamic dialog/picker owner. All methods except close use the UI thread.
 * Moving retains content, selection/undo state and this handle. Closed handles do not reattach content.
 * @since 0.8.5
 */
public interface ContentTab extends WindowOwner, AutoCloseable {
    /** @param value literal title, location and state */
    void setPresentation(ContentPresentation value);
    /** @param commands immutable snapshot of active-content commands */
    void setCommands(List<ContentCommand> commands);
    /** @param items immutable snapshot of status items */
    void setStatus(List<ContentStatus> items);
    /** @param focus UI-thread callback focusing the editable content */
    void setFocusTarget(Runnable focus);
    /** Selects and raises the current host, focusing its content. */
    void select();
    /**
     * A new child window takes the tab and its content. A child window's only tab, a tab with a modal prompt open and a
     * tab being closed stay where they are.
     *
     * @since 0.20.0
     */
    void moveToNewWindow();
    /**
     * Move to Main Window: a new tab of the main window, at the end of its tab strip, takes the tab and its content, the
     * main window restored first when none is open. A tab the main window holds, a tab with a modal prompt
     * open and a tab being closed stay where they are.
     *
     * @since 0.20.0
     */
    void moveToMainWindow();
    /**
     * The window that holds the tab; {@link dev.jasper.sdk.terminal.WindowHandle#isMain()} tells the main window from a
     * child window.
     *
     * @return the window, or the last window that held the tab once it is closed; a window that is not open, with the id
     *         {@code new UUID(0, 0)}, for a tab waiting for the main window to open or one no window could take back
     * @since 0.20.0
     */
    dev.jasper.sdk.terminal.WindowHandle window();
    /** @return whether the content is selected in its host */
    boolean isActive();
    /** @return whether the handle has been disposed */
    boolean isClosed();
    /** @return whether closing was accepted; false keeps content intact */
    CompletableFuture<Boolean> requestClose();
    /** @param listener activation callback
     * @return removable registration */
    Subscription onActive(Consumer<Boolean> listener);
    /** @param listener once-only disposal callback
     * @return removable registration */
    Subscription onClosed(Runnable listener);
    /** Disposes without asking, for plugin shutdown; user close uses requestClose. */
    @Override void close();
}
