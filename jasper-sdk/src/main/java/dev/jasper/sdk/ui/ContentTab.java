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
    /**
     * Sets what the tab shows: its title, preceded by a dot while dirty and followed by the detail, its icon and, as its
     * tooltip, the location; while the tab is selected, the window's title and header context follow it. Ignored once
     * the tab is closed.
     *
     * @param value literal title, location and state
     */
    void setPresentation(ContentPresentation value);
    /**
     * Replaces the content's commands. They appear in the tab's context menu; while the tab is selected, a command's
     * shortcut runs it ahead of the Jasper actions bound to the same key, the command palette's shortcuts excepted (a
     * disabled command's shortcut does nothing), and Jasper's Copy, Paste, Find, Find Next and Find Previous run the enabled command with the id {@code copy},
     * {@code paste}, {@code find}, {@code find-next} or {@code find-previous}, and its Increase, Decrease and Reset Font
     * Size the one with the id {@code font-bigger}, {@code font-smaller} or {@code font-reset}. In a text field, the shortcut of a
     * {@code copy} or {@code paste} command keeps the field's own clipboard behaviour. Ignored once the tab is closed.
     *
     * @param commands immutable snapshot of active-content commands
     */
    void setCommands(List<ContentCommand> commands);
    /**
     * Replaces the items the window's status bar shows while this tab is selected. Ignored once the tab is closed.
     *
     * @param items immutable snapshot of status items
     */
    void setStatus(List<ContentStatus> items);
    /**
     * Sets what Jasper runs to focus the content, as when its tab is selected or its window is brought to the front; by
     * default Jasper focuses the content's view.
     *
     * @param focus UI-thread callback focusing the editable content
     */
    void setFocusTarget(Runnable focus);
    /** Selects and raises the current host, focusing its content. */
    void select();
    /**
     * Moves the content to another window and selects it there, as the tab's Open in Separate Window and Move to Main
     * Window do. Nothing happens when it is already in such a window, is closed, has a file chooser or a modal dialog
     * it owns open, or awaits a close decision.
     *
     * @param separate true moves to a new separate workspace; false moves to the last main workspace
     */
    void move(boolean separate);
    /**
     * Whether the content is in a separate workspace window rather than a main one.
     *
     * @return whether the content lives in a separate window
     */
    boolean isSeparate();
    /**
     * Whether the content's tab is the selected tab of its window.
     *
     * @return whether the content is selected in its host
     */
    boolean isActive();
    /**
     * Whether the content has closed: by the user, through {@link #requestClose()} or {@link #close()}, with its window,
     * or when the plugin stopped.
     *
     * @return whether the handle has been disposed
     */
    boolean isClosed();
    /**
     * Asks to close the content as Close Tab does: it closes unless this plugin's
     * {@link Windows#onTabsClosing(java.util.function.BiFunction) closing guard} refuses, another close decision is
     * pending, or a file chooser or modal dialog it owns is open.
     *
     * @return whether closing was accepted; false keeps content intact
     */
    CompletableFuture<Boolean> requestClose();
    /**
     * Calls {@code listener} with true when the tab becomes its window's selected tab and with false when it stops
     * being selected, including when it moves or closes.
     *
     * @param listener activation callback
     * @return removable registration
     */
    Subscription onActive(Consumer<Boolean> listener);
    /**
     * Calls {@code listener} once when the content closes; on a closed handle it is never called.
     *
     * @param listener once-only disposal callback
     * @return removable registration
     */
    Subscription onClosed(Runnable listener);
    /** Disposes without asking, for plugin shutdown; user close uses requestClose. */
    @Override void close();
}
