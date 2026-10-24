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
    /**
     * Sets what the tab shows: its title, preceded by a dot while dirty and followed by the detail, its icon and, as its
     * tooltip, the location; while the tab is active, the window's title and header context follow it. Ignored once
     * the tab is closed.
     *
     * @param value literal title, location and state
     */
    void setPresentation(ContentPresentation value);
    /**
     * Replaces the content's commands. They appear in the tab's context menu; while the tab is active, a command's
     * shortcut runs it ahead of the Jasper actions bound to the same key, the command palette's shortcuts excepted (a
     * disabled command's shortcut does nothing), and Jasper's Copy, Paste, Find, Find Next, Find Previous, Increase
     * Font Size, Decrease Font Size and Reset Font Size run the enabled command with the id {@code copy},
     * {@code paste}, {@code find}, {@code find-next}, {@code find-previous}, {@code font-bigger}, {@code font-smaller}
     * or {@code font-reset}. In a text field, the shortcut of a {@code copy} or {@code paste} command keeps the field's
     * own clipboard behaviour. Ignored once the tab is closed.
     *
     * @param commands immutable snapshot of active-content commands
     */
    void setCommands(List<ContentCommand> commands);
    /**
     * Replaces the items the main window's status bar shows while this tab is active there; a child window has no
     * status bar. Ignored once the tab is closed.
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
     * Move Pane to New Tab: the pane showing this item leaves its tab for a new tab right after it,
     * which shows; nothing closes or leaves the window. Its tab's only pane, an item with a modal prompt open and an
     * item being closed stay where they are ({@link #mayMoveToNewTab()}).
     *
     * @since 0.21.0
     */
    void moveToNewTab();
    /**
     * Whether {@link #moveToNewTab()} would move this item, for a menu entry that shows disabled otherwise.
     *
     * @return false for its tab's only pane, an item with a modal prompt open, an item being closed and a closed one
     * @since 0.21.0
     */
    boolean mayMoveToNewTab();
    /**
     * Move Pane to Tab's choices: the window's other tabs, in strip order, for a menu the
     * plugin builds, which shows disabled while there is none. A choice carries its tab's title, the title of the tab's
     * shown item, only when the plugin holds {@code terminal.observe}, which reads any tab's title, or when that item is
     * this plugin's own content; otherwise its title is empty and its label reads {@code Tab 2}. Its number is the
     * tab's place in the strip either way.
     *
     * @return the choices; none while the window has one tab, and none for an item with a modal prompt open, an item
     *     being closed or a closed one
     * @since 0.21.0
     */
    List<TabChoice> tabChoices();
    /**
     * Move Pane to Tab: the pane showing this item moves to the right of the active pane of the
     * tab numbered {@code number} in its window's strip, which then shows, this item focused; no item closes or leaves the
     * window; its old tab closes if this was its last pane, and a maximized pane of the tab it joins stops being
     * maximized. The number of its own tab, a number no tab has, and an item {@link #tabChoices()} offers none for move
     * nothing.
     *
     * @param number a tab's place in its window's strip, from 1, as {@link TabChoice#number()} gives it
     * @since 0.21.0
     */
    void moveToTab(int number);
    /**
     * Split with a choice beside this item: selects it, then opens {@code choice} in a new pane on
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
    /**
     * Whether the tab is active: the item of the focused pane in its window's shown tab.
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
     * Calls {@code listener} with true when the tab becomes its window's active item and with false when it stops
     * being active, including when it moves or closes.
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
