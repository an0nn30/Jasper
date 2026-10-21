package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** App-owned document tab, also a dynamic dialog/picker owner. All methods except close use the UI thread.
 * Moving retains content, selection/undo state and this handle. Closed handles do not reattach content.
 * The handle carries the owning plugin's authority over the tab, its {@code terminal.open} for Terminal Right and Down
 * ({@link #split}) included, so a plugin hands other plugins {@link #window()}, not the tab.
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
    /**
     * How the tab comes back when Jasper restores its windows, in place of what its {@link ContentSpec#restore()} said:
     * a document saved under a new name names its new file. Empty: the tab is not saved.
     *
     * @param state the restore state, or empty
     * @since 0.21.0
     */
    void setRestore(java.util.Optional<dev.jasper.sdk.RestoreState> state);
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
     * Move Pane to New Tab (tabs and panes spec 9): the pane showing this item leaves its tab for a new tab right after it,
     * which shows; nothing closes or leaves the window. Its tab's only pane, a tab with a modal prompt open and a tab
     * being closed stay where they are ({@link #mayMoveToNewTab()}).
     *
     * @since 0.21.0
     */
    void moveToNewTab();
    /**
     * Whether {@link #moveToNewTab()} would move this item, for a menu entry that shows disabled otherwise.
     *
     * @return false for its tab's only pane, a tab with a modal prompt open, a tab being closed and a closed one
     * @since 0.21.0
     */
    boolean mayMoveToNewTab();
    /**
     * Split with a choice beside this item (tabs and panes spec 8): selects it, then opens {@code choice} in a new pane on
     * {@code direction}'s side of its pane, as the Split lists' entries do: a terminal for {@link SplitChoice#TERMINAL},
     * else the split item's view, through its action. A closed tab, or one being closed, opens nothing. Whoever calls it,
     * it runs with the owning plugin's authority over the tab, its {@code terminal.open} for Terminal Right and Down
     * included, so hand other plugins {@link #window()}, not the tab.
     *
     * @param choice one of {@link Windows#splitChoices()}
     * @param direction the new pane's side
     * @throws IllegalArgumentException when {@code choice} is not, or no longer, one of {@link Windows#splitChoices()}
     * @throws dev.jasper.sdk.MissingCapabilityException when {@code choice} is {@link SplitChoice#TERMINAL} and the plugin
     *     lacks {@code terminal.open}: checked first, as {@link dev.jasper.sdk.terminal.Terminals} checks it, so a
     *     closed tab's split is refused too
     * @since 0.21.0
     */
    void split(SplitChoice choice, dev.jasper.sdk.terminal.Direction direction);
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
