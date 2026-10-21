package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.layout.TabRestore;
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
import java.util.function.Function;
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
    /** The tabs asked for while the main window was being composed, in the order they were asked for. */
    private final List<Tab> waiting = new ArrayList<>();
    private final Map<String, BiFunction<List<Tab>, Boolean, CompletableFuture<Boolean>>> guards = new HashMap<>();
    /** The owner of Jasper's own content tabs, such as the Errors tab; a plugin's id is never {@code jasper}. */
    public static final String APP_OWNER = "jasper";
    private Windows windows;
    private List<Tab> pending = List.of();
    private CompletableFuture<Boolean> decision;
    private CompletableFuture<Boolean> closing;
    /** Each plugin's restorers by kind (workspace tabs spec 13). */
    private final Map<String, Map<String, Function<Restoring, Optional<String>>>> restorers = new HashMap<>();
    /** The saved tab being rebuilt: the next tab its plugin opens takes its id and window. */
    private Slot slot;
    /** Told when a tab's restore state changes, which is saved as a layout change is. */
    Runnable restoreChanged = () -> { };

    /** A saved tab being rebuilt, and the tab its plugin opened for it so far. */
    private static final class Slot {
        final TabId id; final UUID window; final String plugin; Tab taken;
        Slot(TabId id, UUID window, String plugin) { this.id = id; this.window = window; this.plugin = plugin; }
    }

    /**
     * A saved tab its plugin rebuilds (workspace tabs spec 13).
     *
     * @param window the window being restored
     * @param title the tab's title when it was saved
     * @param state the plugin's restorer kind and the tab's restore state
     */
    public record Restoring(UUID window, String title, TabRestore state) {
        /** Requires every value. */
        public Restoring {
            Objects.requireNonNull(window, "window"); Objects.requireNonNull(title, "title"); Objects.requireNonNull(state, "state");
        }
    }

    /**
     * What rebuilding a saved tab gave (workspace tabs spec 13).
     *
     * @param tab the tab its plugin opened, with the saved id, in the window being restored, which places it
     * @param problem why there is no tab, as its placeholder words it after the plugin's name; empty with a tab
     */
    public record Restored(Optional<Tab> tab, String problem) {
        /** Requires both values. */
        public Restored { Objects.requireNonNull(tab, "tab"); Objects.requireNonNull(problem, "problem"); }
    }

    /** Where {@link #open} puts a new plugin's tab: the app's twin of the SDK's {@code TabPlacement} (workspace tabs spec 14). */
    public enum Placement {
        /** In a new tab of the window, at the end of its strip and shown (tabs and panes spec 13). */
        NEW_TAB,
        /** In a new pane to the right of the window's focused pane. */
        SPLIT_RIGHT,
        /** In a new pane below the window's focused pane. */
        SPLIT_DOWN,
        /** In a new child window of its own, opened empty for it: the owner's window is not touched. */
        NEW_WINDOW
    }

    /**
     * How the registry reaches the windows (workspace tabs spec 6.4 and 6.5); the window manager gives it.
     *
     * @param main the main window's id, restored first when none is open; null while the main window is being composed,
     *     and a plugin's tab asked for then opens there once it is up
     * @param isMain whether a window is the main window
     * @param newWindow moves a tab into a new full window of its own, or leaves it where it is
     * @param movedOut a move of a tab out of this window has ended, whether or not the tab left, or a tab placed in it
     *     failed to show: the window manager closes a child window left empty now, never in the middle of the move
     * @param emptyWindow opens an empty child window for a tab placed in a new window and gives its id; null when none
     *     opens (the main window is being composed, or a test plays the windows), and the tab then opens where an
     *     {@link Placement#NEW_TAB} one would
     */
    public record Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID> movedOut,
                          Supplier<UUID> emptyWindow) {
        /** Requires every function. */
        public Windows {
            Objects.requireNonNull(main, "main"); Objects.requireNonNull(isMain, "isMain"); Objects.requireNonNull(newWindow, "newWindow");
            Objects.requireNonNull(movedOut, "movedOut"); Objects.requireNonNull(emptyWindow, "emptyWindow");
        }

        /**
         * Windows as a test plays them, where a move closes no window.
         *
         * @param main the main window's id
         * @param isMain whether a window is the main window
         * @param newWindow moves a tab into a new full window of its own, or leaves it where it is
         */
        public Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow) {
            this(main, isMain, newWindow, window -> { }, () -> null);
        }

        /**
         * Windows as a test plays them, where no window opens for a tab placed in a new window: it opens where an
         * {@link Placement#NEW_TAB} one would.
         *
         * @param main the main window's id
         * @param isMain whether a window is the main window
         * @param newWindow moves a tab into a new full window of its own, or leaves it where it is
         * @param movedOut a move of a tab out of a window has ended, whether or not the tab left
         */
        public Windows(Supplier<UUID> main, Predicate<UUID> isMain, Consumer<Tab> newWindow, Consumer<UUID> movedOut) {
            this(main, isMain, newWindow, movedOut, () -> null);
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
     * Opens a plugin's view in a new tab of {@code owner}'s window, or of the main window when {@code owner} is null or
     * not registered, and shows it (tabs and panes spec 13). A (plugin, key) already open is selected again, and
     * attached to a window again when it lost its own. While the main window is being composed, a tab for it waits
     * ({@link #mainOpened}, {@link #mainFailed}).
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component) {
        return open(plugin, key, title, owner, component, Placement.NEW_TAB);
    }

    /**
     * As {@link #open(String, String, String, UUID, JComponent)}, the new tab where {@code placement} puts it: in a new
     * tab of that window or a new pane beside its focused pane, or in a new child window of its own (workspace tabs spec
     * 14). A (plugin, key) already open is selected where it is. One asked for while the main window is being composed
     * waits for it and opens in a new tab of it, whatever its placement.
     */
    public Tab open(String plugin, String key, String title, UUID owner, JComponent component, Placement placement) {
        requireEdt();
        Objects.requireNonNull(placement, "placement");
        for (var tab : tabs) if (tab.kind() == Tab.Kind.HOSTED && tab.plugin().equals(plugin) && tab.key().equals(key)) {
            if (tab.window() == null) {
                // While a saved tab is rebuilt there, the window being restored takes no other tab (workspace plan 6b Rulings 24).
                WorkspaceHost retry = owner != null && !restoring(owner) ? hosts.get(owner) : null;
                if (retry == null) retry = main();
                if (retry == null) { waitForMain(tab); return tab; }
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
        if (slot != null && slot.taken == null && slot.plugin.equals(plugin) && tab(slot.id).isEmpty()) {
            // A saved tab its plugin's restorer rebuilds (workspace tabs spec 13): under its saved id, straight into the
            // window being restored, as add registers a restored terminal tab; that window places it with its other saved
            // tabs. No placement applies, and it never waits for a main window being composed (workspace plan 6b Rulings 4
            // and 23).
            var restored = new Tab(this, slot.id, Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
            tabs.add(restored);
            restored.window(slot.window);
            slot.taken = restored;
            return restored;
        }
        var tab = new Tab(this, TabId.random(), Tab.Kind.HOSTED, plugin, key, title, new HostedContent(Objects.requireNonNull(component)));
        // While a saved tab is rebuilt there, the window being restored takes no other tab: it goes where an owner-less
        // tab goes (workspace plan 6b Rulings 24).
        WorkspaceHost host = owner != null && !restoring(owner) ? hosts.get(owner) : null;
        // A new child window of its own, opened empty for the tab: the owner's window is not touched (workspace tabs plan 4b).
        UUID child = placement == Placement.NEW_WINDOW && windows != null ? windows.emptyWindow().get() : null;
        if (child != null) host = Objects.requireNonNull(hosts.get(child), "The new window registered no workspace");
        if (host == null) host = main();
        tabs.add(tab);
        if (host == null) { waitForMain(tab); return tab; }
        tab.window(host.id());
        try {
            switch (placement) {
                case SPLIT_RIGHT -> host.attachBeside().attach(tab, null, Side.RIGHT);
                case SPLIT_DOWN -> host.attachBeside().attach(tab, null, Side.DOWN);
                case NEW_TAB, NEW_WINDOW -> host.attach().accept(tab);
            }
        }
        catch (RuntimeException | Error failure) {
            rollbackAttach(host, tab, failure);
            tabs.remove(tab);
            try { tab.dispose(); }
            catch (RuntimeException | Error cleanup) { Tab.keep(failure, cleanup); }
            // The child window opened for the tab is left empty: it closes, its failure kept behind the attach's.
            if (child != null) {
                try { windows.movedOut().accept(child); }
                catch (RuntimeException | Error closing) { Tab.keep(failure, closing); }
            }
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

    /** A tab asked for while the main window is being composed (a panel factory, say) waits for it (spec 6.4). */
    private void waitForMain(Tab tab) { if (!waiting.contains(tab)) waiting.add(tab); }

    /**
     * The main window has opened (workspace tabs spec 6.4): the tabs that waited for it open there, in the order they
     * were asked for, and the last one that opened is selected. One that fails to open there is removed, so disposed;
     * its failure, like a failing selection's, is logged as a warning, since its caller has already returned, and keeps
     * no other tab out. When the main window closes again while they open, the tabs not yet shown wait for the next one.
     *
     * @param main the main window's id
     */
    public void mainOpened(UUID main) {
        requireEdt();
        Objects.requireNonNull(main, "main");
        var batch = List.copyOf(waiting);
        waiting.clear();
        if (!batch.isEmpty()) Objects.requireNonNull(hosts.get(main), "The main window registered no workspace");
        Tab opened = null;
        for (var tab : batch) {
            // One closed, or given a window of its own meanwhile, no longer waits.
            if (tab.closed() || !tabs.contains(tab) || tab.window() != null) continue;
            // Looked up for each tab: an earlier one's opening may have closed the main window again.
            WorkspaceHost host = hosts.get(main);
            if (host == null) { waitForMain(tab); continue; }
            try {
                tab.window(host.id());
                host.attach().accept(tab);
                opened = tab;
            } catch (RuntimeException | Error failure) {
                rollbackAttach(host, tab, failure);
                tabs.remove(tab);
                try { tab.dispose(); }
                catch (RuntimeException | Error cleanup) { Tab.keep(failure, cleanup); }
                LOG.log(System.Logger.Level.WARNING, "A tab that waited for the main window failed to open there",
                    failure);
            }
        }
        if (opened == null) return;
        try { select(opened); }
        catch (RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "A tab that waited for the main window failed to be selected there",
                failure);
        }
    }

    /**
     * The main window failed to open (workspace tabs spec 6.4): the tabs that waited for it are removed, so disposed,
     * whatever removing another one throws, and nothing asks for the main window again for them.
     *
     * @param failure the main window's failure, which keeps theirs suppressed under it; the window manager rethrows it
     */
    public void mainFailed(Throwable failure) {
        requireEdt();
        Objects.requireNonNull(failure, "failure");
        var batch = List.copyOf(waiting);
        waiting.clear();
        for (var tab : batch) {
            try { remove(tab); }
            catch (RuntimeException | Error thrown) { Tab.keep(failure, thrown); }
        }
    }

    /** Shows the tab in its window and raises the window. */
    public void select(Tab tab) {
        if (tab.closed()) return;
        var host = hosts.get(tab.window());
        if (host != null) { host.select().accept(tab); host.toFront().run(); }
    }

    /**
     * Whether a move would take {@code tab}: one of this registry's, open, neither pinned nor being closed, nor a restorer's
     * item while its restore runs. A parent tab moves whole or not at all (workspace plan 7b Rulings 7).
     *
     * @param tab the tab
     * @return whether it may move
     */
    public boolean movable(Tab tab) { return splittable(tab) && !tab.pinned() && !held(tab); }

    /**
     * Whether {@code tab} is the item a restorer opened while its restore still runs: it stays in the window being
     * restored, which places it (workspace plan 6b Rulings 30; 6b Task 1's review).
     */
    private boolean held(Tab tab) { return slot != null && slot.taken == tab; }

    /**
     * Whether a pane may open beside {@code tab}, as {@code ContentTab.split} asks: one of this registry's, open and not
     * being closed, the part of {@link #movable} a split shares (a terminal opening in a window whose close is pending
     * would surprise the user).
     *
     * @param tab the tab
     * @return whether a split beside it may open
     */
    public boolean splittable(Tab tab) { return tabs.contains(tab) && !tab.closed() && !pending.contains(tab); }

    /**
     * {@code ContentTab.moveToNewWindow()} (tabs and panes spec 13): the window manager opens a full window holding the
     * tab, or leaves it where it is (a child window's only tab). A pinned tab, one being closed or a restorer's item while
     * its restore runs stays.
     *
     * @param tab the tab
     */
    public void moveToNewWindow(Tab tab) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || held(tab) || windows == null) return;
        windows.newWindow().accept(tab);
    }

    /**
     * {@code ContentTab.moveToMainWindow()} (workspace tabs spec 6.5): the end of the active pane of the main window's
     * shown parent tab. A pinned tab, one being closed, a restorer's item while its restore runs or one the main window
     * holds stays.
     *
     * @param tab the tab
     */
    public void moveToMainWindow(Tab tab) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || held(tab) || isMain(tab.window())) return;
        WorkspaceHost target = main();
        if (target != null) move(tab, target.id());
    }

    /**
     * Split with a choice's Terminal Right and Terminal Down beside {@code tab}, as {@code ContentTab.split} asks (tabs
     * and panes spec 8; workspace plan 8b Rulings 18): its window selects it and opens a terminal in a new pane on
     * {@code side} of its pane. A tab that is not {@link #splittable}, or one no window holds, opens nothing.
     *
     * @param tab the tab
     * @param side where the new pane goes
     * @return whether its window opened the terminal, after it did; false when nothing opened
     */
    public boolean splitTerminal(Tab tab, Side side) {
        requireEdt();
        Objects.requireNonNull(side, "side");
        var host = splittable(tab) ? hosts.get(tab.window()) : null;
        return host != null && host.splitTerminal().test(tab, side);
    }

    /**
     * Whether Move Pane to New Tab would move {@code tab} (tabs and panes spec 9): a tab that may move ({@link #movable})
     * whose window's tab holds another pane.
     *
     * @param tab the tab
     * @return whether it would move
     */
    public boolean mayMoveToNewTab(Tab tab) {
        requireEdt();
        var host = movable(tab) ? hosts.get(tab.window()) : null;
        return host != null && host.mayMoveToNewTab().test(tab);
    }

    /**
     * {@code ContentTab.moveToNewTab()} (tabs and panes spec 9): the tab's pane leaves its tab for a new tab right after
     * it, in its window, which shows it; nothing when {@link #mayMoveToNewTab} says no.
     *
     * @param tab the tab
     */
    public void moveToNewTab(Tab tab) {
        if (mayMoveToNewTab(tab)) hosts.get(tab.window()).moveToNewTab().accept(tab);
    }

    /**
     * Moves a tab of any kind to {@code window}, in a new tab of that window, and selects it there; its
     * content and session are untouched. A pinned tab, one being closed, a restorer's item while its restore runs or one
     * already there stays.
     *
     * @param tab the tab
     * @param window the window to move it to
     * @throws IllegalArgumentException when no workspace {@code window} is registered
     */
    public void move(Tab tab, UUID window) { move(tab, window, next -> next.attach().accept(tab)); }

    /**
     * As {@link #move(Tab, UUID)}, into a new pane on {@code side} of the pane {@code group} in that window: a pane dragged
     * beside another window's pane (tabs and panes spec 7).
     *
     * @param tab the tab
     * @param window the window to move it to
     * @param group a pane of that window
     * @param side where the new pane goes
     * @throws IllegalArgumentException when no workspace {@code window} is registered
     */
    public void move(Tab tab, UUID window, GroupId group, Side side) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(side, "side");
        move(tab, window, next -> next.attachBeside().attach(tab, group, side));
    }

    /** Moves {@code tab} to {@code window}, where {@code attach} shows it. */
    private void move(Tab tab, UUID window, Consumer<WorkspaceHost> attach) {
        requireEdt();
        var next = hosts.get(Objects.requireNonNull(window, "window"));
        if (next == null) throw new IllegalArgumentException("No workspace " + window);
        if (tab.closed() || tab.pinned() || pending.contains(tab) || held(tab) || window.equals(tab.window())) return;
        var old = hosts.get(tab.window());
        if (old == null) return;
        transfer(tab, old, next, attach);
    }

    /**
     * Detaches the tab from {@code old} and attaches it to {@code next} as {@code attach} does, then selects it. A
     * change listener that throws as the tab takes {@code next} leaves it in {@code old}, and its failure propagates.
     * When {@code next} fails, the tab goes back to {@code old}, or another window, or stays in no window, and the
     * failure propagates. When {@code old} fails to let the tab go, the move still goes on, so the tab is shown in the
     * window it names, and that failure propagates afterwards. The window the tab left hears of the move after it, then
     * the tab is selected (workspace plan 4a); a failure of either is kept behind the move's own.
     */
    private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next, Consumer<WorkspaceHost> attach) {
        // Change ownership before removal: an emptied old window may unregister itself synchronously.
        boolean wasActive = tab.active();
        tab.active(false);
        try { tab.window(next.id()); }
        catch (RuntimeException | Error refused) {
            // The tab never left: it names the window that holds it again (workspace plan 4a's final review), as active as
            // it was (its re-review).
            try { tab.window(old.id()); }
            catch (RuntimeException | Error again) { Tab.keep(refused, again); }
            try { tab.active(wasActive); }
            catch (RuntimeException | Error again) { Tab.keep(refused, again); }
            throw refused;
        }
        tab.movingFrom = old.id();
        // The first failure is the move's own: the source's, else the destination's; later ones are kept behind it.
        Throwable failure = null;
        boolean arrived = false;
        try {
            try { old.detach().accept(tab); }
            catch (RuntimeException | Error leaving) { failure = leaving; }
            try { attach.accept(next); arrived = true; }
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
            catch (RuntimeException | Error closeFailure) { failure = Tab.keep(failure, closeFailure); }
        }
        if (arrived) {
            try { select(tab); }
            catch (RuntimeException | Error selecting) { failure = Tab.keep(failure, selecting); }
        }
        Tab.rethrow(failure);
    }

    /**
     * Registers how {@code plugin} rebuilds its saved tabs of {@code kind} (workspace tabs spec 13).
     *
     * @param plugin the plugin
     * @param kind the restorer kind
     * @param restorer runs the plugin's restorer and gives back why it did not restore the tab, if it said or threw why
     * @return the registration
     * @throws IllegalArgumentException when the plugin already has a restorer for {@code kind}
     */
    public Subscription restorer(String plugin, String kind, Function<Restoring, Optional<String>> restorer) {
        requireEdt();
        Objects.requireNonNull(restorer, "restorer");
        var byKind = restorers.computeIfAbsent(plugin, ignored -> new HashMap<>());
        if (byKind.putIfAbsent(kind, restorer) != null) throw new IllegalArgumentException(plugin + " already restores " + kind + " tabs");
        return new Subscription(() -> {
            var current = restorers.get(plugin);
            if (current != null && current.remove(kind, restorer) && current.isEmpty()) restorers.remove(plugin);
        });
    }

    /**
     * Whether {@code plugin} rebuilds saved tabs of {@code kind} now: only such a tab is saved (workspace plan 2 Rulings 20).
     *
     * @param plugin the plugin
     * @param kind the restorer kind
     * @return true while its restorer is registered
     */
    public boolean restores(String plugin, String kind) { return restorers.getOrDefault(plugin, Map.of()).containsKey(kind); }

    /**
     * Tells {@code listener} whenever a tab's restore state changes (workspace tabs spec 13).
     *
     * @param listener told on the EDT; it replaces the one before
     */
    public void onRestoreChanged(Runnable listener) { restoreChanged = Objects.requireNonNull(listener, "listener"); }

    /**
     * Rebuilds one saved tab through its plugin's restorer (workspace tabs spec 13). The tab the plugin opens meanwhile
     * takes {@code id} and the window being restored, whatever window it named, and is not shown: that window places it.
     * It stays there while the restorer runs, whatever move it asks for, and closes when {@code restorer} throws, before
     * the failure goes on.
     *
     * @param id the saved tab's id
     * @param plugin the plugin that saved it
     * @param saved the window, the saved title and the restore state
     * @return the tab, or why there is none: a restore already running (a restorer that restores again), an id already
     *     taken, no restorer for the kind, what the restorer said or threw, or that it opened no tab
     */
    public Restored restore(TabId id, String plugin, Restoring saved) {
        requireEdt();
        if (slot != null) return new Restored(Optional.empty(), "another tab is being restored");
        if (tab(id).isPresent()) return new Restored(Optional.empty(), "a tab with its id is already open");
        var restorer = restorers.getOrDefault(plugin, Map.of()).get(saved.state().kind());
        if (restorer == null) return new Restored(Optional.empty(), "it does not restore " + saved.state().kind() + " tabs");
        Slot rebuilding = new Slot(id, saved.window(), plugin);
        slot = rebuilding;
        Optional<String> problem;
        try {
            problem = restorer.apply(saved);
        } catch (RuntimeException | Error failure) {
            // What gets past the plugin's containment, an Error, takes the item the restorer opened with it, so its saved
            // id is free again (6b Task 1's review).
            slot = null;
            if (rebuilding.taken != null) {
                try { rebuilding.taken.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            }
            throw failure;
        } finally {
            slot = null;
        }
        Tab taken = rebuilding.taken;
        // Only a tab still open in the window being restored is the saved tab (workspace plan 6b Rulings 30).
        if (taken != null && !taken.closed() && saved.window().equals(taken.window())) return new Restored(Optional.of(taken), "");
        return new Restored(Optional.empty(), problem.orElse("it opened no tab"));
    }

    /**
     * The saved tab being rebuilt, while {@code plugin} has not opened its tab in {@code window} yet: a session the plugin
     * opens there now takes its id (workspace tabs spec 13).
     *
     * @param plugin the plugin opening a session
     * @param window the window it opens in
     * @return the saved id, or empty when no saved tab of that plugin waits there
     */
    public Optional<TabId> slot(String plugin, UUID window) {
        return slot != null && slot.taken == null && slot.plugin.equals(plugin) && slot.window.equals(window) && tab(slot.id).isEmpty()
            ? Optional.of(slot.id) : Optional.empty();
    }

    /**
     * A window opened the saved tab being rebuilt itself, as a session's terminal tab: the restore hands it back.
     *
     * @param tab the tab, registered with the saved id
     */
    public void restored(Tab tab) { if (slot != null && slot.taken == null && slot.id.equals(tab.id())) slot.taken = tab; }

    /**
     * Whether a saved tab is being rebuilt in {@code window} now: while its restorer runs, that window takes no other tab
     * (workspace plan 6b Rulings 24).
     *
     * @param window a window's id
     * @return true while a restore's slot names it
     */
    public boolean restoring(UUID window) { return slot != null && slot.window.equals(window); }

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
