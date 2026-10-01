package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.auth.LoginResolver;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.share.ShareRoot;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Every saved and temporary server and its state machine:
 * STOPPED → STARTING → RUNNING → STOPPING → STOPPED, with FAILED and WAITING_FOR_VAULT.
 * UI thread only. Engines start and stop on {@code background}; their events reach the UI thread through {@code ui}.
 * Returned futures complete on the UI thread and never exceptionally: the state says how it went.
 */
public final class ServerRegistry {
    @FunctionalInterface
    public interface EngineFactory {
        ServerEngine create(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events) throws EngineFailure;
    }

    /** What the panel and the palette show about one server. */
    public record View(ServerDefinition definition, boolean temporary, ServerState state, List<URI> addresses,
                       Optional<String> user, boolean restartNeeded) { }

    private final class Slot {
        ServerDefinition definition;
        ServerDefinition running;
        boolean temporary, removed;
        ServerState state = ServerState.STOPPED;
        ServerEngine engine;
        ResolvedLogin login;
        List<URI> addresses = List.of();
        final ActivityLog log = new ActivityLog(activityEntries.getAsInt());
        long attempt;

        Slot(ServerDefinition definition, boolean temporary) { this.definition = definition; this.temporary = temporary; }
        UUID id() { return definition.id(); }
    }

    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    private final EngineFactory engines;
    private final LoginResolver logins;
    private final Executor background, ui;
    private final Clock clock;
    private final IntSupplier activityEntries;
    private final Consumer<ServerEvent> observer;
    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> activityListeners = new CopyOnWriteArrayList<>();

    public ServerRegistry(EngineFactory engines, LoginResolver logins, Executor background, Executor ui, Clock clock,
                          IntSupplier activityEntries, Consumer<ServerEvent> observer) {
        this.engines = engines; this.logins = logins; this.background = background; this.ui = ui; this.clock = clock;
        this.activityEntries = activityEntries; this.observer = observer;
    }

    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public Subscription onActivity(Consumer<UUID> listener) { activityListeners.add(listener); return () -> activityListeners.remove(listener); }

    /**
     * The saved servers, in order. A temporary server saved under its own id becomes a saved one; a deleted server
     * that still runs leaves the list at once and stops in the background.
     */
    public void setDefinitions(List<ServerDefinition> saved) {
        var next = new LinkedHashMap<UUID, Slot>();
        for (ServerDefinition definition : saved) {
            Slot slot = slots.get(definition.id());
            if (slot == null) slot = new Slot(definition, false);
            else { slot.definition = definition; slot.temporary = false; slot.removed = false; }
            next.put(definition.id(), slot);
        }
        var stopping = new ArrayList<UUID>();
        for (Slot slot : slots.values()) {
            if (next.containsKey(slot.id())) continue;
            if (slot.temporary) { next.put(slot.id(), slot); continue; }
            if (slot.state.phase() == Phase.STARTING || slot.state.phase() == Phase.RUNNING || slot.state.phase() == Phase.STOPPING) {
                slot.removed = true;
                next.put(slot.id(), slot);
                stopping.add(slot.id());
            }
        }
        slots.clear();
        slots.putAll(next);
        stopping.forEach(this::stop);
        changed();
    }

    public List<View> servers() { return slots.values().stream().filter(slot -> !slot.removed).map(this::view).toList(); }
    public Optional<View> server(UUID id) { return Optional.ofNullable(slots.get(id)).filter(slot -> !slot.removed).map(this::view); }

    public int running() { return (int) slots.values().stream().filter(slot -> slot.state.phase() == Phase.RUNNING).count(); }

    /** Servers that are starting, running or stopping: the ones quitting would stop. */
    public int active() {
        return (int) slots.values().stream().filter(slot -> slot.state.phase() == Phase.STARTING
            || slot.state.phase() == Phase.RUNNING || slot.state.phase() == Phase.STOPPING).count();
    }

    /** The running server's password, for Copy Password; empty for anonymous or stopped servers. */
    public Optional<String> password(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null || slot.state.phase() != Phase.RUNNING || slot.login == null) return Optional.empty();
        return slot.login.revealPassword();
    }

    public List<ActivityLog.Entry> activity(UUID id) {
        Slot slot = slots.get(id);
        return slot == null ? List.of() : slot.log.entries();
    }

    public void clearActivity(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null) return;
        slot.log.clear();
        activityListeners.forEach(listener -> listener.accept(id));
    }

    /** Adds a server that is never saved and starts it; it disappears when stopped. */
    public CompletableFuture<Void> startTemporary(ServerDefinition definition, WindowHandle owner) {
        if (slots.containsKey(definition.id())) return CompletableFuture.completedFuture(null);
        slots.put(definition.id(), new Slot(definition, true));
        changed();
        return start(definition.id(), owner);
    }

    /** Starts a stopped, failed or waiting server; a Vault login may prompt over {@code owner}. */
    public CompletableFuture<Void> start(UUID id, WindowHandle owner) {
        Slot slot = slots.get(id);
        if (slot == null || slot.removed || !slot.state.phase().startable()) return CompletableFuture.completedFuture(null);
        ServerDefinition definition = slot.definition;
        Optional<String> problem = startProblem(slot);
        if (problem.isPresent()) { fail(slot, problem.get()); return CompletableFuture.completedFuture(null); }
        long attempt = ++slot.attempt;
        slot.state = ServerState.STARTING;
        changed();
        var result = new CompletableFuture<Void>();
        CompletableFuture<ResolvedLogin> login;
        try { login = logins.resolve(definition, owner); }
        catch (RuntimeException failure) { login = CompletableFuture.failedFuture(failure); }
        login.whenComplete((resolved, failure) -> ui.execute(() -> {
            if (!current(slot, attempt)) { if (resolved != null) resolved.close(); result.complete(null); return; }
            if (failure != null) { fail(slot, message(failure)); result.complete(null); return; }
            try { background.execute(() -> bind(slot, attempt, definition, resolved, result)); }
            catch (RejectedExecutionException closing) { resolved.close(); fail(slot, "File Server is stopping"); result.complete(null); }
        }));
        return result;
    }

    /** Worker side: builds and starts the engine, then reports on the UI thread. */
    private void bind(Slot slot, long attempt, ServerDefinition definition, ResolvedLogin login, CompletableFuture<Void> result) {
        ServerEngine engine = null;
        Throwable error = null;
        try {
            ShareRoot share = new ShareRoot(definition.folder(), !definition.writable());
            engine = engines.create(definition, share, login, new ServerEvents(clock, event -> ui.execute(() -> record(slot, event))));
            engine.start();
        } catch (Throwable failure) {
            error = failure;
            if (engine != null) { try { engine.stop(); } catch (Throwable ignored) { } }
            engine = null;
        }
        ServerEngine started = engine;
        Throwable failure = error;
        ui.execute(() -> {
            if (!current(slot, attempt)) {
                if (started != null) {
                    try { background.execute(started::stop); } catch (RejectedExecutionException closing) { started.stop(); }
                }
                login.close();
            } else if (failure != null) {
                login.close();
                fail(slot, message(failure));
            } else {
                List<URI> addresses;
                try { addresses = started.addresses(); }
                catch (Throwable broken) {
                    try { background.execute(started::stop); } catch (RejectedExecutionException closing) { started.stop(); }
                    login.close();
                    fail(slot, message(broken));
                    result.complete(null);
                    return;
                }
                slot.engine = started;
                slot.login = login;
                slot.running = definition;
                slot.addresses = addresses;
                slot.state = ServerState.RUNNING;
                changed();
            }
            result.complete(null);
        });
    }

    /**
     * Stops a server. A running one completes once its engine has stopped; a starting one is cancelled and the
     * future completes at once; a stopping one completes at once (the stop already under way is not awaited).
     */
    public CompletableFuture<Void> stop(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null) return CompletableFuture.completedFuture(null);
        switch (slot.state.phase()) {
            case STARTING -> { cancelStart(id); return CompletableFuture.completedFuture(null); }
            case STOPPED, FAILED, WAITING_FOR_VAULT -> { settle(slot); return CompletableFuture.completedFuture(null); }
            case STOPPING -> { return CompletableFuture.completedFuture(null); }
            case RUNNING -> { }
        }
        slot.state = ServerState.STOPPING;
        changed();
        ServerEngine engine = slot.engine;
        slot.engine = null;
        var result = new CompletableFuture<Void>();
        Runnable stop = () -> {
            try { engine.stop(); } catch (RuntimeException ignored) { }
            ui.execute(() -> {
                if (slot.login != null) slot.login.close();
                slot.login = null;
                slot.running = null;
                slot.addresses = List.of();
                settle(slot);
                result.complete(null);
            });
        };
        try { background.execute(stop); } catch (RejectedExecutionException closing) { stop.run(); }
        return result;
    }

    public void cancelStart(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null || slot.state.phase() != Phase.STARTING) return;
        slot.attempt++;
        settle(slot);
    }

    /** Starts every saved server marked to start with Jasper; one that needs a locked Vault waits for it instead of prompting. */
    public void autostart() {
        for (Slot slot : List.copyOf(slots.values())) {
            if (slot.temporary || !slot.definition.autostart() || slot.state.phase() != Phase.STOPPED) continue;
            if (logins.needsUnlock(slot.definition)) { slot.state = ServerState.WAITING_FOR_VAULT; changed(); }
            else start(slot.id(), null);
        }
    }

    public void vaultUnlocked() {
        for (Slot slot : List.copyOf(slots.values()))
            if (slot.state.phase() == Phase.WAITING_FOR_VAULT) start(slot.id(), null);
    }

    public CompletableFuture<Void> stopAll() {
        var stops = new ArrayList<CompletableFuture<Void>>();
        for (Slot slot : List.copyOf(slots.values())) if (slot.state.phase() != Phase.STOPPED) stops.add(stop(slot.id()));
        return CompletableFuture.allOf(stops.toArray(CompletableFuture[]::new));
    }

    /** Plugin shutdown: stops every engine in parallel, waiting at most 5 s in all, and forgets every server. */
    public void shutdown() {
        var threads = new ArrayList<Thread>();
        for (Slot slot : slots.values()) {
            slot.attempt++;
            ServerEngine engine = slot.engine;
            if (engine != null) {
                Thread thread = new Thread(() -> { try { engine.stop(); } catch (RuntimeException ignored) { } }, "file-server-stop");
                thread.setDaemon(true);
                thread.start();
                threads.add(thread);
            }
            if (slot.login != null) slot.login.close();
        }
        long deadline = System.nanoTime() + SHUTDOWN_WAIT.toNanos();
        for (Thread thread : threads) {
            long left = deadline - System.nanoTime();
            if (left <= 0) break;
            try { thread.join(Math.max(1, left / 1_000_000)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        slots.clear();
        changed();
    }

    /** Recomputes running servers' URLs, for example after joining another network. */
    public void refreshAddresses() {
        boolean any = false;
        for (Slot slot : slots.values()) {
            if (slot.state.phase() != Phase.RUNNING || slot.engine == null) continue;
            List<URI> next = slot.engine.addresses();
            if (!next.equals(slot.addresses)) { slot.addresses = next; any = true; }
        }
        if (any) changed();
    }

    private Optional<String> startProblem(Slot slot) {
        ServerDefinition definition = slot.definition;
        if (!Files.isDirectory(definition.folder())) return Optional.of("The folder " + definition.folder() + " does not exist");
        for (Slot other : slots.values()) {
            if (other == slot) continue;
            ServerDefinition held = other.running != null ? other.running : other.definition;
            if (held.port() != definition.port()) continue;
            Phase phase = other.state.phase();
            if ((phase == Phase.STARTING || phase == Phase.RUNNING || phase == Phase.STOPPING) && held.bind().overlaps(definition.bind()))
                return Optional.of("Port " + definition.port() + " is already used by ‘" + other.definition.name() + "’");
        }
        return Optional.empty();
    }

    private boolean current(Slot slot, long attempt) {
        return slots.get(slot.id()) == slot && slot.attempt == attempt && slot.state.phase() == Phase.STARTING;
    }

    /** STOPPED, or gone for a temporary or deleted server. */
    private void settle(Slot slot) {
        slot.state = ServerState.STOPPED;
        if ((slot.temporary || slot.removed) && slots.get(slot.id()) == slot) slots.remove(slot.id());
        changed();
    }

    private void fail(Slot slot, String reason) {
        slot.state = ServerState.failed(reason);
        if (slot.log.add(new ServerEvent.Failed(clock.instant(), "", reason))) activityListeners.forEach(listener -> listener.accept(slot.id()));
        changed();
    }

    private void record(Slot slot, ServerEvent event) {
        if (slots.get(slot.id()) == slot && slot.log.add(event)) activityListeners.forEach(listener -> listener.accept(slot.id()));
        observer.accept(event);
    }

    private View view(Slot slot) {
        return new View(slot.definition, slot.temporary, slot.state, slot.addresses,
            slot.login == null ? Optional.empty() : slot.login.user(),
            slot.state.phase() == Phase.RUNNING && slot.running != null && !slot.running.equals(slot.definition));
    }

    private void changed() { listeners.forEach(Runnable::run); }

    static String message(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
