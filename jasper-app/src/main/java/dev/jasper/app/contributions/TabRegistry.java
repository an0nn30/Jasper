package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Every tab of every window (workspace tabs spec 9.1). The application owns one; a window not yet connected to the
 * contributions keeps one of its own. Tabs outlive the windows that show them: moving a tab detaches it from one
 * {@link WorkspaceHost} and attaches it to another, and only {@link #remove(Tab)} disposes it. Close guards are per
 * plugin: a batch is approved by each plugin's guard in turn, and a tab without a guard, such as a terminal tab, needs no
 * approval. EDT only.
 */
public final class TabRegistry {
    private static final System.Logger LOG = System.getLogger(TabRegistry.class.getName());
    private final Map<UUID, WorkspaceHost> hosts = new LinkedHashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, BiFunction<List<Tab>, Boolean, CompletableFuture<Boolean>>> guards = new HashMap<>();
    private Windows windows;
    private List<Tab> pending = List.of();
    private CompletableFuture<Boolean> decision;
    private CompletableFuture<Boolean> closing;

    /**
     * How the registry reaches the windows (workspace tabs spec 6.4 and 6.5); the window manager gives it.
     *
     * @param main the main window's id, restored first when none is open; null while the main window is being composed,
     *     and a plugin's tab asked for then opens there once it is up
     * @param isMain whether a window is the main window
     * @param newWindow moves a tab into a new child window, or leaves it where it is
     * @param movedOut a move of a tab out of this window has ended, whether or not the tab left: the window manager closes
     *     a child window the move left empty now, never in the middle of the move
     */
    public record Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID> movedOut) {
        /** Requires every function. */
        public Windows {
            Objects.requireNonNull(main, "main"); Objects.requireNonNull(isMain, "isMain"); Objects.requireNonNull(newWindow, "newWindow");
            Objects.requireNonNull(movedOut, "movedOut");
        }

        /**
         * Windows as a test plays them, where a move closes no window.
         *
         * @param main the main window's id
         * @param isMain whether a window is the main window
         * @param newWindow moves a tab into a new child window, or leaves it where it is
         */
        public Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow) {
            this(main, isMain, newWindow, window -> { });
        }
    }

    /**
     * Where a plugin's view without a window goes and how a tab moves to a new window or the main window. Until the window
     * manager gives this, the first registered workspace counts as the main window and no tab moves to a new window.
     *
     * @param value the window manager's part
     */
    public void windows(Windows value) { windows = Objects.requireNonNull(value, "value"); }

    /**
     * Whether {@code window} is the main window.
     *
     * @param window a window's id, or null
     * @return true for the main window
     */
    public boolean isMain(UUID window) {
        if (window == null) return false;
        if (windows != null) return windows.isMain().test(window);
        return hosts.keySet().stream().findFirst().map(window::equals).orElse(false);
    }

    /**
     * Registers a window's workspace. Closing the subscription removes, so disposes, the tabs that window still holds,
     * then forgets it, whichever of those removals fail; the first failure is rethrown afterwards.
     */
    public Subscription addHost(WorkspaceHost host) {
        if (hosts.putIfAbsent(host.id(), host) != null) throw new IllegalArgumentException("Duplicate workspace");
        return new Subscription(() -> {
            Throwable failure = null;
            for (var tab : List.copyOf(tabs)) if (host.id().equals(tab.window())) {
                try { remove(tab); }
                catch (RuntimeException | Error thrown) { failure = Tab.keep(failure, thrown); }
            }
            hosts.remove(host.id());
            Tab.rethrow(failure);
        });
    }

    /** @return every tab, in the order the tabs were registered */
    public List<Tab> tabs() { return List.copyOf(tabs); }
    /** @return the plugin tabs, in the order they opened */
    public List<Tab> hosted() { return tabs.stream().filter(tab -> tab.kind() == Tab.Kind.HOSTED).toList(); }
    /** @return every tab {@code window} holds */
    public List<Tab> inWindow(UUID window) { return tabs.stream().filter(tab -> window.equals(tab.window())).toList(); }
    /** @return the tab with this id, while it is open */
    public Optional<Tab> tab(TabId id) {
        for (var tab : tabs) if (tab.id().equals(id)) return Optional.of(tab);
        return Optional.empty();
    }
    /** @return the group that holds the tab in its window, as that window says */
    public Optional<GroupId> groupOf(Tab tab) {
        var host = tab.window() == null ? null : hosts.get(tab.window());
        return host == null ? Optional.empty() : host.groupOf().apply(tab.id());
    }

    /**
     * Registers a tab that {@code window} has opened and places itself, such as a terminal tab; no host is called.
     * Workspace plan 6 restores saved ids through here.
     *
     * @param id the tab's id, fresh or restored
     * @param kind what the tab holds; a plugin's tab opens through {@link #open}
     * @param content its content
     * @param window the window that shows it
     * @return the tab
     */
    public Tab add(TabId id, Tab.Kind kind, TabContent content, UUID window) {
        requireEdt();
        if (kind == Tab.Kind.HOSTED) throw new IllegalArgumentException("A plugin's tab opens through open()");
        if (tab(id).isPresent()) throw new IllegalArgumentException("Tab " + id + " is already registered");
        var tab = new Tab(this, id, kind, "", id.toString(), "", content);
        tabs.add(tab);
        tab.window(Objects.requireNonNull(window, "window"));
        return tab;
    }

    /**
     * Takes over the tabs {@code window} holds in {@code from}, its own registry, as the window joins this one. The tabs
     * keep their identity, content and listeners; {@code from} forgets them, so the window's leaving it closes nothing.
     *
     * @param from the window's own registry
     * @param window the joining window
     */
    public void adopt(TabRegistry from, UUID window) {
        requireEdt();
        if (from == this) return;
        for (var tab : from.inWindow(window)) {
            if (tab(tab.id()).isPresent()) throw new IllegalArgumentException("Tab " + tab.id() + " is already registered");
            from.tabs.remove(tab);
            tab.registry = this;
            tabs.add(tab);
        }
    }

    /** Registers a plugin's close guard, which approves a list of its tabs; {@code true} marks a batch (a window or quit). */
    public Subscription guard(String plugin, BiFunction<List<Tab>, Boolean, CompletableFuture<Boolean>> callback) {
        guards.put(plugin, callback);
        return new Subscription(() -> guards.remove(plugin, callback));
    }

    /**
     * Opens a plugin's view as a tab in {@code owner}'s window, or the main window when {@code owner} is null or not
     * registered, and selects it. A (plugin, key) already open is selected again, and attached to a window again when it
     * lost its own.
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component) {
        requireEdt();
        for (var tab : tabs) if (tab.kind() == Tab.Kind.HOSTED && tab.plugin().equals(plugin) && tab.key().equals(key)) {
            if (tab.window() == null) {
                WorkspaceHost retry = owner != null ? hosts.get(owner) : null;
                if (retry == null) retry = main();
                if (retry == null) { later(tab); return tab; }
                tab.window(retry.id());
                try { retry.attach().accept(tab); }
                catch (RuntimeException | Error failure) {
                    rollbackAttach(retry, tab, failure);
                    tab.window(null);
                    throw failure;
                }
            }
            select(tab);
            return tab;
        }
        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        WorkspaceHost host = owner != null ? hosts.get(owner) : null;
        if (host == null) host = main();
        tabs.add(tab);
        if (host == null) { later(tab); return tab; }
        tab.window(host.id());
        try { host.attach().accept(tab); }
        catch (RuntimeException | Error failure) {
            rollbackAttach(host, tab, failure);
            tabs.remove(tab);
            try { tab.dispose(); }
            catch (RuntimeException | Error cleanup) { Tab.keep(failure, cleanup); }
            throw failure;
        }
        select(tab);
        return tab;
    }

    private static void rollbackAttach(WorkspaceHost host, Tab tab, Throwable failure) {
        try { host.detach().accept(tab); }
        catch (RuntimeException | Error cleanup) { Tab.keep(failure, cleanup); }
    }

    /**
     * The main window's workspace, restored first by the window manager when none is open (workspace tabs spec 6.4); null
     * while the main window is being composed.
     */
    private WorkspaceHost main() {
        UUID id = windows != null ? windows.main().get()
            : hosts.keySet().stream().findFirst().orElseThrow(() -> new IllegalStateException("No workspace host available"));
        if (id == null) return null;
        return Objects.requireNonNull(hosts.get(id), "The main window registered no workspace");
    }

    /**
     * Attaches a tab asked for while the main window was being composed (a panel factory or a {@code WINDOW_OPENED}
     * listener, say) once the main window is up, on a later EDT turn (workspace tabs spec 6.4).
     */
    private void later(Tab tab) {
        SwingUtilities.invokeLater(() -> {
            if (tab.closed() || !tabs.contains(tab) || tab.window() != null) return;
            WorkspaceHost host = main();
            if (host == null) { later(tab); return; }
            tab.window(host.id());
            try { host.attach().accept(tab); }
            catch (RuntimeException | Error failure) {
                rollbackAttach(host, tab, failure);
                tab.window(null);
                throw failure;
            }
            select(tab);
        });
    }

    /** Shows the tab in its window and raises the window. */
    public void select(Tab tab) {
        if (tab.closed()) return;
        var host = hosts.get(tab.window());
        if (host != null) { host.select().accept(tab); host.toFront().run(); }
    }

    /**
     * Move Tab to New Window (workspace tabs spec 6.5): the window manager opens a child window for the tab, or leaves it
     * where it is (a child window's only tab). A pinned tab or one being closed stays.
     *
     * @param tab the tab
     */
    public void moveToNewWindow(Tab tab) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || windows == null) return;
        windows.newWindow().accept(tab);
    }

    /**
     * Move Tab to Main Window (workspace tabs spec 6.5): the end of the main window's active group. A pinned tab, one being
     * closed or one the main window holds stays.
     *
     * @param tab the tab
     */
    public void moveToMainWindow(Tab tab) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || isMain(tab.window())) return;
        WorkspaceHost target = main();
        if (target != null) move(tab, target.id());
    }

    /**
     * Moves a tab of any kind to {@code window}, at the end of that window's active group, and selects it there; its
     * content and session are untouched. A pinned tab, one being closed or one already there stays.
     *
     * @param tab the tab
     * @param window the window to move it to
     * @throws IllegalArgumentException when no workspace {@code window} is registered
     */
    public void move(Tab tab, UUID window) {
        requireEdt();
        var next = hosts.get(Objects.requireNonNull(window, "window"));
        if (next == null) throw new IllegalArgumentException("No workspace " + window);
        if (tab.closed() || tab.pinned() || pending.contains(tab) || window.equals(tab.window())) return;
        var old = hosts.get(tab.window());
        if (old == null) return;
        transfer(tab, old, next);
    }

    /**
     * Detaches the tab from {@code old} and attaches it to {@code next}, then selects it. When {@code next} fails, the tab
     * goes back to {@code old}, or another window, or stays in no window, and the failure propagates. When {@code old}
     * fails to let the tab go, the move still goes on, so the tab is shown in the window it names, and that failure
     * propagates afterwards. The window the tab left hears of the move last (workspace plan 4a); a failure there is kept
     * behind the move's own.
     */
    private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next) {
        // Change ownership before removal: an emptied old window may unregister itself synchronously.
        tab.active(false);
        tab.window(next.id());
        tab.movingFrom = old.id();
        // The first failure is the move's own: the source's, else the destination's; later ones are kept behind it.
        Throwable failure = null;
        boolean arrived = false;
        try {
            try { old.detach().accept(tab); }
            catch (RuntimeException | Error leaving) { failure = leaving; }
            try { next.attach().accept(tab); arrived = true; }
            catch (RuntimeException | Error attaching) {
                failure = Tab.keep(failure, attaching);
                rollbackAttach(next, tab, attaching);
                if (tabs.contains(tab) && !tab.closed()) {
                    var recovery = new ArrayList<WorkspaceHost>();
                    if (hosts.get(old.id()) == old) recovery.add(old);
                    for (var host : hosts.values())
                        if (host != old && host != next) recovery.add(host);
                    boolean restored = false;
                    for (var host : recovery) {
                        tab.window(host.id());
                        try { host.attach().accept(tab); restored = true; break; }
                        catch (RuntimeException | Error recoveryFailure) {
                            rollbackAttach(host, tab, recoveryFailure);
                            failure = Tab.keep(failure, recoveryFailure);
                        }
                    }
                    if (!restored) tab.window(null);
                }
            }
        } finally {
            tab.movingFrom = null;
        }
        // The window the tab left hears of it only now: a child window the move emptied closes after the move has
        // reported itself, never midway, so a failed attach could still go back to it (workspace plan 4a).
        if (windows != null) {
            try { windows.movedOut().accept(old.id()); }
            catch (RuntimeException | Error closing) { failure = Tab.keep(failure, closing); }
        }
        if (arrived) {
            try { select(tab); }
            catch (RuntimeException | Error selecting) { failure = Tab.keep(failure, selecting); }
        }
        Tab.rethrow(failure);
    }

    /**
     * Disposes the tab at once, without asking a guard, and takes it out of its window. Whatever a listener or the content
     * throws, the window still lets go of the tab; the first failure is rethrown afterwards.
     */
    public void remove(Tab tab) {
        requireEdt();
        if (!tabs.remove(tab)) return;
        var host = hosts.get(tab.window());
        Throwable failure = null;
        try { tab.dispose(); }
        catch (RuntimeException | Error thrown) { failure = thrown; }
        if (host != null) {
            try { host.detach().accept(tab); }
            catch (RuntimeException | Error thrown) { failure = Tab.keep(failure, thrown); }
        }
        Tab.rethrow(failure);
    }

    /**
     * Removes each tab as {@link #remove(Tab)} does, whatever removing another one throws; the first failure is rethrown
     * afterwards, the others suppressed under it.
     *
     * @param batch the tabs to remove
     */
    public void removeAll(List<Tab> batch) {
        Throwable failure = null;
        for (var tab : batch) {
            try { remove(tab); }
            catch (RuntimeException | Error thrown) { failure = Tab.keep(failure, thrown); }
        }
        Tab.rethrow(failure);
    }

    public CompletableFuture<Boolean> approve(List<Tab> requested) { return approve(requested, true); }

    private CompletableFuture<Boolean> approve(List<Tab> requested, boolean batch) {
        requireEdt();
        var live = requested.stream().filter(tab -> !tab.closed()).distinct().toList();
        if (decision != null) return pending.equals(live) ? decision : CompletableFuture.completedFuture(false);
        if (live.isEmpty()) return CompletableFuture.completedFuture(true);
        if (live.stream().anyMatch(Tab::pinned)) return CompletableFuture.completedFuture(false);
        pending = live;
        var result = new CompletableFuture<Boolean>();
        decision = result;
        var groups = new LinkedHashMap<String, List<Tab>>();
        for (var tab : live) groups.computeIfAbsent(tab.plugin(), plugin -> new ArrayList<>()).add(tab);
        var iterator = groups.entrySet().iterator();
        class Next implements Runnable {
            @Override public void run() {
                if (!iterator.hasNext()) { finish(true); return; }
                var group = iterator.next();
                var guard = guards.get(group.getKey());
                if (guard == null) { run(); return; }
                try {
                    Objects.requireNonNull(guard.apply(List.copyOf(group.getValue()), batch))
                        .whenComplete((allowed, error) -> onEdt(() -> {
                            if (error != null || !Boolean.TRUE.equals(allowed)) finish(false);
                            else run();
                        }));
                } catch (RuntimeException | Error failure) {
                    LOG.log(System.Logger.Level.WARNING, "A close guard failed", failure);
                    finish(false);
                }
            }
            void finish(boolean allowed) {
                pending = List.of();
                decision = null;
                result.complete(allowed);
            }
        }
        new Next().run();
        return result;
    }

    /**
     * Asks the guards about these tabs as one decision, then removes them all, or none. A tab whose removal throws never
     * keeps the others open or the next close waiting: the failure is logged as a warning, since the decision may arrive
     * after this method has returned.
     */
    public CompletableFuture<Boolean> requestClose(List<Tab> requested) {
        requireEdt();
        if (decision != null && closing == null) return CompletableFuture.completedFuture(false);
        if (closing != null) return pending.equals(requested) ? closing : CompletableFuture.completedFuture(false);
        var result = new CompletableFuture<Boolean>();
        closing = result;
        approve(requested, false).whenComplete((allowed, error) -> onEdt(() -> {
            boolean approved = error == null && Boolean.TRUE.equals(allowed);
            try {
                if (approved) removeAll(requested);
            } catch (RuntimeException | Error failure) {
                // Thrown here, it would end in the decision's dependent future, which nobody reads.
                LOG.log(System.Logger.Level.WARNING, "A tab failed to close", failure);
            } finally {
                closing = null;
                result.complete(approved);
            }
        }));
        return result;
    }

    /** Existing synchronous native quit/restart guards pump EDT events while asynchronous saves complete. */
    public boolean mayClose(List<Tab> requested) {
        // A nested native close must not wait for the outer modal guard's own stack to unwind.
        if (decision != null) return false;
        return await(approve(requested));
    }

    /** As {@link #mayClose(List)}, refused also when the tabs changed while the guards decided. */
    public boolean mayClose(Supplier<List<Tab>> current) {
        var requested = List.copyOf(current.get());
        return mayClose(requested) && requested.equals(current.get());
    }

    private static boolean await(CompletableFuture<Boolean> future) {
        if (future.isDone()) return future.getNow(false);
        var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        future.whenComplete((value, error) -> SwingUtilities.invokeLater(loop::exit));
        loop.enter();
        return future.getNow(false);
    }
    private static void onEdt(Runnable run) {
        if (SwingUtilities.isEventDispatchThread()) run.run(); else SwingUtilities.invokeLater(run);
    }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Tabs belong to the EDT");
    }
}
