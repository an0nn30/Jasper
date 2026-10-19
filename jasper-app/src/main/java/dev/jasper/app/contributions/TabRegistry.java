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
import java.util.function.Function;
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
    private final Map<UUID, WorkspaceHost> hosts = new LinkedHashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final Map<String, BiFunction<List<Tab>, Boolean, CompletableFuture<Boolean>>> guards = new HashMap<>();
    private Function<Boolean, UUID> factory = separate -> { throw new IllegalStateException("No workspace host available"); };
    private UUID lastMain;
    private List<Tab> pending = List.of();
    private CompletableFuture<Boolean> decision;
    private CompletableFuture<Boolean> closing;

    /** How a window for plugin views is made when none is open; the argument asks for a separate one. */
    public void setFactory(Function<Boolean, UUID> factory) { this.factory = Objects.requireNonNull(factory); }

    /**
     * Registers a window's workspace. Closing the subscription removes, so disposes, the tabs that window still holds,
     * then forgets it.
     */
    public Subscription addHost(WorkspaceHost host) {
        if (hosts.putIfAbsent(host.id(), host) != null) throw new IllegalArgumentException("Duplicate workspace");
        if (!host.detached()) lastMain = host.id();
        return new Subscription(() -> {
            for (var tab : List.copyOf(tabs)) if (host.id().equals(tab.window())) remove(tab);
            hosts.remove(host.id());
        });
    }

    /** The user turned to this window; the last such main window takes plugin views opened without an owner. */
    public void activated(UUID id) {
        if (hosts.containsKey(id) && !hosts.get(id).detached()) lastMain = id;
    }
    public boolean detached(UUID id) { var host = hosts.get(id); return host != null && host.detached(); }
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
        tab.window(host.id());
        try { host.attach().accept(tab); }
        catch (RuntimeException | Error failure) {
            rollbackAttach(host, tab, failure);
            tabs.remove(tab);
            try { tab.dispose(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        select(tab);
        return tab;
    }

    private static void rollbackAttach(WorkspaceHost host, Tab tab, Throwable failure) {
        try { host.detach().accept(tab); }
        catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
    }

    private WorkspaceHost main() {
        var host = hosts.get(lastMain);
        if (host == null) host = hosts.values().stream().filter(h -> !h.detached()).findFirst().orElse(null);
        if (host == null) host = hosts.get(factory.apply(false));
        return Objects.requireNonNull(host, "Factory did not register a workspace");
    }

    /** Shows the tab in its window and raises the window. */
    public void select(Tab tab) {
        if (tab.closed()) return;
        var host = hosts.get(tab.window());
        if (host != null) { host.select().accept(tab); host.toFront().run(); }
    }

    /** Moves a tab to a separate window or back to the main one; a pinned tab or one being closed stays. */
    public void move(Tab tab, boolean separate) {
        requireEdt();
        if (tab.closed() || tab.pinned() || pending.contains(tab) || detached(tab.window()) == separate) return;
        var old = hosts.get(tab.window());
        var next = separate ? hosts.get(factory.apply(true)) : main();
        if (old == null || next == null || old == next) return;
        transfer(tab, old, next);
    }

    /**
     * Detaches the tab from {@code old} and attaches it to {@code next}, then selects it. When {@code next} fails, the tab
     * goes back to {@code old}, or another main window, or stays in no window, and the failure propagates.
     */
    private void transfer(Tab tab, WorkspaceHost old, WorkspaceHost next) {
        // Change ownership before removal: an emptied old window may unregister itself synchronously.
        tab.active(false);
        tab.window(next.id());
        tab.moving = true;
        try {
            old.detach().accept(tab);
            try { next.attach().accept(tab); }
            catch (RuntimeException | Error failure) {
                rollbackAttach(next, tab, failure);
                if (tabs.contains(tab) && !tab.closed()) {
                    var recovery = new ArrayList<WorkspaceHost>();
                    if (hosts.get(old.id()) == old) recovery.add(old);
                    for (var host : hosts.values())
                        if (host != old && host != next && !host.detached()) recovery.add(host);
                    boolean restored = false;
                    for (var host : recovery) {
                        tab.window(host.id());
                        try { host.attach().accept(tab); restored = true; break; }
                        catch (RuntimeException | Error recoveryFailure) {
                            rollbackAttach(host, tab, recoveryFailure);
                            failure.addSuppressed(recoveryFailure);
                        }
                    }
                    if (!restored) tab.window(null);
                }
                throw failure;
            }
        } finally {
            tab.moving = false;
        }
        select(tab);
    }

    /** Disposes the tab at once, without asking a guard, and takes it out of its window. */
    public void remove(Tab tab) {
        requireEdt();
        if (!tabs.remove(tab)) return;
        var host = hosts.get(tab.window());
        tab.dispose();
        if (host != null) host.detach().accept(tab);
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
                } catch (RuntimeException failure) { finish(false); }
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

    /** Asks the guards about these tabs as one decision, then removes them all, or none. */
    public CompletableFuture<Boolean> requestClose(List<Tab> requested) {
        if (decision != null && closing == null) return CompletableFuture.completedFuture(false);
        if (closing != null) return pending.equals(requested) ? closing : CompletableFuture.completedFuture(false);
        var result = new CompletableFuture<Boolean>();
        closing = result;
        approve(requested, false).whenComplete((allowed, error) -> onEdt(() -> {
            if (error == null && Boolean.TRUE.equals(allowed)) for (var tab : requested) remove(tab);
            closing = null;
            result.complete(error == null && Boolean.TRUE.equals(allowed));
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
