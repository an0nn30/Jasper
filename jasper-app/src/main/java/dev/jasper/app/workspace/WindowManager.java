package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.layout.TabRestore;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import java.awt.AWTEvent;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;

/**
 * Jasper's windows (full windows spec 5): any number of {@link JasperWindow}s, all of one kind, in the order they opened
 * and the order they came to the front. It makes every window, in one place ({@link #open}); it sends anything with no
 * window of its own (a plugin's view without an owner, a handoff) to the most recently active window, restoring the saved
 * windows first when none is open ({@link #ensureWindow}); it closes a window a move leaves without a tab, and every
 * window on Quit. A window whose last tab closes keeps its empty workspace. The application owns it and asks it every
 * window question. EDT only.
 */
public final class WindowManager {
    /**
     * A window about to open.
     *
     * @param empty whether it starts without a tab, for a tab that moves into it or a restore
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @param trigger what asked for it, for its start-up timing line
     * @param bounds where the window's workspace opens on screen, a torn-out tab's (workspace tabs plan 5); empty
     *     for where the platform puts a new window, or where a restored window's saved place puts it
     * @param cursor where the cursor released that tab on screen, present with the bounds: the window opens on the
     *     display there
     */
    public record Opening(boolean empty, Path directory, String trigger, Optional<Rectangle> bounds, Optional<Point> cursor) {
        /** Requires the trigger, and the bounds with the cursor or neither. */
        public Opening {
            Objects.requireNonNull(trigger, "trigger");
            Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(cursor, "cursor");
            if (bounds.isPresent() != cursor.isPresent())
                throw new IllegalArgumentException("A window's bounds come with the cursor");
        }
    }

    /**
     * How a plugin stands when the windows restore its tabs (workspace tabs spec 13): its name, and whether it runs or
     * why not, which a placeholder says.
     *
     * @param name its display name; its id when it is not installed
     * @param kind whether it runs, or why not
     */
    public record PluginState(String name, Kind kind) {
        /** Whether a plugin runs, or why not. */
        public enum Kind {
            /** It started at this launch: its restorers rebuild its tabs. */
            RUNNING,
            /** No plugin with that id is installed. */
            NOT_INSTALLED,
            /** The user turned it off. */
            TURNED_OFF,
            /** The user marked it for removal at the next launch. */
            MARKED_FOR_REMOVAL,
            /** It is off because Jasper runs in safe mode. */
            SAFE_MODE,
            /** It waits for the user's review of what it asks to do. */
            NEEDS_CONSENT,
            /** It could not start at this launch. */
            FAILED
        }

        /** Requires both values. */
        public PluginState { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); }
    }

    /**
     * A window was shown, minimized or restored.
     *
     * @param window the window
     * @param showing whether its frame shows
     * @param iconified whether it is minimized
     */
    public record State(JasperWindow window, boolean showing, boolean iconified) { }

    /**
     * What the application gives the window manager.
     *
     * @param content composes a window's content for an opening, wired to the application; null while Jasper quits, and
     *     then no window opens
     * @param frames builds a window's native frame, or a headless test's stand-in
     * @param opened a window has opened and shows
     * @param activated a window came to the front
     * @param closed a window has closed, or failed to open
     * @param state a window was shown, minimized or restored
     * @param closing a window is about to close, its content still whole; never for one that failed to open
     * @param closingAll every window is about to close (Quit, going to the background), before any
     *     does, and the workspace store has saved them as they are
     */
    public record Hooks(Function<Opening, WindowContent> content, Function<JasperWindow, WindowShell> frames,
                        Consumer<JasperWindow> opened, Consumer<JasperWindow> activated, Consumer<JasperWindow> closed,
                        Consumer<State> state, Consumer<JasperWindow> closing, Runnable closingAll) {
        /** Requires every hook. */
        public Hooks {
            Objects.requireNonNull(content, "content"); Objects.requireNonNull(frames, "frames"); Objects.requireNonNull(opened, "opened");
            Objects.requireNonNull(activated, "activated"); Objects.requireNonNull(closed, "closed"); Objects.requireNonNull(state, "state");
            Objects.requireNonNull(closing, "closing"); Objects.requireNonNull(closingAll, "closingAll");
        }
    }

    private static final System.Logger LOG = System.getLogger(WindowManager.class.getName());
    /** Why no window opens for a request: every window is closing, or Jasper quits. */
    private static final String CLOSING = "Every window is closing or Jasper is quitting";
    private final Contributions model;
    private final Hooks hooks;
    /** The open windows, in the order they opened. */
    private final List<JasperWindow> windows = new ArrayList<>();
    /** The windows that came to the front, the last one first. */
    private final List<JasperWindow> activations = new ArrayList<>();
    /**
     * The one tab drag of every window this manager opens (workspace tabs spec 12): it reaches them all, top first, and
     * carries what leaves a window to this manager, which each window knows (parent tabs spec 5).
     */
    private final TabDrag tabDrag = new TabDrag(() -> windowsByActivation().stream().map(JasperWindow::content).toList());
    /**
     * Tells the tab drag of each of Jasper's other windows (Settings, About, a plugin's dialog) coming to the front, so
     * that the one on top under the cursor takes or refuses a drop (workspace plan 9a Rulings 21): installed when a
     * window of this manager first comes to the front, gone when its last window closes.
     */
    private final AWTEventListener fronts = event -> {
        if (event.getID() == WindowEvent.WINDOW_ACTIVATED) tabDrag.activated(((WindowEvent) event).getWindow());
    };
    private boolean watchingFronts;
    private int openings;
    /** While {@link #ensureWindow} restores the saved windows: one restore at a time (workspace plan 4a Rulings 24). */
    private boolean restoring;
    /** Where the windows are saved; none in a test that does not save. */
    private WorkspaceStore store;
    /** How each plugin stands, by id: without the application's answer, none is installed. */
    private Function<String, PluginState> plugins = id -> new PluginState(id, PluginState.Kind.NOT_INSTALLED);
    /** The items the size limit has left out of a save so far, each logged once (workspace plan 6b Rulings 16). */
    private final Set<TabId> leftOut = new HashSet<>();
    /** While {@link #closeAll()} runs: no window opens, so none outlives the others and no fresh one opens. */
    private boolean closingAll;
    /** The window {@link #openWindow} opened last, until another window comes to the front: New Window cascades from it. */
    private JasperWindow lastOpened;

    /**
     * The windows of the application whose contributions are {@code model}: its tab registry places a plugin's view
     * through this manager.
     *
     * @param model the application's contributions
     * @param hooks what the application gives the manager
     */
    public WindowManager(Contributions model, Hooks hooks) {
        this.model = Objects.requireNonNull(model, "model");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        // A plugin's view opened with no window goes to the most recently active window, the saved windows restored first
        // when none is open (full windows spec 5.2); while they are being restored it waits, until ensureWindow says they
        // opened or failed. A window a move empties closes after the move.
        model.tabs().windows(new TabRegistry.Windows(() -> restoring ? null : ensureWindow("plugin", null).map(JasperWindow::id)
            .orElseThrow(() -> new IllegalStateException(CLOSING)), this::moveToNewWindow, this::movedOut, this::emptyWindow));
        // A plugin's tab giving new restore state changes what is saved (workspace tabs spec 13).
        model.tabs().onRestoreChanged(this::changed);
    }

    /**
     * How the plugins stand (workspace tabs spec 13): a saved tab of a plugin that runs is rebuilt by its restorer, else it
     * is a placeholder that says why, with Retry while its plugin runs; asked again whenever a placeholder shows or its
     * Retry is pressed.
     *
     * @param states a plugin's standing by its id
     */
    public void plugins(Function<String, PluginState> states) { plugins = Objects.requireNonNull(states, "states"); }

    /**
     * Where the windows are saved and restored from (workspace tabs spec 13): the application gives it once it knows the
     * Jasper home. Without it nothing is saved and every window opens fresh.
     *
     * @param value the store
     */
    public void workspace(WorkspaceStore value) { store = Objects.requireNonNull(value, "value"); }

    /** Something {@code workspace.toml} saves changed: a layout, a tab's name or directory, where a window is. */
    void changed() { if (store != null) store.changed(); }

    /** @return every open window, in the order they opened */
    public List<JasperWindow> windows() { return List.copyOf(windows); }

    /** @return whether no window is open */
    public boolean isEmpty() { return windows.isEmpty(); }

    /** @return how many windows are being opened right now: from the start of an opening until it shows or fails */
    public int opening() { return openings; }

    /**
     * The open window with this id.
     *
     * @param id a window's id, or null
     * @return the window, while it is open
     */
    public Optional<JasperWindow> window(UUID id) {
        if (id == null) return Optional.empty();
        return windows().stream().filter(window -> window.id().equals(id)).findFirst();
    }

    /**
     * @return every open window, the one that came to the front last first; those that never did follow in the order of
     *     {@link #windows()}: the window on top where windows overlap, for a drop (workspace tabs plan 5)
     */
    public List<JasperWindow> windowsByActivation() {
        var ordered = new ArrayList<JasperWindow>();
        for (JasperWindow window : activations) if (!window.closed()) ordered.add(window);
        for (JasperWindow window : windows()) if (!ordered.contains(window)) ordered.add(window);
        return List.copyOf(ordered);
    }

    /** @return the tab drag every window of this manager shares */
    TabDrag tabDrag() { return tabDrag; }

    /**
     * @return the open window that came to the front last; else the one opened last; empty while none is open, or every
     *     open window is closing or has not shown yet
     */
    public Optional<JasperWindow> lastActive() {
        for (JasperWindow window : activations) if (!window.closed()) return Optional.of(window);
        for (JasperWindow window : windows.reversed()) if (!window.closed() && window.shell() != null) return Optional.of(window);
        return Optional.empty();
    }

    /**
     * The most recently active window, the saved windows restored first when none is open (full windows spec 5.2: every
     * request with no window of its own): as the store has them, each where it was with its tabs, splits and panes, the
     * first in front; else a new window with one terminal tab. The tabs a plugin asked for while they were being restored
     * open in the first once it shows, or go with it when it fails to open, their failures under its own.
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new window's terminal tab starts, or, when the windows are restored, where a terminal
     *     starts in a new tab of the first after its restored ones; ignored while a window is open; null for where a new
     *     shell starts, or no new tab
     * @return the window; empty while Jasper quits, or every window closes
     * @throws IllegalStateException when asked again while the saved windows are still being restored
     */
    public Optional<JasperWindow> ensureWindow(String trigger, Path directory) {
        requireEdt();
        Optional<JasperWindow> open = lastActive();
        if (open.isPresent()) return open;
        // A window that is closing is not absent: none opens while the last ones go, nor while every window closes.
        if (!windows.isEmpty() || closingAll) return Optional.empty();
        if (restoring) throw new IllegalStateException("The windows are still being restored");
        restoring = true;
        Optional<JasperWindow> opened;
        try {
            opened = restore(trigger, directory);
        } catch (RuntimeException | Error failure) {
            // Still restoring: a waiting tab's close listener cannot ask for another restore.
            model.tabs().windowFailed(failure);
            throw failure;
        } finally {
            restoring = false;
        }
        // A window its own opening closed again leaves the waiting tabs to the next one.
        opened.filter(window -> !window.closed()).ifPresent(window -> model.tabs().windowOpened(window.id()));
        return opened;
    }

    /**
     * The windows as the store has them (workspace tabs spec 13; tabs and panes spec 12), in file order, each where it was
     * with its tabs, splits and panes and its items, the first in front; with nothing to restore, or a first window that
     * cannot be restored, a new window with one terminal tab in {@code directory}. A restored first window opens
     * {@code directory} in a new tab after its own. A window that cannot be restored is not lost: the store keeps the saved
     * windows as {@code workspace.toml.bad} and says so (spec 15). The store is read here, after {@link #ensureWindow}'s
     * checks, since reading it ends its hold.
     */
    private Optional<JasperWindow> restore(String trigger, Path directory) {
        WorkspaceFile.Loaded loaded = store == null ? new WorkspaceFile.Loaded.Fresh(Optional.empty()) : store.load();
        if (!(loaded instanceof WorkspaceFile.Loaded.Restore restore)) return open(new Opening(false, directory, trigger, Optional.empty(), Optional.empty()));
        WorkspaceFile.Snapshot saved = restore.snapshot();
        boolean[] restored = new boolean[1];
        Optional<JasperWindow> opened = open(new Opening(true, null, trigger, Optional.empty(), Optional.empty()), window -> {
                try {
                    window.restore(saved.windows().getFirst());
                    restored[0] = true;
                } catch (RuntimeException | Error failure) {
                    // It opens as a new window instead, never discarded: the closed hook of the last window ends a Jasper
                    // that is not resident. An Error too: the saved windows are kept aside before this window's first save
                    // replaces them, so the next start does not meet it again (workspace plan 6a Rulings 20). Its restore
                    // closed what it made.
                    LOG.log(System.Logger.Level.WARNING, "Could not restore the first saved window; it opens as a new one", failure);
                    keepAside(saved);
                    window.content().newTab(directory);
                }
                return true;
            });
        if (opened.isEmpty() || !restored[0]) return opened;
        // The first window has shown: from here a failure, an Error too, costs only what failed, never the window, nor the
        // tabs that wait for it (workspace plan 6a Rulings 41).
        boolean whole = true;
        int back = 0;
        for (WorkspaceFile.WindowRecord record : saved.windows().subList(1, saved.windows().size())) {
            try {
                if (restoreLater(record).isPresent()) back++;
            } catch (RuntimeException | Error failure) {
                // A window that cannot come back costs only itself, and the saved windows are kept.
                LOG.log(System.Logger.Level.WARNING, "Could not restore a saved window", failure);
                whole = false;
            }
        }
        if (!whole) keepAside(saved);
        if (directory != null) {
            try { opened.get().content().newTab(directory); }
            catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not open the requested directory in a new tab", failure); }
        }
        // In front when another window came back after it (workspace plan 6a Rulings 12), and so the most recently active
        // until another comes to the front: a request with no window of its own goes to it (full windows spec 5.2).
        if (back > 0) {
            activations.remove(opened.get());
            activations.addFirst(opened.get());
            try { opened.get().toFront(); }
            catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not bring the first restored window to the front", failure); }
        }
        return opened;
    }

    /**
     * A saved window after the first, where it was with its tabs and items; one with no item that can come back is left
     * out, discarded unshown, and so is one whose failing restore emptied it before its frame exists. Its timing line says
     * {@code restore}, which no benchmark reads as a menu's window (workspace plan 6a Rulings 16). Its saved place reaches
     * its frame through its placement, checked against today's displays, never as a tear-out's workspace bounds, which its
     * frame would grow by its chrome (workspace plan 6a Rulings 21).
     *
     * @return the window, once it shows; empty when it was left out
     */
    private Optional<JasperWindow> restoreLater(WorkspaceFile.WindowRecord record) {
        return open(new Opening(true, null, "restore", Optional.empty(), Optional.empty()), window -> window.restore(record) > 0);
    }

    /**
     * New Window (full windows spec 5.2): a new window with one terminal tab in {@code directory}, after the saved windows
     * are restored when none is open, so that its first save does not replace them. It opens {@link #CASCADE} pixels below
     * and right of the most recently active window, or of the window it opened last when no other came to the front
     * since, on that window's display.
     *
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @return the window; empty while Jasper quits or every window closes
     * @throws IllegalStateException when asked while the saved windows are being restored and none is open yet
     */
    public Optional<JasperWindow> openWindow(Path directory) {
        requireEdt();
        if (closingAll || !restoredFirst("menu")) return Optional.empty();
        // The window this opened last, while no other came to the front since: a repeated New Window cascades on.
        Optional<JasperWindow> source = lastOpened != null && !lastOpened.closed() ? Optional.of(lastOpened) : lastActive();
        Optional<UiState.Bounds> from = source.flatMap(window -> window.placement().bounds());
        Optional<JasperWindow> opened = open(new Opening(false, directory, "menu", Optional.empty(), Optional.empty()), window -> {
            from.ifPresent(bounds -> window.placed(new JasperWindow.Placement(Optional.of(cascaded(bounds)), 0)));
            return true;
        });
        opened.ifPresent(window -> lastOpened = window);
        return opened;
    }

    /** How far a new window sits below and right of the window it opens from (full windows spec 5.2), in unscaled pixels. */
    static final int CASCADE = 30;

    /** {@code bounds} moved {@link #CASCADE} down and right; its frame keeps it on its display ({@code WindowPlacement}). */
    static UiState.Bounds cascaded(UiState.Bounds bounds) {
        return new UiState.Bounds(bounds.x() + CASCADE, bounds.y() + CASCADE, bounds.width(), bounds.height());
    }

    /**
     * Restores the saved windows when none is open, before a new window opens beside them (Controller Ruling 4): a new
     * window alone would otherwise save over them.
     *
     * @return whether a window is open or was restored; false while Jasper quits or every window closes, as for
     *     {@link #ensureWindow}: none opens while the last ones go
     */
    private boolean restoredFirst(String trigger) {
        return ensureWindow(trigger, null).isPresent();
    }

    /**
     * Keeps the saved windows as {@code workspace.toml.bad} (workspace plan 6a Rulings 13); a failure there is logged, so
     * it never costs the window that is opening.
     */
    private void keepAside(WorkspaceFile.Snapshot saved) {
        try { store.setAside(saved); }
        catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not keep the saved windows aside", failure); }
    }

    /**
     * {@code ContentTab.moveToNewWindow()} (tabs and panes spec 13): a new full window takes {@code tab}, its content and
     * session untouched, where the platform puts a window (workspace plan 8a Rulings 6). A tab being closed or pinned
     * stays, and so does a window's only tab: that window already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new full window; empty when the tab stayed
     */
    public Optional<JasperWindow> moveToNewWindow(Tab tab) {
        requireEdt();
        if (leaving(tab) == null) return Optional.empty();
        Optional<JasperWindow> opened = openEmpty(null, null);
        if (opened.isEmpty()) return opened;
        Throwable failure = null;
        try {
            model.tabs().move(tab, opened.get().id());
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        return settle(opened.get(), failure);
    }

    /**
     * Move Tab to New Window (tabs and panes spec 6; plan 7b's Pop Out Parent Tab): a new full window takes the parent tab
     * {@code parentTab} whole, with its id and layout, where the platform puts it (workspace plan 7b Rulings 10 and 11).
     * A window's only parent tab stays (workspace plan 8a Rulings 6), and so does a parent tab holding a tab that is
     * pinned or being closed.
     *
     * @param window one of these windows
     * @param parentTab one of its parent tabs
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<JasperWindow> moveTabToNewWindow(WindowContent window, ParentTabId parentTab) {
        return moveTabToNewWindow(window, parentTab, null, null);
    }

    /**
     * As {@link #moveTabToNewWindow(WindowContent, ParentTabId)}, the full window's workspace at {@code bounds} on screen,
     * on the display under {@code cursor}: the tab drag's tear-out.
     *
     * @param window one of these windows
     * @param parentTab one of its parent tabs
     * @param bounds where the full window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<JasperWindow> moveTabToNewWindow(WindowContent window, ParentTabId parentTab, Rectangle bounds, Point cursor) {
        requireEdt();
        if (window == null || !window.mayMoveTabToNewWindow(parentTab)) return Optional.empty();
        Optional<JasperWindow> opened = openEmpty(bounds, cursor);
        if (opened.isEmpty()) return opened;
        Throwable failure = null;
        try {
            moveParentTab(window, parentTab, opened.get().content(), 0);
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        return settle(opened.get(), failure);
    }

    /**
     * Moves the parent tab {@code parentTab} of {@code from} to {@code index} of {@code to}'s bar, whole, with its id and
     * layout (parent tabs spec 5 and 11; workspace plan 7b Rulings 6 and 10): through the tab registry, one
     * {@code TAB_MOVED} per tab, every session untouched. It shows there, raised, and a window it empties closes once the
     * move has reported itself (full windows spec 5.3). A parent tab holding a tab that is pinned or being closed stays.
     * It keeps its maximize too: spec 15 ends a maximize for a tab that joins a layout, and a parent tab moving whole
     * brings its own (Rulings 10). When part of it stays behind (a
     * listener pinned a tab mid-move, or a tab's move failed), the part that arrived takes a new id and keeps the panes it
     * landed in, so no id is in two windows (spec 4); a failure propagates afterwards.
     *
     * @param from the window holding the parent tab
     * @param parentTab the parent tab
     * @param to another window
     * @param index where it goes in {@code to}'s bar
     * @return whether any of its tabs arrived
     */
    public boolean moveParentTab(WindowContent from, ParentTabId parentTab, WindowContent to, int index) {
        requireEdt();
        if (from == null || to == null || from == to || from.closed() || to.closed())
            return false;
        WorkspaceView view = from.parentTabs().view(parentTab).orElse(null);
        if (view == null || view.isEmpty() || !view.tabs().stream().allMatch(model.tabs()::movable)) return false;
        List<Tab> moving = view.tabs();
        Throwable failure = null;
        try {
            to.receive(moving, parentTab, index, view.model());
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        // A listener may pin a later tab mid-move, or one tab's move may fail, so only part of the parent tab arrives.
        boolean moved = !to.closed()
            && to.parentTabs().view(parentTab).filter(arrived -> moving.stream().anyMatch(arrived::contains)).isPresent();
        if (moved && !from.closed()) {
            try {
                // The rest stays under its id, so the part that arrived takes a new one: no id is in two windows (spec 4).
                if (from.parentTabs().view(parentTab).filter(left -> !left.isEmpty()).isPresent())
                    to.parentTabs().reid(parentTab, ParentTabId.random());
                // The window's only parent tab is the other window's now: should the window stay, the empty workspace it
                // leaves takes a new id (Rulings 10).
                else from.parentTabs().renew(parentTab);
            } catch (RuntimeException | Error thrown) {
                // A move's own failure is not lost to the repair's.
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return moved;
    }

    /**
     * A pane drag's drop beside {@code group}, a pane of {@code to}'s shown tab (tabs and panes spec 7; workspace plan 8c
     * Rulings 12): {@code tab}'s pane leaves {@code from} for a new pane on {@code side} of it, through the tab registry,
     * so it reports {@code TAB_MOVED} once and keeps its content and session, and {@code to} shows it, raised, its
     * maximize ended (spec 13). Its tab in {@code from} goes with its last pane, and a window it empties closes after the
     * move. An item that is pinned or being closed stays.
     *
     * @param from the window holding the item
     * @param tab the item
     * @param to another window
     * @param group a pane of {@code to}
     * @param side where the item's pane goes
     * @return whether it arrived
     */
    public boolean movePane(WindowContent from, Tab tab, WindowContent to, GroupId group, Side side) {
        requireEdt();
        if (!mayMovePane(from, tab, to) || to.parentTabs().holding(group).isEmpty()) return false;
        model.tabs().move(tab, to.id(), group, side);
        return to.parentTabs().contains(tab);
    }

    /**
     * A pane drag's drop on a new-tab place of {@code to}'s strip, or on a window's empty workspace (tabs and panes spec 7;
     * workspace plan 8c Rulings 10 and 12): as {@link #movePane}, into a new tab at {@code index} of {@code to}'s strip; an
     * empty workspace takes it in its place, under its own id.
     *
     * @param from the window holding the item
     * @param tab the item
     * @param to another window
     * @param index where the new tab goes among {@code to}'s strip entries
     * @return whether it arrived
     */
    public boolean movePaneToNewTab(WindowContent from, Tab tab, WindowContent to, int index) {
        requireEdt();
        if (!mayMovePane(from, tab, to)) return false;
        if (to.parentTabs().isEmpty()) model.tabs().move(tab, to.id());
        else to.receiveAsTab(tab, index);
        return to.parentTabs().contains(tab);
    }

    /** Whether {@code tab}, an item of {@code from}, may move to {@code to}, another open window: one the registry moves. */
    private boolean mayMovePane(WindowContent from, Tab tab, WindowContent to) {
        return from != null && to != null && from != to && !from.closed() && !to.closed() && from.parentTabs().contains(tab)
            && model.tabs().movable(tab);
    }

    /**
     * A tab's entry dropped beside {@code group}, a pane of a tab of {@code to} (pane flexibility spec 5; workspace plan 9a
     * Rulings 13): every item of {@code from}'s tab {@code parentTab} moves into that tab through the tab registry, in the
     * tab's order, one {@code TAB_MOVED} each, every session untouched, and the tab takes them as one nested split on
     * {@code side} of the pane that keeps their arrangement and sizes ({@link Layout#insertBeside}); it shows there,
     * keeping its id, its maximize ended, the moved tab's active item selected and its window raised. A window the move
     * empties closes after the moves. A tab holding an item that is pinned or being closed stays, and none merges into an
     * empty workspace, which has no pane to go beside. When part of it stays behind (a listener pinned an
     * item mid-move, or an item's move failed), the items that arrived keep the panes they landed in, to the right of
     * the joined tab's active pane; a failure propagates afterwards.
     *
     * @param from the window holding the tab
     * @param parentTab the tab
     * @param to another window
     * @param group a pane of one of {@code to}'s tabs
     * @param side where the tab's panes go
     * @return whether any of its items arrived
     */
    public boolean moveTabInto(WindowContent from, ParentTabId parentTab, WindowContent to, GroupId group, Side side) {
        requireEdt();
        if (from == null || to == null || from == to || from.closed() || to.closed()) return false;
        WorkspaceView view = from.parentTabs().view(parentTab).orElse(null);
        WorkspaceView target = to.parentTabs().holding(group).orElse(null);
        // An empty workspace has a pane id but no pane to go beside: it takes a tab in its place.
        if (view == null || target == null || view.isEmpty() || target.isEmpty()
            || !view.tabs().stream().allMatch(model.tabs()::movable)) return false;
        List<Tab> moving = view.tabs();
        Layout arranged = target.model().insertBeside(view.model(), group, side);
        Throwable failure = null;
        try {
            to.receive(moving, target.id(), 0, arranged);
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        boolean moved = !to.closed() && moving.stream().anyMatch(target::contains);
        if (moved && !from.closed()) {
            try {
                // The window's only tab moved away whole: should the window stay, its empty workspace takes new ids, as
                // the moved panes keep theirs in the other window (spec 4).
                from.parentTabs().renew(parentTab);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return moved;
    }

    /**
     * Move Tab to Window (full windows spec 5.4): the parent tab {@code parentTab} of {@code from} moves whole, with its
     * id and layout, to just after {@code to}'s active parent tab, through {@link #moveParentTab}; it shows there and
     * {@code to}'s window comes to the front, and {@code from} closes when that left it without a tab. When part of it
     * stays behind (a listener pinned a tab mid-move, or a tab's move failed), the part that arrived shows; a failure
     * propagates afterwards.
     *
     * @param from the window holding the parent tab
     * @param parentTab the parent tab
     * @param to another window
     * @return whether any of its tabs arrived; false when nothing moved (a tab pinned or being closed, {@code from == to})
     */
    public boolean moveTabToWindow(WindowContent from, ParentTabId parentTab, WindowContent to) {
        requireEdt();
        if (from == null || to == null || from == to || from.closed() || to.closed()) return false;
        List<Tab> tabs = from.parentTabs().view(parentTab).map(WorkspaceView::tabs).orElse(List.of());
        int at = to.parentTabs().model().indexOf(to.activeParentTab()) + 1;
        Throwable failure = null;
        try {
            moveParentTab(from, parentTab, to, at);
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        // Where it landed: under its id, or under a new one when a listener or a failure left part of it behind.
        Optional<ParentTabId> landed = to.closed() ? Optional.empty()
            : tabs.stream().map(to::parentTabOf).flatMap(Optional::stream).findFirst();
        if (landed.isPresent()) {
            try {
                to.selectParentTab(landed.get());
                window(to.id()).ifPresent(JasperWindow::toFront);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return landed.isPresent();
    }

    /**
     * The window {@code tab} may leave for a window of its own: none for a tab the registry would not move (closed, pinned
     * or being closed: {@code TabRegistry.movable}), so no window opens for it, nor for a window's only tab, whose window
     * already is its own.
     */
    private WindowContent leaving(Tab tab) {
        if (tab == null || !model.tabs().movable(tab)) return null;
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || from.get().content().tabCount() < 2) return null;
        return from.get().content();
    }

    /**
     * A new window that a refused or failed move left without a tab goes; the move's failure propagates, the close's kept
     * behind it.
     */
    private Optional<JasperWindow> settle(JasperWindow child, Throwable failure) {
        if (!child.closed() && model.tabs().inWindow(child.id()).isEmpty()) {
            try {
                child.close();
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return child.closed() ? Optional.empty() : Optional.of(child);
    }

    /**
     * Closes every window without asking any guard, the most recently active last (full windows spec 5.3): Quit and going
     * to the background ask the tabs' guards first (the application's). While every window is whole, the workspace store saves them
     * all and then holds until they are restored, and the closing-all hook hears of it, once, before anything closes
     * (workspace tabs spec 6.4 and 13). Every window closes whatever closing another one throws, and no window opens
     * meanwhile; the first failure is rethrown once the last has closed, the others suppressed under it.
     */
    public void closeAll() {
        requireEdt();
        boolean nested = closingAll;
        closingAll = true;
        Throwable failure = null;
        try {
            if (!isEmpty()) {
                try {
                    // Closing the windows closes their tabs: that is not saved, or nothing would come back.
                    if (store != null) store.hold();
                } catch (RuntimeException | Error thrown) {
                    failure = thrown;
                }
                try {
                    hooks.closingAll().run();
                } catch (RuntimeException | Error thrown) {
                    failure = keep(failure, thrown);
                }
            }
            for (JasperWindow window : windowsByActivation().reversed()) failure = close(window, failure);
        } finally {
            closingAll = nested;
        }
        rethrow(failure);
    }

    /** Closes {@code window}, keeping its failure with those before it. */
    private static Throwable close(JasperWindow window, Throwable failure) {
        try {
            window.close();
        } catch (RuntimeException | Error thrown) {
            failure = keep(failure, thrown);
        }
        return failure;
    }

    /**
     * A new window, empty for tabs that move into it; its workspace at {@code bounds} on screen, on the display under
     * {@code cursor}, or where the platform puts it when they are null.
     */
    private Optional<JasperWindow> openEmpty(Rectangle bounds, Point cursor) {
        // A closing tab's listener asking for a window while every window closes gets none (spec 6.4).
        if (closingAll) return Optional.empty();
        return open(new Opening(true, null, "menu", Optional.ofNullable(bounds).map(Rectangle::new), Optional.ofNullable(cursor).map(Point::new)));
    }

    /** Composes, frames and shows one window; a failure on the way discards it and propagates. */
    private Optional<JasperWindow> open(Opening opening) {
        return open(opening, window -> true);
    }

    /**
     * As {@link #open(Opening)}; {@code prepare} readies the window before its frame exists, as a restore does, and a
     * window it refuses is discarded unshown. The one place a window is made (full windows spec 5.1;
     * {@code verifyApplicationArchitecture} pins this method).
     */
    private Optional<JasperWindow> open(Opening opening, Predicate<JasperWindow> prepare) {
        requireEdt();
        openings++;
        JasperWindow window = null;
        try {
            WindowContent content = hooks.content().apply(opening);
            if (content == null) return Optional.empty();
            // Every window drags tabs with the one drag, which reaches them all (workspace tabs spec 12).
            content.tabDrag(tabDrag);
            // Its moves to other windows are this manager's (tabs and panes spec 6).
            content.manager(this);
            window = new JasperWindow(this, content);
            windows.add(window);
            content.onLayoutChanged = this::changed;
            // A plugin's saved tab comes back through its plugin, or as a placeholder; one naming Jasper itself is left out
            // (workspace plan 6b Rulings 3 and 22).
            content.savedTabs = (tabId, saved) -> {
                if (!saved.pluginId().equals(TabRegistry.APP_OWNER)) return savedTab(content, tabId, saved);
                LOG.log(System.Logger.Level.WARNING, "Not restoring a saved " + saved.restorer() + " item: Jasper's own items are not restored");
                return Optional.empty();
            };
            if (!prepare.test(window)) {
                window.discard();
                return Optional.empty();
            }
            window.shell(hooks.frames().apply(window));
            // A torn-out tab's window opens where it was dropped, on the cursor's display (workspace tabs plan 5).
            if (opening.bounds().isPresent())
                window.shell().place().accept(opening.bounds().get(), opening.cursor().orElseThrow());
            window.shell().show().run();
            hooks.opened().accept(window);
            changed();
            return Optional.of(window);
        } catch (RuntimeException | Error failure) {
            if (window != null) {
                try {
                    window.discard();
                } catch (RuntimeException | Error discarding) {
                    failure.addSuppressed(discarding);
                }
            }
            throw failure;
        } finally {
            openings--;
        }
    }

    /**
     * What {@code workspace.toml} saves now (workspace tabs spec 8 and 13; tabs and panes spec 12): every open window, the
     * most recently active first ({@link #windowsByActivation}), each with where it is, what it holds (its parent tabs and
     * the one that shows) and what each of its tabs needs to come back. A tab that cannot come back is taken out first, as
     * closing it would take it out ({@code WorkspaceFile.keeping}): a parent tab left with none goes, and a window left
     * with none is left out, unless every window is: then the most recently active is saved with its empty workspace
     * ({@link #holding}). A window whose content has closed holds nothing, though it is listed until its closed hook runs.
     *
     * @return the snapshot; empty while no window is open, while the saved windows are restored, or once every open
     *     window's content has closed
     */
    public Optional<WorkspaceFile.Snapshot> snapshot() {
        requireEdt();
        if (restoring) return Optional.empty();
        var records = new ArrayList<WorkspaceFile.WindowRecord>();
        for (JasperWindow window : windowsByActivation()) if (!window.content().closed()) records.add(windowRecord(window));
        if (records.isEmpty()) return Optional.empty();
        return Optional.of(fitted(holding(records)));
    }

    /**
     * The windows as {@code records} save them, with items left out until the file fits {@link WorkspaceFile#MAX_BYTES},
     * which a read would otherwise set aside (workspace plan 6b Rulings 16): plugins' items first, then terminals, each the
     * largest first by the bytes the file writes for it, all counted in one pass ({@link WorkspaceFile#sizes}), so a save
     * under the limit writes no text here. Each is logged the first time, not at every save, and left out as closing it
     * would (Rulings 21): a tab left with none goes, and a window left with none is left out ({@link #holding}).
     */
    private WorkspaceFile.Snapshot fitted(List<WorkspaceFile.WindowRecord> records) {
        var snapshot = new WorkspaceFile.Snapshot(records);
        WorkspaceFile.Sizes sizes = WorkspaceFile.sizes(snapshot);
        long over = sizes.total() - WorkspaceFile.MAX_BYTES;
        if (over <= 0) return snapshot;
        var ranked = new ArrayList<Map.Entry<TabId, WorkspaceFile.TabRecord>>();
        for (WorkspaceFile.WindowRecord window : records) ranked.addAll(window.tabs().entrySet());
        ranked.sort(Comparator.comparing((Map.Entry<TabId, WorkspaceFile.TabRecord> item) -> !(item.getValue() instanceof WorkspaceFile.TabRecord.Content))
            .thenComparing(item -> sizes.items().getOrDefault(item.getKey(), 0L), Comparator.reverseOrder())
            .thenComparing(item -> item.getKey().value()));
        var out = new HashSet<TabId>();
        int next = 0;
        while (true) {
            for (; over > 0 && next < ranked.size(); next++) {
                var item = ranked.get(next);
                out.add(item.getKey());
                over -= sizes.items().getOrDefault(item.getKey(), 0L);
                if (leftOut.add(item.getKey())) LOG.log(System.Logger.Level.WARNING, "Not saving the tab " + item.getValue().title()
                    + ": the saved windows would pass workspace.toml's size limit");
            }
            snapshot = without(records, out);
            // What was counted leaves with each item, though a split it leaves may write its other panes' weights longer:
            // the file is checked once, and more items leave only when it still passes the limit.
            over = WorkspaceFile.encodedBytes(snapshot) - WorkspaceFile.MAX_BYTES;
            if (over <= 0 || next == ranked.size()) return snapshot;
        }
    }

    /**
     * {@code records} without the items {@code out} names, each window's taken out at once as closing them would
     * ({@code WorkspaceFile.keeping}): a tab left with none goes, and a window left with none is left out ({@link #holding}).
     */
    private static WorkspaceFile.Snapshot without(List<WorkspaceFile.WindowRecord> records, Set<TabId> out) {
        var windows = new ArrayList<WorkspaceFile.WindowRecord>();
        for (WorkspaceFile.WindowRecord window : records) {
            if (window.tabs().keySet().stream().noneMatch(out::contains)) {
                windows.add(window);
                continue;
            }
            ParentTabs kept = WorkspaceFile.keeping(window.layout(), tab -> !out.contains(tab));
            var tabs = new HashMap<>(window.tabs());
            tabs.keySet().removeAll(out);
            windows.add(new WorkspaceFile.WindowRecord(window.bounds(), window.extendedState(), kept, tabs));
        }
        return new WorkspaceFile.Snapshot(holding(windows));
    }

    /**
     * {@code records} without the windows that hold nothing that comes back, as a window that held none would come back
     * with nothing (workspace plan 2 Rulings 20); when no window holds any, the first, the most recently active, with its
     * empty workspace.
     */
    private static List<WorkspaceFile.WindowRecord> holding(List<WorkspaceFile.WindowRecord> records) {
        List<WorkspaceFile.WindowRecord> held = records.stream().filter(record -> !record.layout().isEmpty()).toList();
        return held.isEmpty() ? List.of(records.getFirst()) : held;
    }

    private WorkspaceFile.WindowRecord windowRecord(JasperWindow window) {
        var tabs = new HashMap<TabId, WorkspaceFile.TabRecord>();
        for (Tab tab : window.content().tabs()) tabRecord(tab).ifPresent(saved -> tabs.put(tab.id(), saved));
        ParentTabs kept = WorkspaceFile.keeping(window.content().windowLayout(), tabs::containsKey);
        JasperWindow.Placement placement = window.placement();
        return new WorkspaceFile.WindowRecord(placement.bounds(), placement.extendedState(), kept, tabs);
    }

    /**
     * What a tab needs to come back (workspace tabs spec 13): a terminal tab's name, directory and profile; a plugin's tab,
     * content or session, its restore state while its plugin restores that kind; a placeholder what was saved for it. An
     * app-owned tab and any other plugin's tab is not saved.
     */
    private Optional<WorkspaceFile.TabRecord> tabRecord(Tab tab) {
        if (tab.content() instanceof PlaceholderContent placeholder) return Optional.of(placeholder.saved());
        Optional<String> plugin = tab.content() instanceof TerminalContent terminal ? terminal.pane().snapshot().providerId()
            : Optional.of(tab.plugin()).filter(id -> !id.isEmpty());
        if (plugin.isEmpty())
            return tab.content() instanceof TerminalContent terminal ? terminal.record().map(WorkspaceFile.TabRecord.class::cast) : Optional.empty();
        if (plugin.get().equals(TabRegistry.APP_OWNER)) return Optional.empty();
        return tab.restore().filter(state -> model.tabs().restores(plugin.get(), state.kind()))
            .map(state -> new WorkspaceFile.TabRecord.Content(tab.title(), plugin.get(), state.kind(), state.state()));
    }

    /**
     * A plugin's saved tab for {@code content}'s window, with the saved id: rebuilt by its plugin's restorer, else a
     * placeholder that says why (workspace tabs spec 9.2 and 13); empty when an item with that id is already open.
     */
    private Optional<Tab> savedTab(WindowContent content, TabId id, WorkspaceFile.TabRecord.Content saved) {
        PluginState state = plugins.apply(saved.pluginId());
        if (state.kind() != PluginState.Kind.RUNNING) return placeholder(content, id, saved, reason(state), false);
        TabRegistry.Restored restored = rebuilt(content, id, state, saved);
        return restored.tab().isPresent() ? restored.tab() : placeholder(content, id, saved, restored.problem(), true);
    }

    /**
     * Runs the restorer of {@code saved}'s plugin, which runs as {@code state} says, for {@code content}'s window under
     * {@code id}: the item it opened, else why there is none, as a placeholder says it (workspace tabs spec 9.2 and 13).
     */
    private TabRegistry.Restored rebuilt(WindowContent content, TabId id, PluginState state, WorkspaceFile.TabRecord.Content saved) {
        TabRegistry.Restored restored = model.tabs().restore(id, saved.pluginId(),
            new TabRegistry.Restoring(content.id(), saved.title(), new TabRestore(saved.restorer(), saved.state())));
        if (restored.tab().isPresent()) return restored;
        return new TabRegistry.Restored(Optional.empty(), state.name() + " could not restore this tab: " + sentence(restored.problem()));
    }

    /** {@code text} ending a sentence: as it is when it already ends with a full stop, a question mark or an exclamation mark. */
    private static String sentence(String text) {
        return text.endsWith(".") || text.endsWith("?") || text.endsWith("!") ? text : text + ".";
    }

    /**
     * Why a plugin that does not run restores nothing, as a placeholder says it, and what the user can do about it: a change
     * in Manage Plugins takes effect when Jasper next starts (workspace plan 6b's final review, M3).
     */
    private static String reason(PluginState state) {
        return switch (state.kind()) {
            case NOT_INSTALLED -> "The plugin " + state.name() + " is not installed.";
            case TURNED_OFF -> state.name() + " is turned off. Turn it on in Manage Plugins and restart Jasper.";
            case MARKED_FOR_REMOVAL -> state.name() + " is marked for removal in Manage Plugins.";
            case SAFE_MODE -> state.name() + " is off in safe mode. Restart Jasper normally to restore this tab.";
            case NEEDS_CONSENT -> state.name() + " waits for your review in Manage Plugins. Review it there and restart Jasper.";
            case FAILED -> state.name() + " could not start.";
            case RUNNING -> state.name() + " runs.";
        };
    }

    /**
     * A placeholder tab in {@code content}'s window, titled as the saved tab was. Its Retry and Close act in whatever window
     * holds it when they are pressed, and it asks how its plugin stands again when it shows and on Retry. Empty when an item
     * with the saved id is already open.
     */
    private Optional<Tab> placeholder(WindowContent content, TabId id, WorkspaceFile.TabRecord.Content saved, String why, boolean retryable) {
        if (model.tabs().tab(id).isPresent()) {
            // It came back elsewhere: a placeholder under its id would throw out of this window's restore, which leaves the
            // record out instead (6b Task 1's review).
            LOG.log(System.Logger.Level.WARNING, "Not restoring " + saved.pluginId() + "'s saved item " + id.value()
                + " here: an item with its id is already open");
            return Optional.empty();
        }
        Tab[] tab = new Tab[1];
        var placeholder = new PlaceholderContent(saved, why, retryable, () -> retry(tab[0]),
            () -> window(tab[0].window()).ifPresent(holder -> holder.content().closeTab(tab[0])), () -> standing(tab[0]));
        tab[0] = model.tabs().add(id, Tab.Kind.PLACEHOLDER, placeholder, content.id());
        tab[0].presentation(saved.title(), "", null, false, "", "");
        // Focusing the tab from the keyboard reaches its action: Retry while it shows, otherwise Close.
        tab[0].focusTarget(placeholder::focusAction);
        return Optional.of(tab[0]);
    }

    /**
     * Retry on a placeholder: while its plugin runs, its restorer runs again in the window that holds it, which puts what
     * it opens in the placeholder's pane, or shows why there is none.
     */
    private void retry(Tab placeholder) {
        standing(placeholder).ifPresent(state -> window(placeholder.window()).ifPresent(holder ->
            holder.content().retry(placeholder, saved -> rebuilt(holder.content(), TabId.random(), state, saved))));
    }

    /**
     * Says again how a placeholder's plugin stands: why it does not run, without Retry, or, for one that came back as not
     * running and runs now, that Retry brings the tab back.
     *
     * @return how the plugin stands while it runs; empty when it does not, or the placeholder has closed
     */
    private Optional<PluginState> standing(Tab placeholder) {
        if (placeholder.closed() || !(placeholder.content() instanceof PlaceholderContent content)) return Optional.empty();
        PluginState state = plugins.apply(content.saved().pluginId());
        if (state.kind() != PluginState.Kind.RUNNING) {
            content.failed(reason(state), false);
            return Optional.empty();
        }
        if (!content.retryable()) content.failed(state.name() + " runs now: Retry brings this tab back.", true);
        return Optional.of(state);
    }

    /**
     * A move of a tab out of {@code window} has ended, whether or not the tab left, or a tab placed in it failed to show:
     * a window left without a tab closes now, so listeners hear {@code TAB_MOVED} before that window's
     * {@code WINDOW_CLOSED} (full windows spec 5.3).
     */
    private void movedOut(UUID window) {
        window(window).filter(found -> !found.closed() && found.content().tabCount() == 0).ifPresent(JasperWindow::close);
    }

    /**
     * An empty window for a plugin's tab placed in a new window (workspace tabs spec 14's
     * {@code TabPlacement.NEW_WINDOW}), after the saved windows are restored when none is open; null while they are being
     * restored.
     */
    private UUID emptyWindow() {
        if (restoring) return null;
        if (!restoredFirst("plugin")) throw new IllegalStateException(CLOSING);
        return openEmpty(null, null).map(JasperWindow::id).orElseThrow(() -> new IllegalStateException(CLOSING));
    }

    /**
     * Brings an open window to the front for a gesture that goes on to act in the same event: it is the most recently
     * active window at once ({@link #lastActive}, {@link #ensureWindow}), before the operating system reports the
     * activation, so what the gesture opens next lands in it. The activated hook runs when that report arrives. EDT.
     */
    public void raise(JasperWindow window) {
        requireEdt();
        if (window.closed() || !windows.contains(window)) return;
        mostRecent(window);
        window.toFront();
    }

    void activated(JasperWindow window) {
        mostRecent(window);
        tabDrag.activated(window.content());
        if (!watchingFronts) {
            Toolkit.getDefaultToolkit().addAWTEventListener(fronts, AWTEvent.WINDOW_EVENT_MASK);
            watchingFronts = true;
        }
        hooks.activated().accept(window);
    }

    /** Puts the window first in the activation order. */
    private void mostRecent(JasperWindow window) {
        if (window != lastOpened) lastOpened = null;
        activations.remove(window);
        activations.addFirst(window);
    }

    /** A window is about to close, its content whole. */
    void closing(JasperWindow window) { hooks.closing().accept(window); }

    void stateChanged(JasperWindow window, boolean showing, boolean iconified) {
        hooks.state().accept(new State(window, showing, iconified));
    }

    /** A window closed or was discarded: it is forgotten first, so a failing hook cannot keep it. */
    void closed(JasperWindow window) {
        windows.remove(window);
        if (window == lastOpened) lastOpened = null;
        activations.remove(window);
        if (watchingFronts && isEmpty()) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(fronts);
            watchingFronts = false;
        }
        try {
            // A window closing is saved at once: a window the user closed is gone with its tabs (spec 13).
            if (store != null) store.save();
        } finally {
            // Residency, the Dock and shutdown hear of every window, whatever the save did.
            hooks.closed().accept(window);
        }
    }

    static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Windows belong to the EDT");
    }

    /**
     * The failure to throw once a cleanup is done: the first one, each later one suppressed under it; the first thrown
     * again is not suppressed under itself.
     */
    static Throwable keep(Throwable first, Throwable later) {
        if (first == null) return later;
        if (first != later) first.addSuppressed(later);
        return first;
    }

    /** Throws {@code failure}, which {@link #keep} collected from runtime exceptions and errors only; null throws nothing. */
    static void rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }
}
