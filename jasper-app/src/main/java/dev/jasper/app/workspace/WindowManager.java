package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Origin;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
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
     *     for where the platform puts it
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
     *     does: plan 6 saves the main window and its child windows here
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

    private final Contributions model;
    private final Hooks hooks;
    private final List<ChildWindow> children = new ArrayList<>();
    /** The windows that came to the front, the last one first. */
    private final List<JasperWindow> activations = new ArrayList<>();
    /** The one tab drag of every window this manager opens (workspace tabs spec 12): it reaches them all, top first. */
    private final TabDrag tabDrag = new TabDrag(() -> windowsByActivation().stream().map(JasperWindow::content).toList(),
        (tab, out) -> moveToNewWindow(tab, out.bounds(), out.cursor()));
    private MainWindow main;
    private JasperWindow lastActive;
    private int openings;
    private boolean openingMain;
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
            this::popOutTab, this::movedOut, this::emptyWindow));
        model.panelRoute(this::routePanel);
    }

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
     * The main window, opened first when none is open (workspace tabs spec 6.4: every request restores it first). The
     * tabs a plugin asked for while it was being composed open in it once it shows, or go with it when it fails to
     * open, their failures under its own.
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts; null for where a new shell starts
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
            opened = open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty(),
                Optional.empty()), content -> main = new MainWindow(this, content));
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
     * New Terminal Window (workspace tabs spec 6.5): a child window with a terminal tab in {@code directory}, after the
     * main window is open.
     *
     * @param directory where its terminal tab starts; null for where a new shell starts
     * @return the child window; empty while Jasper quits or every window closes
     */
    public Optional<ChildWindow> openTerminalWindow(Path directory) {
        requireEdt();
        if (ensureMain("menu", null).isEmpty()) return Optional.empty();
        return openChild(false, directory, null, null, null);
    }

    /**
     * Pop Out Tab (parent tabs spec 5): a new pane window takes {@code tab}, its content and session untouched, where its
     * pane is on screen and at the pane's size, with the origin {@code WindowContent.popOutOrigin} gives (workspace plan
     * 7b Rulings 3, 4 and 11). A tab being closed or pinned stays, and so does a child window's only tab: that window
     * already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new pane window; empty when the tab stayed
     */
    public Optional<ChildWindow> popOutTab(Tab tab) {
        requireEdt();
        WindowContent from = leaving(tab);
        Rectangle bounds = from == null ? null : tab.group().flatMap(from::paneOnScreen).orElse(null);
        return popOutTab(tab, bounds, centre(bounds));
    }

    /**
     * As {@link #popOutTab(Tab)}, the pane window's workspace at {@code bounds} on screen, on the display under
     * {@code cursor}.
     *
     * @param tab a tab of one of these windows
     * @param bounds where the pane window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new pane window; empty when the tab stayed
     */
    public Optional<ChildWindow> popOutTab(Tab tab, Rectangle bounds, Point cursor) {
        requireEdt();
        WindowContent from = leaving(tab);
        return from == null ? Optional.empty() : moveToNewChild(tab, bounds, cursor, from.popOutOrigin(tab));
    }

    /**
     * Pop Out Pane (parent tabs spec 5): a new pane window takes every tab of {@code pane}, in their order, showing the one
     * the pane showed, where the pane is on screen and at its size; its origin is where the pane sat
     * ({@code WindowContent.originOf}). A child window's only pane stays, as its only tab does, and a pane holding a tab
     * that is pinned or being closed stays whole (workspace plan 7b Rulings 7 and 8).
     *
     * @param window the window holding the pane
     * @param pane the pane
     * @return the new pane window, holding what arrived; empty when no tab did
     */
    public Optional<ChildWindow> popOutPane(WindowContent window, GroupId pane) {
        requireEdt();
        if (window == null || !window.mayPopOutPane(pane)) return Optional.empty();
        WorkspaceView view = window.parentTabs().holding(pane).orElseThrow();
        var group = view.model().group(pane).orElseThrow();
        List<Tab> moving = group.tabs().stream().map(id -> view.tab(id).orElseThrow()).toList();
        Tab shown = group.selected().flatMap(view::tab).orElse(null);
        Rectangle bounds = window.paneOnScreen(pane).orElse(null);
        Optional<ChildWindow> opened = openChild(true, null, bounds, centre(bounds), window.originOf(pane, true));
        if (opened.isEmpty()) return opened;
        ChildWindow child = opened.get();
        Throwable failure = null;
        for (Tab tab : moving) {
            try {
                model.tabs().move(tab, child.id());
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        // A listener may pin a later tab mid-move, or one tab's move may fail: the pane then stays split across the two
        // windows, each window's ids still unique (parent tabs spec 4), and settle gives the pane window while a tab
        // arrived. The pane window shows the tab the pane showed.
        if (shown != null && child.content().parentTabs().contains(shown)) {
            try {
                model.tabs().select(shown);
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        return settle(child, failure);
    }

    /**
     * Pop Out Parent Tab (parent tabs spec 5): a new full window takes the parent tab {@code parentTab} whole, with its id and
     * layout, where the platform puts it (workspace plan 7b Rulings 10 and 11). A full window's only parent tab stays (spec
     * 5), and so does a parent tab holding a tab that is pinned or being closed.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> popOutParentTab(WindowContent window, ParentTabId parentTab) {
        return popOutParentTab(window, parentTab, null, null);
    }

    /**
     * As {@link #popOutParentTab(WindowContent, ParentTabId)}, the full window's workspace at {@code bounds} on screen, on the
     * display under {@code cursor}.
     *
     * @param window the main window or a full window
     * @param parentTab one of its parent tabs
     * @param bounds where the full window's workspace goes on screen; null for where the platform puts it
     * @param cursor a point on the display it opens on; null exactly when {@code bounds} is
     * @return the new full window; empty when the parent tab stayed
     */
    public Optional<ChildWindow> popOutParentTab(WindowContent window, ParentTabId parentTab, Rectangle bounds, Point cursor) {
        requireEdt();
        if (window == null || !window.mayPopOutParentTab(parentTab)) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, bounds, cursor, null);
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
     * main window it empties shows an empty workspace under a new id. Both windows are main or full windows; a parent tab
     * holding a tab that is pinned or being closed stays. It keeps its maximize too: spec 15 ends a maximize for a tab
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
        if (from == null || to == null || from == to || from.closed() || to.closed() || from.isPaneWindow() || to.isPaneWindow())
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
     * Bring Back (parent tabs spec 5 and 11; workspace plan 7b Rulings 5): a pane window's whole layout returns as a nested
     * split beside its origin pane, on the recorded side with the recorded share, in the parent tab the origin names,
     * whichever main or full window holds it now. It goes beside that parent tab's active pane when the origin pane is
     * gone, and beside the active pane of the main window's active parent tab when no window holds the parent tab. A
     * maximize there ends; the parent tab shows with the pane window's active tab selected, focused and raised, and the
     * pane window closes with its last tab. While one of its tabs is pinned or being closed, nothing moves.
     *
     * @param window a pane window
     * @return the window the panes went to; empty when none arrived
     */
    public Optional<JasperWindow> bringBack(WindowContent window) {
        requireEdt();
        if (window == null || !window.mayBringBack()) return Optional.empty();
        List<Tab> moving = window.tabs();
        Origin origin = window.origin().get();
        Layout panes = window.workspace().model();
        JasperWindow target = holderOf(origin.parentTab()).orElse(null);
        ParentTabId parentTab = origin.parentTab();
        if (target == null) {
            target = ensureMain("menu", null).orElse(null);
            if (target == null) return Optional.empty();
            parentTab = target.content().activeParentTab();
        }
        Layout before = target.content().parentTabs().view(parentTab).orElseThrow().model();
        GroupId beside = before.group(origin.pane()).isPresent() ? origin.pane() : before.activeGroup();
        // A listener may pin a later tab mid-move, or one tab's move may fail: the rest then stays in the pane window, which
        // stays open, and what arrived keeps to the parent tab's own panes (receive), so no id is in two windows (spec 4).
        Layout arranged = before.graft(panes, beside, origin.side(), origin.share());
        return target.content().receive(moving, parentTab, 0, arranged) ? Optional.of(target) : Optional.empty();
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
     * The open main or full window holding the parent tab {@code id}, which follows it between windows (parent tabs spec
     * 11).
     */
    private Optional<JasperWindow> holderOf(ParentTabId id) {
        for (JasperWindow candidate : windows()) {
            WindowContent content = candidate.content();
            // A window that is closing holds its parent tabs no more.
            if (!content.closed() && !content.isPaneWindow() && content.parentTabs().view(id).isPresent())
                return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * Plan 5's tear-out: a new full child window takes {@code tab}, its content and session untouched, its workspace at
     * {@code bounds} on screen, on the display under {@code cursor}. A tab being closed or pinned stays, and so does a
     * child window's only tab.
     *
     * @param tab a tab of one of these windows
     * @param bounds where the new window's workspace goes on screen; null for where the platform puts it
     * @param cursor where the cursor released the tab on screen; null exactly when {@code bounds} is
     * @return the new child window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab, Rectangle bounds, Point cursor) {
        requireEdt();
        return leaving(tab) == null ? Optional.empty() : moveToNewChild(tab, bounds, cursor, null);
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

    /** A new child window takes {@code tab}: a pane window from {@code origin}, or a full window when it is null. */
    private Optional<ChildWindow> moveToNewChild(Tab tab, Rectangle bounds, Point cursor, Origin origin) {
        Optional<ChildWindow> opened = openChild(true, null, bounds, cursor, origin);
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

    /** The middle of {@code bounds} on screen, whose display a window opening there takes; null for null. */
    private static Point centre(Rectangle bounds) {
        return bounds == null ? null : new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
    }

    /**
     * Closes every child window, then the main window, without asking any guard: Quit and going to the background ask
     * first. The closing-all hook hears of it once before anything closes, while every window is whole. Every window
     * closes whatever closing another one throws, and no child window opens meanwhile; the first failure is rethrown once
     * the main window has closed, the others suppressed under it.
     */
    public void closeAll() {
        requireEdt();
        boolean nested = closingAll;
        closingAll = true;
        Throwable failure = null;
        try {
            try {
                if (!isEmpty()) hooks.closingAll().run();
            } catch (RuntimeException | Error thrown) {
                failure = thrown;
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

    /**
     * A child window: a pane window from {@code origin}, made one while it is empty and before its frame exists (parent tabs
     * spec 4), or a full window when it is null.
     */
    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds, Point cursor, Origin origin) {
        // A closing tab's listener asking for a window while every window closes gets none (spec 6.4).
        if (closingAll) return Optional.empty();
        ChildWindow[] made = new ChildWindow[1];
        return open(new Opening(WindowRole.CHILD, empty, directory, "menu", () -> { if (made[0] != null) made[0].emptied(); },
            Optional.ofNullable(bounds).map(Rectangle::new), Optional.ofNullable(cursor).map(Point::new)), content -> {
            if (origin != null) content.paneWindow(origin);
            made[0] = new ChildWindow(this, content);
            children.add(made[0]);
            return made[0];
        });
    }

    /** Composes, frames and shows one window; a failure on the way discards it and propagates. */
    private <W extends JasperWindow> Optional<W> open(Opening opening, Function<WindowContent, W> make) {
        requireEdt();
        openings++;
        W window = null;
        try {
            WindowContent content = hooks.content().apply(opening);
            if (content == null) return Optional.empty();
            // Every window drags tabs with the one drag, which reaches them all (workspace tabs spec 12).
            content.tabDrag(tabDrag);
            // Its pop-outs, Bring Back and Move to Main Window are this manager's (parent tabs spec 5).
            content.manager(this);
            window = make.apply(content);
            window.shell(hooks.frames().apply(window));
            // A torn-out tab's window opens where it was dropped, on the cursor's display (workspace tabs plan 5).
            if (opening.bounds().isPresent())
                window.shell().place().accept(opening.bounds().get(), opening.cursor().orElseThrow());
            window.shell().show().run();
            hooks.opened().accept(window);
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
        return openChild(true, null, null, null, null).map(JasperWindow::id)
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
        hooks.closed().accept(window);
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
