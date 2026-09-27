package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.Toolkit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.*;

/** Owns hosted document lifetimes independently of the window rendering them. EDT only. */
public final class ContentRegistry {
    private final Map<UUID, ContentHost> hosts = new LinkedHashMap<>();
    private final List<ContentEntry> entries = new ArrayList<>();
    private final Map<String, BiFunction<List<ContentEntry>, Boolean, CompletableFuture<Boolean>>> guards = new HashMap<>();
    private Function<Boolean, UUID> factory = separate -> { throw new IllegalStateException("No workspace host available"); };
    private UUID lastMain;
    private List<ContentEntry> pending = List.of();
    private CompletableFuture<Boolean> decision;
    private CompletableFuture<Boolean> closing;

    public void setFactory(Function<Boolean, UUID> factory) { this.factory = Objects.requireNonNull(factory); }

    public Subscription addHost(ContentHost host) {
        if (hosts.putIfAbsent(host.id(), host) != null) throw new IllegalArgumentException("Duplicate workspace");
        if (!host.detached()) lastMain = host.id();
        return new Subscription(() -> {
            for (var entry : List.copyOf(entries)) if (host.id().equals(entry.window())) remove(entry);
            hosts.remove(host.id());
        });
    }

    public void activated(UUID id) {
        if (hosts.containsKey(id) && !hosts.get(id).detached()) lastMain = id;
    }
    public boolean detached(UUID id) { var host = hosts.get(id); return host != null && host.detached(); }
    public List<ContentEntry> entries() { return List.copyOf(entries); }
    public List<ContentEntry> inWindow(UUID window) { return entries.stream().filter(e -> window.equals(e.window())).toList(); }

    public Subscription guard(String plugin, BiFunction<List<ContentEntry>, Boolean, CompletableFuture<Boolean>> callback) {
        guards.put(plugin, callback);
        return new Subscription(() -> guards.remove(plugin, callback));
    }

    public ContentEntry open(String plugin, String key, String title, UUID owner, JComponent component) {
        requireEdt();
        for (var entry : entries) if (entry.plugin().equals(plugin) && entry.key().equals(key)) {
            select(entry);
            return entry;
        }
        var entry = new ContentEntry(this, plugin, key, title, Objects.requireNonNull(component));
        ContentHost host = owner != null ? hosts.get(owner) : null;
        if (host == null) host = main();
        entries.add(entry);
        entry.window(host.id());
        host.mount().accept(entry);
        select(entry);
        return entry;
    }

    private ContentHost main() {
        var host = hosts.get(lastMain);
        if (host == null) host = hosts.values().stream().filter(h -> !h.detached()).findFirst().orElse(null);
        if (host == null) host = hosts.get(factory.apply(false));
        return Objects.requireNonNull(host, "Factory did not register a workspace");
    }

    public void select(ContentEntry entry) {
        if (entry.closed()) return;
        var host = hosts.get(entry.window());
        if (host != null) { host.select().accept(entry); host.toFront().run(); }
    }

    public void move(ContentEntry entry, boolean separate) {
        requireEdt();
        if (entry.closed() || entry.pinned() || pending.contains(entry) || detached(entry.window()) == separate) return;
        var old = hosts.get(entry.window());
        var next = separate ? hosts.get(factory.apply(true)) : main();
        if (old == null || next == null || old == next) return;
        // Change ownership before removal: an empty old window may synchronously unregister itself.
        entry.active(false);
        entry.window(next.id());
        old.remove().accept(entry);
        next.mount().accept(entry);
        select(entry);
    }

    public void remove(ContentEntry entry) {
        requireEdt();
        if (!entries.remove(entry)) return;
        var host = hosts.get(entry.window());
        entry.dispose();
        if (host != null) host.remove().accept(entry);
    }

    public CompletableFuture<Boolean> approve(List<ContentEntry> requested) { return approve(requested, true); }

    private CompletableFuture<Boolean> approve(List<ContentEntry> requested, boolean batch) {
        requireEdt();
        var live = requested.stream().filter(e -> !e.closed()).distinct().toList();
        if (decision != null) return pending.equals(live) ? decision : CompletableFuture.completedFuture(false);
        if (live.isEmpty()) return CompletableFuture.completedFuture(true);
        if (live.stream().anyMatch(ContentEntry::pinned)) return CompletableFuture.completedFuture(false);
        pending = live;
        var result = new CompletableFuture<Boolean>();
        decision = result;
        var groups = new LinkedHashMap<String, List<ContentEntry>>();
        for (var entry : live) groups.computeIfAbsent(entry.plugin(), p -> new ArrayList<>()).add(entry);
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

    public CompletableFuture<Boolean> requestClose(List<ContentEntry> requested) {
        if (decision != null && closing == null) return CompletableFuture.completedFuture(false);
        if (closing != null) return pending.equals(requested) ? closing : CompletableFuture.completedFuture(false);
        var result = new CompletableFuture<Boolean>();
        closing = result;
        approve(requested, false).whenComplete((allowed, error) -> onEdt(() -> {
            if (error == null && Boolean.TRUE.equals(allowed)) for (var entry : requested) remove(entry);
            closing = null;
            result.complete(error == null && Boolean.TRUE.equals(allowed));
        }));
        return result;
    }

    /** Existing synchronous native quit/restart guards pump EDT events while asynchronous saves complete. */
    public boolean mayClose(List<ContentEntry> entries) {
        // A nested native close must not wait for the outer modal guard's own stack to unwind.
        if (decision != null) return false;
        return await(approve(entries));
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
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Content belongs to EDT");
    }
}
