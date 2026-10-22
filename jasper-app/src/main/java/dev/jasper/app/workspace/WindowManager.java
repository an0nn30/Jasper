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
import dev.jasper.app.persistence.WorkspaceFile;
import java.awt.Point;
import java.awt.Rectangle;
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
 * Jasper's windows (workspace tabs spec 6.1 and 6.4): at most one {@link MainWindow} and any number of
 * {@link ChildWindow}s. It makes the main window only when none is open, in one place ({@link #ensureMain}), and every
 * child window; it closes a child window when its last tab goes and every child window before the main window; it sends a
 * plugin's view without a window, and a child window's panel requests, to the main window. The application owns it and
 * asks it every window question. EDT only.
 */
public final class WindowManager {
    /**
     * A window about to open.
     *
     * @param role the main window or a child window
     * @param empty whether it starts without a tab, for a tab that moves into it
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @param trigger what asked for it, for its start-up timing line
     * @param emptied what its content runs when its last tab has gone
     * @param bounds where a child window's workspace opens on screen, a torn-out tab's (workspace tabs plan 5); empty
     *     for where the platform puts a new window, or where a restored window's saved place puts it
     * @param cursor where the cursor released that tab on screen, present with the bounds: the window opens on the
     *     display there
     */
    public record Opening(WindowRole role, boolean empty, Path directory, String trigger, Runnable emptied,
                          Optional<Rectangle> bounds, Optional<Point> cursor) {
        /** Requires the role, the trigger, the callback, and the bounds with the cursor or neither. */
        public Opening {
            Objects.requireNonNull(role, "role"); Objects.requireNonNull(trigger, "trigger"); Objects.requireNonNull(emptied, "emptied");
            Objects.requireNonNull(bounds, "bounds"); Objects.requireNonNull(cursor, "cursor");
            if (bounds.isPresent() != cursor.isPresent())
                throw new IllegalArgumentException("A child window's bounds come with the cursor");
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
     * @param closingAll every window is about to close (closing the main window, Quit, going to the background), before any
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
    private final Contributions model;
    private final Hooks hooks;
    private final List<ChildWindow> children = new ArrayList<>();
    /** The windows that came to the front, the last one first. */
    private final List<JasperWindow> activations = new ArrayList<>();
    /**
     * The one tab drag of every window this manager opens (workspace tabs spec 12): it reaches them all, top first, and
     * carries what leaves a window to this manager, which each window knows (parent tabs spec 5).
     */
    private final TabDrag tabDrag = new TabDrag(() -> windowsByActivation().stream().map(JasperWindow::content).toList());
    private MainWindow main;
    private JasperWindow lastActive;
    private int openings;
    private boolean openingMain;
    /** Where the windows are saved; none in a test that does not save. */
    private WorkspaceStore store;
    /** How each plugin stands, by id: without the application's answer, none is installed. */
    private Function<String, PluginState> plugins = id -> new PluginState(id, PluginState.Kind.NOT_INSTALLED);
    /** The items the size limit has left out of a save so far, each logged once (workspace plan 6b Rulings 16). */
    private final Set<TabId> leftOut = new HashSet<>();
    /** While {@link #closeAll()} runs: no window opens, so no child window outlives the main window and no fresh one opens. */
    private boolean closingAll;

    /**
     * The windows of the application whose contributions are {@code model}: its tab registry places a plugin's view
     * through this manager, and its panel requests from a child window reach the main window.
     *
     * @param model the application's contributions
     * @param hooks what the application gives the manager
     */
    public WindowManager(Contributions model, Hooks hooks) {
        this.model = Objects.requireNonNull(model, "model");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        // A plugin's view opened with no window goes to the main window, restored first when none is open (spec 6.4);
        // while the main window is being composed it waits for it, until ensureMain says it opened or failed. A child
        // window a move empties closes after the move.
        model.tabs().windows(new TabRegistry.Windows(() -> openingMain ? null : ensureMain("plugin", null).map(JasperWindow::id)
            .orElseThrow(() -> new IllegalStateException("The main window is closing or Jasper is quitting")), this::isMain,
            this::moveToNewWindow, this::movedOut, this::emptyWindow));
        model.panelRoute(this::routePanel);
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
     * Jasper home. Without it nothing is saved and every main window opens fresh.
     *
     * @param value the store
     */
    public void workspace(WorkspaceStore value) { store = Objects.requireNonNull(value, "value"); }

    /** Something {@code workspace.toml} saves changed: a layout, a tab's name or directory, where a window is. */
    void changed() { if (store != null) store.changed(); }

    /** @return the main window while it is open */
    public Optional<MainWindow> main() { return Optional.ofNullable(main); }

    /** @return the child windows, in the order they opened */
    public List<ChildWindow> children() { return List.copyOf(children); }

    /** @return every open window: the main window first, then the child windows in the order they opened */
    public List<JasperWindow> windows() {
        var all = new ArrayList<JasperWindow>();
        if (main != null) all.add(main);
        all.addAll(children);
        return List.copyOf(all);
    }

    /**
     * Whether {@code id} is the main window's.
     *
     * @param id a window's id, or null
     * @return true for the main window while it is open
     */
    public boolean isMain(UUID id) { return main != null && main.id().equals(id); }

    /** @return whether no window is open */
    public boolean isEmpty() { return main == null && children.isEmpty(); }

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

    /** @return the window that came to the front last while it is open; else the main window; else the first child window */
    public Optional<JasperWindow> lastActive() {
        if (lastActive != null && !lastActive.closed()) return Optional.of(lastActive);
        if (main != null) return Optional.of(main);
        return children.isEmpty() ? Optional.empty() : Optional.of(children.getFirst());
    }

    /**
     * The main window, restored first when none is open (workspace tabs spec 6.4 and 13: every request restores it first):
     * as the store has it, with its full windows, else a new main window with one terminal tab. The tabs a plugin asked
     * for while it was being composed open in it once it shows, or go with it when it fails to open, their failures
     * under its own.
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts, or, when the main window is restored, where a
     *     terminal starts in a new tab after the restored ones; null for where a new shell starts, or no new tab
     * @return the main window; empty while Jasper quits, the main window closes or every window closes
     * @throws IllegalStateException when asked again while the main window is still opening
     */
    public Optional<MainWindow> ensureMain(String trigger, Path directory) {
        requireEdt();
        // A main window that is closing is not absent: no second one opens while it goes (workspace tabs spec 6.1).
        if (main != null) return main.closed() ? Optional.empty() : Optional.of(main);
        // Nor does one open while every window closes, from a closed hook say.
        if (closingAll) return Optional.empty();
        if (openingMain) throw new IllegalStateException("The main window is still opening");
        openingMain = true;
        Optional<MainWindow> opened;
        try {
            // The one place a main window is made (workspace tabs spec 6.1; verifyApplicationArchitecture pins this method).
            Function<WindowContent, MainWindow> make = content -> main = new MainWindow(this, content);
            opened = openMain(trigger, directory, make);
        } catch (RuntimeException | Error failure) {
            // Still opening: a waiting tab's close listener cannot ask for another main window.
            model.tabs().mainFailed(failure);
            throw failure;
        } finally {
            openingMain = false;
        }
        // A main window its own opening closed again leaves the waiting tabs to the next one.
        opened.filter(window -> !window.closed()).ifPresent(window -> model.tabs().mainOpened(window.id()));
        return opened;
    }

    /**
     * The main window as the store has it (workspace tabs spec 13; tabs and panes spec 12): the saved main window, then
     * each saved full window, each where it was with its tabs, splits and panes and its items, the main window in front;
     * with nothing to restore, or a main window that cannot be restored, a new main window with one terminal tab in
     * {@code directory}. A restored main window opens {@code directory} in a new tab after its own. A window that cannot
     * be restored is not lost: the store keeps the saved windows as {@code workspace.toml.bad} and says so (spec 15).
     * {@code make} is {@link #ensureMain}'s, the one place a main window is made; the store is read here, after
     * {@link #ensureMain}'s checks, since reading it ends its hold.
     */
    private Optional<MainWindow> openMain(String trigger, Path directory, Function<WindowContent, MainWindow> make) {
        WorkspaceFile.Loaded loaded = store == null ? new WorkspaceFile.Loaded.Fresh(Optional.empty()) : store.load();
        if (!(loaded instanceof WorkspaceFile.Loaded.Restore restore)) return openNewMain(trigger, directory, make);
        WorkspaceFile.Snapshot saved = restore.snapshot();
        boolean[] restored = new boolean[1];
        Optional<MainWindow> opened = open(new Opening(WindowRole.MAIN, true, null, trigger, () -> { }, Optional.empty(),
            Optional.empty()), make, window -> {
                try {
                    window.restore(saved.main());
                    restored[0] = true;
                } catch (RuntimeException | Error failure) {
                    // It opens as a new main window instead, never discarded: the closed hook of the last window ends a
                    // Jasper that is not resident. An Error too: the saved windows are kept aside before this window's
                    // first save replaces them, so the next start does not meet it again (workspace plan 6a Rulings 20).
                    // Its restore closed what it made.
                    LOG.log(System.Logger.Level.WARNING, "Could not restore the main window; it opens as a new one", failure);
                    keepAside(saved);
                    window.content().newTab(directory);
                }
                return true;
            });
        if (opened.isEmpty() || !restored[0]) return opened;
        // The main window has shown: from here a failure, an Error too, costs only what failed, never the window, nor the
        // tabs that wait for it (workspace plan 6a Rulings 41).
        boolean whole = true;
        var back = new ArrayList<ChildWindow>();
        for (WorkspaceFile.WindowRecord child : saved.children()) {
            try {
                restoreChild(child).ifPresent(back::add);
            } catch (RuntimeException | Error failure) {
                // A full window that cannot come back costs only itself, and the saved windows are kept.
                LOG.log(System.Logger.Level.WARNING, "Could not restore a full window", failure);
                whole = false;
            }
        }
        if (!whole) keepAside(saved);
        if (directory != null) {
            try { opened.get().content().newTab(directory); }
            catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not open the requested directory in a new tab", failure); }
        }
        // In front when a full window came back (workspace plan 6a Rulings 12).
        if (!back.isEmpty()) {
            try { opened.get().toFront(); }
            catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not bring the restored main window to the front", failure); }
        }
        return opened;
    }

    /**
     * Keeps the saved windows as {@code workspace.toml.bad} (workspace plan 6a Rulings 13); a failure there is logged, so
     * it never costs the window that is opening.
     */
    private void keepAside(WorkspaceFile.Snapshot saved) {
        try { store.setAside(saved); }
        catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not keep the saved windows aside", failure); }
    }

    /** A new main window with one terminal tab in {@code directory}, made by {@link #ensureMain}'s {@code make}. */
    private Optional<MainWindow> openNewMain(String trigger, Path directory, Function<WindowContent, MainWindow> make) {
        return open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty(), Optional.empty()), make);
    }

    /**
     * A saved full window, where it was with its tabs and items; one with no item that can come back is left out. Its
     * timing line says {@code restore}, which no benchmark reads as a menu's window (workspace plan 6a Rulings 16).
     *
     * @return the window, once it shows; empty when it was left out
     */
    private Optional<ChildWindow> restoreChild(WorkspaceFile.WindowRecord record) {
        ChildWindow[] made = new ChildWindow[1];
        // A restore that fails once its items are mounted closes them, which empties the window before its frame exists:
        // it is discarded then, unshown, never closed as a window that opened is (no closing hook).
        Runnable emptied = () -> { if (made[0] != null && made[0].shell() != null) made[0].emptied(); };
        // Its saved place reaches its frame through its placement, checked against today's displays, never as a tear-out's
        // workspace bounds, which its frame would grow by its chrome (workspace plan 6a Rulings 21).
        return open(new Opening(WindowRole.CHILD, true, null, "restore", emptied, Optional.empty(), Optional.empty()), content -> {
            made[0] = new ChildWindow(this, content);
            children.add(made[0]);
            return made[0];
        }, window -> window.restore(record) > 0);
    }

    /**
     * New Terminal Window (workspace tabs spec 6.5): a child window with a terminal tab in {@code directory}, after the
     * main window is open.
     *
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @return the child window; empty while Jasper quits or every window closes
     */
    public Optional<ChildWindow> openTerminalWindow(Path directory) {
        requireEdt();
        if (ensureMain("menu", null).isEmpty()) return Optional.empty();
        return openChild(false, directory, null, null);
    }

    /**
     * {@code ContentTab.moveToNewWindow()} (tabs and panes spec 13): a new full window takes {@code tab}, its content and
     * session untouched, where the platform puts a window (workspace plan 8a Rulings 6). A tab being closed or pinned
     * stays, and so does a child window's only tab: that window already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new full window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab) {
        requireEdt();
        if (leaving(tab) == null) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, null, null);
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
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> moveTabToNewWindow(WindowContent window, ParentTabId parentTab) {
        return moveTabToNewWindow(window, parentTab, null, null);
    }

    /**
     * As {@link #moveTabToNewWindow(WindowContent, ParentTabId)}, the full window's workspace at {@code bounds} on screen,
     * on the display under {@code cursor}: the tab drag's tear-out.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @param bounds where the full window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> moveTabToNewWindow(WindowContent window, ParentTabId parentTab, Rectangle bounds, Point cursor) {
        requireEdt();
        if (window == null || !window.mayMoveTabToNewWindow(parentTab)) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, bounds, cursor);
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
     * {@code TAB_MOVED} per tab, every session untouched. It shows there, raised, and a full window it empties closes; the
     * main window it empties shows an empty workspace under a new id. A parent tab holding a tab that is pinned or being
     * closed stays. It keeps its maximize too: spec 15 ends a maximize for a tab
     * that joins a layout, and a parent tab moving whole brings its own (Rulings 10). When part of it stays behind (a
     * listener pinned a tab mid-move, or a tab's move failed), the part that arrived takes a new id and keeps the panes it
     * landed in, so no id is in two windows (spec 4); a failure propagates afterwards.
     *
     * @param from the window holding the parent tab
     * @param parentTab the parent tab
     * @param to another main or full window
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
                // The window's only parent tab is the other window's now: the empty workspace it leaves takes a new id
                // (Rulings 10).
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
     * maximize ended (spec 13). Its tab in {@code from} goes with its last pane, the main window keeping its empty
     * workspace, and a full window it empties closes after the move. An item that is pinned or being closed stays.
     *
     * @param from the window holding the item
     * @param tab the item
     * @param to another main or full window
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
     * A pane drag's drop on a new-tab place of {@code to}'s strip, or on the main window's empty workspace (tabs and panes
     * spec 7; workspace plan 8c Rulings 10 and 12): as {@link #movePane}, into a new tab at {@code index} of {@code to}'s
     * strip; the main window's empty workspace takes it in its place, under its own id.
     *
     * @param from the window holding the item
     * @param tab the item
     * @param to another main or full window
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
     * keeping its id, its maximize ended, the moved tab's active item selected and its window raised. A full window the
     * move empties closes after the moves, and the main window it empties shows an empty workspace under new ids, as the
     * moved panes keep theirs. A tab holding an item that is pinned or being closed stays. When part of it stays behind (a
     * listener pinned an item mid-move, or an item's move failed), the items that arrived keep the panes they landed in, to
     * the right of the joined tab's active pane; a failure propagates afterwards.
     *
     * @param from the window holding the tab
     * @param parentTab the tab
     * @param to another main or full window
     * @param group a pane of one of {@code to}'s tabs
     * @param side where the tab's panes go
     * @return whether any of its items arrived
     */
    public boolean moveTabInto(WindowContent from, ParentTabId parentTab, WindowContent to, GroupId group, Side side) {
        requireEdt();
        if (from == null || to == null || from == to || from.closed() || to.closed()) return false;
        WorkspaceView view = from.parentTabs().view(parentTab).orElse(null);
        WorkspaceView target = to.parentTabs().holding(group).orElse(null);
        if (view == null || target == null || view.isEmpty() || !view.tabs().stream().allMatch(model.tabs()::movable)) return false;
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
                // The main window's only tab moved away whole: its empty workspace takes new ids, as the moved panes keep
                // theirs in the other window (spec 4).
                from.parentTabs().renew(parentTab);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return moved;
    }

    /**
     * Move to Main Window (parent tabs spec 5; workspace plan 7b Rulings 12): a full window's parent tabs join the main
     * window's bar after its active parent tab, in their order, each with its id and layout; the parent tab the full window
     * showed shows there, the main window comes to the front, and the full window closes with its last tab. While one of
     * its tabs is pinned or being closed, nothing moves.
     *
     * @param window a full window
     * @return the main window; empty when nothing arrived
     */
    public Optional<MainWindow> moveToMainWindow(WindowContent window) {
        requireEdt();
        if (window == null || !window.mayMoveToMainWindow()) return Optional.empty();
        Optional<MainWindow> main = ensureMain("menu", null);
        if (main.isEmpty()) return main;
        WindowContent target = main.get().content();
        ParentTabId shown = window.activeParentTab();
        List<ParentTabId> moving = window.parentTabs().model().parentTabs().stream().map(ParentTab::id).toList();
        int at = target.parentTabs().model().indexOf(target.activeParentTab()) + 1;
        ParentTabId shownThere = null;
        boolean arrived = false;
        Throwable failure = null;
        for (ParentTabId parentTab : moving) {
            List<Tab> tabs = window.parentTabs().view(parentTab).map(WorkspaceView::tabs).orElse(List.of());
            try {
                moveParentTab(window, parentTab, target, at);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
            // Where it landed: under its id, or under a new one when a listener or a failure left part of it in the full
            // window, which then stays open (moveParentTab).
            Optional<ParentTabId> landed = tabs.stream().map(target::parentTabOf).flatMap(Optional::stream).findFirst();
            if (landed.isEmpty()) continue;
            arrived = true;
            at = target.parentTabs().model().indexOf(landed.get()) + 1;
            if (parentTab.equals(shown)) shownThere = landed.get();
        }
        if (shownThere != null) target.selectParentTab(shownThere);
        if (arrived) main.get().toFront();
        rethrow(failure);
        return arrived ? main : Optional.empty();
    }

    /**
     * The window {@code tab} may leave for a window of its own: none for a tab the registry would not move (closed, pinned
     * or being closed: {@code TabRegistry.movable}), so no window opens for it, nor for a child window's only tab, whose
     * window already is its own.
     */
    private WindowContent leaving(Tab tab) {
        if (tab == null || !model.tabs().movable(tab)) return null;
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || !from.get().isMain() && from.get().content().tabCount() < 2) return null;
        return from.get().content();
    }

    /**
     * A new child window that a refused or failed move left without a tab goes; the move's failure propagates, the close's
     * kept behind it.
     */
    private Optional<ChildWindow> settle(ChildWindow child, Throwable failure) {
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
     * Closes every child window, then the main window, without asking any guard: Quit and going to the background ask
     * first. While every window is whole, the workspace store saves them all and then holds until they are restored, and
     * the closing-all hook hears of it, once, before anything closes (workspace tabs spec 6.4 and 13). Every window
     * closes whatever closing another one throws, and no child window opens meanwhile; the first failure is rethrown once
     * the main window has closed, the others suppressed under it.
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
            for (ChildWindow child : List.copyOf(children)) failure = close(child, failure);
            if (main != null) failure = close(main, failure);
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
     * The user closes the main window (workspace tabs spec 6.4): every window's tabs' guards decide; when they agree, every
     * child window closes, then the main window. Residency then decides whether Jasper stays, through the closed hook.
     *
     * @return whether the windows closed
     */
    boolean closeMain() {
        requireEdt();
        if (main == null) return true;
        if (!model.tabs().mayClose(this::everyTab)) return false;
        closeAll();
        return true;
    }

    private List<Tab> everyTab() {
        var all = new ArrayList<Tab>();
        for (JasperWindow window : windows()) all.addAll(model.tabs().inWindow(window.id()));
        return all;
    }

    /** A full child window, empty for tabs that move into it or holding a terminal tab in {@code directory}. */
    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds, Point cursor) {
        // A closing tab's listener asking for a window while every window closes gets none (spec 6.4).
        if (closingAll) return Optional.empty();
        ChildWindow[] made = new ChildWindow[1];
        return open(new Opening(WindowRole.CHILD, empty, directory, "menu", () -> { if (made[0] != null) made[0].emptied(); },
            Optional.ofNullable(bounds).map(Rectangle::new), Optional.ofNullable(cursor).map(Point::new)), content -> {
            made[0] = new ChildWindow(this, content);
            children.add(made[0]);
            return made[0];
        });
    }

    /** Composes, frames and shows one window; a failure on the way discards it and propagates. */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make) {
        return open(opening, make, window -> true);
    }

    /**
     * As {@link #open(Opening, Function)}; {@code prepare} readies the window before its frame exists, as a restore does,
     * and a window it refuses is discarded unshown.
     */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make, Predicate<? super W> prepare) {
        requireEdt();
        openings++;
        W window = null;
        try {
            WindowContent content = hooks.content().apply(opening);
            if (content == null) return Optional.empty();
            // Every window drags tabs with the one drag, which reaches them all (workspace tabs spec 12).
            content.tabDrag(tabDrag);
            // Its moves to other windows are this manager's (tabs and panes spec 6).
            content.manager(this);
            window = make.apply(content);
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
     * What {@code workspace.toml} saves now (workspace tabs spec 8 and 13; tabs and panes spec 12): the main window and its
     * full windows, each with where it is, what it holds (its parent tabs and the one that shows) and what each of its
     * tabs needs to come back. A tab that cannot come back is taken
     * out first, as closing it would take it out ({@code WorkspaceFile.keeping}): a parent tab left with none goes, a
     * child window left with none is left out (workspace plan 2 Rulings 20), and the main window may be empty. A window
     * whose content has closed holds nothing, though it is listed until its closed hook runs.
     *
     * @return the snapshot; empty while no main window is open, while it opens, or once its content has closed
     */
    public Optional<WorkspaceFile.Snapshot> snapshot() {
        requireEdt();
        if (main == null || openingMain || main.content().closed()) return Optional.empty();
        var records = new ArrayList<WorkspaceFile.WindowRecord>();
        for (JasperWindow window : windows()) if (!window.content().closed()) windowRecord(window).ifPresent(records::add);
        return Optional.of(fitted(records));
    }

    /**
     * The windows as {@code records} save them, with items left out until the file fits {@link WorkspaceFile#MAX_BYTES},
     * which a read would otherwise set aside (workspace plan 6b Rulings 16): plugins' items first, then terminals, each the
     * largest first by the bytes the file writes for it, all counted in one pass ({@link WorkspaceFile#sizes}), so a save
     * under the limit writes no text here. Each is logged the first time, not at every save, and left out as closing it
     * would (Rulings 21): a tab left with none goes, and a full window left with none is left out.
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
     * ({@code WorkspaceFile.keeping}): a tab left with none goes, and a full window left with none is left out.
     */
    private static WorkspaceFile.Snapshot without(List<WorkspaceFile.WindowRecord> records, Set<TabId> out) {
        var windows = new ArrayList<WorkspaceFile.WindowRecord>();
        for (WorkspaceFile.WindowRecord window : records) {
            if (window.tabs().keySet().stream().noneMatch(out::contains)) {
                windows.add(window);
                continue;
            }
            Optional<ParentTabs> kept = WorkspaceFile.keeping(window.layout(), tab -> !out.contains(tab), window.role() == WorkspaceFile.Role.MAIN);
            if (kept.isEmpty()) continue;
            var tabs = new HashMap<>(window.tabs());
            tabs.keySet().removeAll(out);
            windows.add(new WorkspaceFile.WindowRecord(window.role(), window.bounds(), window.extendedState(), kept.get(), tabs));
        }
        return new WorkspaceFile.Snapshot(windows);
    }

    private Optional<WorkspaceFile.WindowRecord> windowRecord(JasperWindow window) {
        var tabs = new HashMap<TabId, WorkspaceFile.TabRecord>();
        for (Tab tab : window.content().tabs()) tabRecord(tab).ifPresent(saved -> tabs.put(tab.id(), saved));
        Optional<ParentTabs> kept = WorkspaceFile.keeping(window.content().windowLayout(), tabs::containsKey, window.isMain());
        if (kept.isEmpty()) return Optional.empty();
        JasperWindow.Placement placement = window.placement();
        return Optional.of(new WorkspaceFile.WindowRecord(window.isMain() ? WorkspaceFile.Role.MAIN : WorkspaceFile.Role.CHILD,
            placement.bounds(), placement.extendedState(), kept.get(), tabs));
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
     * A child window's panel request goes to the main window, which has the dock, and brings it forward to show it. A
     * toggle there shows the panel and never hides it, and a user's activating toggle shows it activating, as a tool
     * window's shortcut in IntelliJ's detached editor activates the tool window.
     */
    private Contributions.PanelRequest routePanel(Contributions.PanelRequest request) {
        if (main == null || children.stream().noneMatch(child -> child.id().equals(request.windowId()))) return request;
        var op = switch (request.op()) {
            case TOGGLE -> Contributions.PanelRequest.Op.SHOW;
            case ACTIVATING_TOGGLE -> Contributions.PanelRequest.Op.ACTIVATING_SHOW;
            case SHOW, HIDE, ACTIVATING_SHOW -> request.op();
        };
        if (op != Contributions.PanelRequest.Op.HIDE) main.toFront();
        return new Contributions.PanelRequest(main.id(), request.panelId(), op);
    }

    /**
     * A move of a tab out of {@code window} has ended, whether or not the tab left: a child window it left empty closes now,
     * so listeners hear {@code TAB_MOVED} before that window's {@code WINDOW_CLOSED} (workspace tabs spec 6.4).
     */
    private void movedOut(UUID window) {
        for (ChildWindow child : List.copyOf(children))
            if (child.id().equals(window) && child.content().tabCount() == 0) child.close();
    }

    /**
     * An empty child window for a plugin's tab placed in a new window (workspace tabs spec 14's
     * {@code TabPlacement.NEW_WINDOW}), after the main window; null while the main window is being composed.
     */
    private UUID emptyWindow() {
        if (openingMain) return null;
        if (ensureMain("plugin", null).isEmpty()) throw new IllegalStateException("The main window is closing or Jasper is quitting");
        return openChild(true, null, null, null).map(JasperWindow::id)
            .orElseThrow(() -> new IllegalStateException("The main window is closing or Jasper is quitting"));
    }

    void activated(JasperWindow window) {
        lastActive = window;
        activations.remove(window);
        activations.addFirst(window);
        hooks.activated().accept(window);
    }

    /** A window is about to close, its content whole. */
    void closing(JasperWindow window) { hooks.closing().accept(window); }

    void stateChanged(JasperWindow window, boolean showing, boolean iconified) {
        hooks.state().accept(new State(window, showing, iconified));
    }

    /** A window closed or was discarded: it is forgotten first, so a failing hook cannot keep it. */
    void closed(JasperWindow window) {
        if (window == main) main = null;
        else children.remove(window);
        activations.remove(window);
        if (lastActive == window) lastActive = null;
        try {
            // A window closing is saved at once: a child window the user closed is gone with its tabs (spec 13).
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
