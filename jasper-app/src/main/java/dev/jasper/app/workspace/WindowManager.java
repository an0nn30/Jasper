package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
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
     * @param bounds where a child window opens on screen, a torn-out tab's (workspace tabs plan 5); empty for where the
     *     platform puts it
     */
    public record Opening(WindowRole role, boolean empty, Path directory, String trigger, Runnable emptied,
                          Optional<Rectangle> bounds) {
        /** Requires the role, the trigger, the callback and the bounds. */
        public Opening {
            Objects.requireNonNull(role, "role"); Objects.requireNonNull(trigger, "trigger"); Objects.requireNonNull(emptied, "emptied");
            Objects.requireNonNull(bounds, "bounds");
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
    private MainWindow main;
    private JasperWindow lastActive;
    private int openings;
    private boolean openingMain;
    /** While {@link #closeAll()} runs: no child window opens, so none outlives the main window. */
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
        // A plugin's view opened with no window goes to the main window, restored first when none is open (spec 6.4).
        model.tabs().setFactory(separate -> (separate ? openChild(true, null, null).map(JasperWindow::id) : ensureMain("plugin", null)
            .map(JasperWindow::id)).orElseThrow(() -> new IllegalStateException("Jasper is quitting")));
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

    /** @return the window that came to the front last while it is open; else the main window; else the first child window */
    public Optional<JasperWindow> lastActive() {
        if (lastActive != null && !lastActive.closed()) return Optional.of(lastActive);
        if (main != null) return Optional.of(main);
        return children.isEmpty() ? Optional.empty() : Optional.of(children.getFirst());
    }

    /**
     * The main window, opened first when none is open (workspace tabs spec 6.4: every request restores it first).
     *
     * @param trigger what asked, for the new window's start-up timing line
     * @param directory where a new main window's terminal tab starts; null for where a new shell starts
     * @return the main window; empty while Jasper quits or the main window closes
     * @throws IllegalStateException when asked again while the main window is still opening
     */
    public Optional<MainWindow> ensureMain(String trigger, Path directory) {
        requireEdt();
        // A main window that is closing is not absent: no second one opens while it goes (workspace tabs spec 6.1).
        if (main != null) return main.closed() ? Optional.empty() : Optional.of(main);
        if (openingMain) throw new IllegalStateException("The main window is still opening");
        openingMain = true;
        try {
            return open(new Opening(WindowRole.MAIN, false, directory, trigger, () -> { }, Optional.empty()),
                content -> main = new MainWindow(this, content));
        } finally {
            openingMain = false;
        }
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
        return openChild(false, directory, null);
    }

    /**
     * Move Tab to New Window (workspace tabs spec 6.5): a new child window takes {@code tab}, its content and session
     * untouched. A tab being closed or pinned stays, and so does a child window's only tab: that window already is its own.
     *
     * @param tab a tab of one of these windows
     * @return the new child window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab) { return moveToNewWindow(tab, null); }

    /**
     * As {@link #moveToNewWindow(Tab)}, the new child window at {@code bounds} on screen: a tab torn out of its window
     * (workspace tabs plan 5).
     *
     * @param tab a tab of one of these windows
     * @param bounds where the new window goes on screen; null for where the platform puts it
     * @return the new child window; empty when the tab stayed
     */
    public Optional<ChildWindow> moveToNewWindow(Tab tab, Rectangle bounds) {
        requireEdt();
        if (tab == null || tab.closed() || tab.pinned()) return Optional.empty();
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || !from.get().isMain() && from.get().content().tabCount() < 2) return Optional.empty();
        Optional<ChildWindow> opened = openChild(true, null, bounds);
        if (opened.isEmpty()) return opened;
        ChildWindow child = opened.get();
        Throwable failure = null;
        try {
            model.tabs().move(tab, child.id());
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
        }
        // A move the registry refused, or one that failed, leaves the new window empty: it goes.
        if (!child.closed() && child.content().tabCount() == 0) {
            try {
                child.close();
            } catch (RuntimeException | Error thrown) {
                failure = keep(failure, thrown);
            }
        }
        rethrow(failure);
        return child.closed() ? Optional.empty() : opened;
    }

    /**
     * Move Tab to Main Window (workspace tabs spec 6.5): {@code tab} goes to the main window's active group; its child
     * window closes when that was its last tab.
     *
     * @param tab a tab of a child window
     */
    public void moveToMainWindow(Tab tab) {
        requireEdt();
        if (tab == null || tab.closed()) return;
        Optional<JasperWindow> from = window(tab.window());
        if (from.isEmpty() || from.get().isMain()) return;
        ensureMain("menu", null).ifPresent(target -> model.tabs().move(tab, target.id()));
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

    private Optional<ChildWindow> openChild(boolean empty, Path directory, Rectangle bounds) {
        // A closing tab's listener asking for a window while every window closes gets none (spec 6.4).
        if (closingAll) return Optional.empty();
        ChildWindow[] made = new ChildWindow[1];
        return open(new Opening(WindowRole.CHILD, empty, directory, "menu", () -> { if (made[0] != null) made[0].emptied(); },
            Optional.ofNullable(bounds).map(Rectangle::new)), content -> {
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
            window = make.apply(content);
            window.shell(hooks.frames().apply(window));
            // A torn-out tab's window opens where it was dropped (workspace tabs plan 5).
            opening.bounds().ifPresent(window.shell().place());
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

    /** A child window's panel request goes to the main window, which has the dock, and brings it forward to show it. */
    private Contributions.PanelRequest routePanel(Contributions.PanelRequest request) {
        if (main == null || children.stream().noneMatch(child -> child.id().equals(request.windowId()))) return request;
        if (request.op() != Contributions.PanelRequest.Op.HIDE) main.toFront();
        return new Contributions.PanelRequest(main.id(), request.panelId(), request.op());
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
