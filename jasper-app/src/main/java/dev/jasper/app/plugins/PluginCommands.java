package dev.jasper.app.plugins;

import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.Invocation;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.cli.CommandCall;
import dev.jasper.sdk.cli.CommandHandler;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs {@code jasper <subcommand>} calls in the plugin that answers them. The owners are fixed at start; each owner
 * registers its handler through {@code CommandLine.handle}. A call hops to the UI thread, where its handler runs inside
 * the plugin's containment. A call still running when its plugin stops ends with exit code 1.
 */
final class PluginCommands implements CommandDispatcher {
    /** A handler, with the pane-to-window lookup of the context that registered it. */
    private record Registered(String pluginId, CommandHandler handler, Function<UUID, Optional<WindowHandle>> windows) { }

    /** A call a handler has taken and not yet completed. */
    private record Running(String name, Call call, CompletableFuture<Integer> result) { }

    private final Consumer<Runnable> ui;
    private final Containment containment;
    private final Map<String, Registered> handlers = new ConcurrentHashMap<>();
    /** Calls still running, by the plugin whose handler took them. */
    private final Map<String, Set<Running>> running = new ConcurrentHashMap<>();
    private volatile Subcommands table = Subcommands.NONE;

    PluginCommands(Consumer<Runnable> ui, Containment containment) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.containment = Objects.requireNonNull(containment, "containment");
    }

    /** Sets which plugin answers each subcommand; before any plugin starts. */
    void declare(Subcommands owners) { table = Objects.requireNonNull(owners, "owners"); }

    /** The owners, by name. */
    List<Subcommands.Owner> owners() { return table.all(); }

    /**
     * Registers a plugin's handler.
     *
     * @return removes it
     * @throws IllegalArgumentException when the plugin does not own {@code name} or it already has a handler
     */
    Runnable register(String pluginId, String name, CommandHandler handler, Function<UUID, Optional<WindowHandle>> windows) {
        Subcommands.Owner owner = table.owner(name).orElseThrow(() ->
            new IllegalArgumentException("plugin.toml does not declare the subcommand " + name + " under [[subcommands]]"));
        if (!owner.pluginId().equals(pluginId))
            throw new IllegalArgumentException("The subcommand " + name + " is provided by " + owner.pluginName());
        var registered = new Registered(pluginId, Objects.requireNonNull(handler, "handler"), windows);
        if (handlers.putIfAbsent(name, registered) != null)
            throw new IllegalArgumentException("The subcommand " + name + " already has a handler");
        return () -> handlers.remove(name, registered);
    }

    /**
     * The plugin stopped: each of its calls still running ends with exit code 1, and their cancellation listeners no
     * longer run. UI thread.
     */
    void stopped(String pluginId) {
        Set<Running> calls = running.remove(pluginId);
        if (calls == null) return;
        for (Running each : List.copyOf(calls)) {
            each.call().live.set(false);
            if (each.result().isDone()) continue;
            each.call().err("jasper: " + each.name() + " stopped\n");
            each.result().complete(1);
        }
    }

    @Override public CompletionStage<Integer> dispatch(Invocation invocation) {
        String name = invocation.arguments().isEmpty() ? "" : invocation.arguments().getFirst();
        Optional<Subcommands.Owner> owner = table.owner(name);
        if (owner.isEmpty()) {
            invocation.err("jasper: unknown command " + name + "; run jasper --help for the list\n");
            return CompletableFuture.completedFuture(2);
        }
        var result = new CompletableFuture<Integer>();
        ui.accept(() -> run(owner.get(), invocation, result));
        return result;
    }

    private void run(Subcommands.Owner owner, Invocation invocation, CompletableFuture<Integer> result) {
        String name = owner.name();
        Registered registered = handlers.get(name);
        if (registered == null) {
            invocation.err("jasper: " + name + " is unavailable (the " + owner.pluginName() + " plugin is off)\n");
            result.complete(1);
            return;
        }
        String what = "subcommand " + name;
        Optional<WindowOwner> window = invocation.pane().flatMap(registered.windows()).map(handle -> (WindowOwner) handle);
        var call = new Call(registered.pluginId(), what, invocation, window);
        var stage = new AtomicReference<CompletionStage<Integer>>();
        Throwable thrown = containment.attempt(registered.pluginId(), what, () -> {
            stage.set(Objects.requireNonNull(registered.handler().run(call), "the handler's stage"));
            return null;
        });
        if (thrown != null) {
            fail(invocation, name, thrown, result);
            return;
        }
        Set<Running> calls = running.computeIfAbsent(registered.pluginId(), id -> ConcurrentHashMap.newKeySet());
        var tracked = new Running(name, call, result);
        calls.add(tracked);
        result.whenComplete((code, failure) -> calls.remove(tracked));
        stage.get().whenComplete((code, failure) -> {
            // Already ended because the plugin stopped.
            if (result.isDone()) return;
            if (failure == null) {
                result.complete(code == null || code < 0 || code > 255 ? 1 : code);
                return;
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            containment.record(registered.pluginId(), what, cause);
            fail(invocation, name, cause, result);
        });
    }

    private static void fail(Invocation invocation, String name, Throwable cause, CompletableFuture<Integer> result) {
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        invocation.err("jasper: " + name + " failed: " + message + "\n");
        result.complete(1);
    }

    /** What a handler sees of one call. */
    private final class Call implements CommandCall {
        private final String pluginId;
        private final String what;
        private final Invocation invocation;
        private final Optional<WindowOwner> window;
        /** False once the plugin stopped: its cancellation listeners no longer run. */
        final AtomicBoolean live = new AtomicBoolean(true);

        Call(String pluginId, String what, Invocation invocation, Optional<WindowOwner> window) {
            this.pluginId = pluginId;
            this.what = what;
            this.invocation = invocation;
            this.window = window;
        }

        @Override public List<String> arguments() { return invocation.arguments().subList(1, invocation.arguments().size()); }
        @Override public Path workingDirectory() { return invocation.workingDirectory(); }
        @Override public Optional<WindowOwner> window() { return window; }
        @Override public void out(String text) { invocation.out(text); }
        @Override public void err(String text) { invocation.err(text); }

        @Override public Subscription onCancelled(Runnable listener) {
            Objects.requireNonNull(listener, "listener");
            var active = new AtomicBoolean(true);
            invocation.onCancelled(() -> ui.accept(() -> {
                if (active.get() && live.get()) containment.run(pluginId, what + " cancellation", listener);
            }));
            return () -> active.set(false);
        }
    }
}
