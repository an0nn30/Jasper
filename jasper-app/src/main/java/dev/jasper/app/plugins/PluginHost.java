package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.jasper.app.terminals.TerminalRegistry;

/**
 * Plugin lifetimes on the UI thread: start in the order given, roll back a failed start completely,
 * stop in reverse. It knows nothing about jars or descriptors; see {@link HostedPlugin}.
 */
final class PluginHost {
    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Function<String, Map<String, Object>> settings, Function<String, Path> settingsFile, BiConsumer<String, String> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor) { }

    record Outcome(PluginStatus.State state, String reason) { }

    final Environment environment;
    /** Runs cancellation handlers and connection closes off the EDT; outlives every plugin's own executor. */
    final CleanupWorker cleanup = new CleanupWorker();
    private final dev.jasper.app.lifecycle.Subscription terminalBridge;
    final Containment containment;
    final EventBus bus;
    final ActivityHub activities;
    final ServiceRegistry services = new ServiceRegistry();
    private final Map<String, HostedContext> contexts = new LinkedHashMap<>();
    private final Map<String, PluginStatus.State> notRunning = new HashMap<>();

    PluginHost(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
        this.containment = new Containment(environment.onUi());
        this.bus = new EventBus(environment.ui(), containment);
        this.activities = new ActivityHub(bus);
        this.terminalBridge = TerminalBridge.connect(environment.terminals(), bus);
    }

    private void requireUi() {
        if (!environment.onUi().getAsBoolean()) throw new IllegalStateException("Plugin lifetimes belong to the UI thread");
    }

    Outcome start(HostedPlugin hosted) {
        requireUi();
        String id = hosted.info().id();
        if (contexts.containsKey(id)) throw new IllegalArgumentException("Already started: " + id);
        for (String required : hosted.requires()) {
            if (active(required)) continue;
            notRunning.put(id, PluginStatus.State.SKIPPED);
            PluginStatus.State why = notRunning.get(required);
            return new Outcome(PluginStatus.State.SKIPPED, "requires " + required + ", which "
                + (why == PluginStatus.State.FAILED ? "failed to start" : why == PluginStatus.State.SKIPPED ? "was skipped" : "is not running"));
        }
        var context = new HostedContext(this, hosted, new PluginSettings(id, environment.settingsFile().apply(id), environment.settings().apply(id),
            containment, environment.configReport()));
        contexts.put(id, context);
        Set<String> providers = new HashSet<>(hosted.requires());
        providers.addAll(hosted.optional());
        services.prepare(hosted.info(), providers, containment);
        Throwable failure = containment.attempt(id, "start", () -> {
            Plugin plugin = hosted.instantiate().call();
            context.plugin = plugin;
            plugin.start(context);
            return null;
        });
        if (failure != null) {
            // Everything the context handed out goes, not only registrations; the publication was never visible.
            context.teardown(true, "Plugin failed to start");
            services.discard(id);
            notRunning.put(id, PluginStatus.State.FAILED);
            return new Outcome(PluginStatus.State.FAILED, failure.toString());
        }
        services.commit(id);
        context.state = HostedContext.State.ACTIVE;
        return new Outcome(PluginStatus.State.ACTIVE, "");
    }

    boolean active(String pluginId) {
        HostedContext context = contexts.get(pluginId);
        return context != null && context.state == HostedContext.State.ACTIVE;
    }

    void settingsChanged() {
        requireUi();
        for (HostedContext context : List.copyOf(contexts.values()))
            if (context.state == HostedContext.State.ACTIVE)
                context.settings.update(environment.settings().apply(context.hosted.info().id()));
    }

    /** Reverse start order. The returned futures complete when each plugin's background work has drained. */
    List<CompletableFuture<?>> stop() {
        requireUi();
        terminalBridge.close();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        List<HostedContext> order = new ArrayList<>(contexts.values());
        for (int i = order.size() - 1; i >= 0; i--) {
            HostedContext context = order.get(i);
            if (context.state != HostedContext.State.ACTIVE) continue;
            context.state = HostedContext.State.STOPPING;
            String id = context.hosted.info().id();
            containment.run(id, "stop", context.plugin::stop);
            context.teardown(false, "Plugin stopped");
            pending.add(context.drain(environment.drainGrace()));
        }
        pending.add(cleanup.drained());
        return pending;
    }

    String executing() { return containment.executing(); }

    int failures(String pluginId) { return containment.failures(pluginId); }
}
