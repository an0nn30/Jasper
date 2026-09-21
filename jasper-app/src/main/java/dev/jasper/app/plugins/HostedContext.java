package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginConfig;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.Toolbar;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** One plugin's view of the host. Closed after a failed start and after stop: nothing can be contributed through it. */
final class HostedContext implements PluginContext {
    enum State { STARTING, ACTIVE, STOPPING, CLOSED }

    private final PluginHost host;
    final HostedPlugin hosted;
    final PluginSettings settings;
    private final String id;
    private final List<Subscription> owned = new ArrayList<>();
    private final ExecutorService executor;
    volatile State state = State.STARTING;
    private final HostedUi ui;
    Plugin plugin;

    HostedContext(PluginHost host, HostedPlugin hosted, PluginSettings settings) {
        this.host = host; this.hosted = hosted; this.settings = settings;
        this.id = hosted.info().id();
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jasper-plugin-" + id + "-", 0).factory());
        this.ui = new HostedUi(id, host.environment.contributions(), host.containment, host.environment.ui(),
            host.environment.onUi(), () -> state != State.CLOSED, hosted.loader(),
            () -> host.environment.dark().getAsBoolean() ? Variant.DARK : Variant.LIGHT,
            handler -> events().subscribe(AppEvents.THEME_CHANGED, event -> handler.accept(event.variant())));
    }

    private void requireOpen() {
        if (state == State.CLOSED) throw new IllegalStateException("Plugin context is closed: " + id);
    }

    private void requireUi(String what) {
        if (!host.environment.onUi().getAsBoolean())
            throw new IllegalStateException(what + " must be called on the UI thread: " + id);
    }

    /** Closes everything the context handed out. Interrupting is for a failed start; shutdown drains first. */
    void teardown(boolean interrupt, String reason) {
        state = State.CLOSED;
        ui.closeAll();
        List<Subscription> copy;
        synchronized (owned) { copy = new ArrayList<>(owned); owned.clear(); }
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).close();
        host.bus.removeAll(id);
        host.activities.failAll(id, reason);
        settings.close();
        if (interrupt) executor.shutdownNow(); else executor.shutdown();
    }

    /** Completes when accepted background work has finished, interrupting it once the grace elapses. */
    CompletableFuture<Void> drain(Duration grace) {
        var done = new CompletableFuture<Void>();
        Thread.ofPlatform().daemon().name("jasper-plugin-drain-" + id).start(() -> {
            try {
                if (!executor.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) executor.shutdownNow();
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            done.complete(null);
        });
        return done;
    }

    @Override public PluginInfo plugin() { return hosted.info(); }
    @Override public System.Logger log() { return Containment.logger(id); }

    @Override public Path dataDirectory() {
        try { return Files.createDirectories(host.environment.dataDirectory().apply(id)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public PluginConfig config() { return settings; }

    @Override public Actions actions() { return ui.actions(); }
    @Override public Toolbar toolbar() { return ui.toolbar(); }
    @Override public Menus menus() { return ui.menus(); }
    @Override public StatusBar statusBar() { return ui.statusBar(); }
    @Override public Appearance appearance() { return ui.appearance(); }

    @Override public Executor background() {
        return task -> {
            Objects.requireNonNull(task, "task");
            if (state == State.CLOSED) throw new RejectedExecutionException("Plugin context is closed: " + id);
            executor.execute(() -> host.containment.run(id, "background task", task));
        };
    }

    @Override public Events events() {
        return new Events() {
            @Override public <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler) {
                requireOpen();
                requireUi("subscribe");
                Subscription subscription = host.bus.subscribe(id, topic, handler);
                synchronized (owned) { owned.add(subscription); }
                return subscription;
            }
            @Override public <T> void publish(Topic<T> topic, T payload) {
                requireOpen();
                host.bus.publish(id, topic, payload);
            }
        };
    }

    @Override public Activities activities() {
        return new Activities() {
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.activities.begin(id, spec);
            }
            @Override public List<ActivityEvent> current() { return host.activities.current(); }
        };
    }

    @Override public Services services() {
        return new Services() {
            @Override public <T> void publish(Class<T> api, T implementation) {
                Objects.requireNonNull(implementation, "implementation");
                publishPerConsumer(api, consumer -> implementation);
            }
            @Override public <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer) {
                requireUi("publish");
                if (state != State.STARTING)
                    throw new IllegalStateException("Services may be published only during start(): " + id);
                host.services.stage(hosted, api, perConsumer);
            }
            @Override public <T> T require(Class<T> api) {
                return find(api).orElseThrow(() -> new ServiceUnavailableException(api));
            }
            @Override public <T> Optional<T> find(Class<T> api) { return host.services.find(id, api); }
        };
    }
}
