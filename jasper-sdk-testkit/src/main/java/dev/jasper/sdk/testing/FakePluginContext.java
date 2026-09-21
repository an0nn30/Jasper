package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginConfig;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.services.Services;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/** One fake plugin's context. Obtain it from {@link FakePluginHost#start}. */
public final class FakePluginContext implements PluginContext {
    enum State { STARTING, ACTIVE, STOPPING, CLOSED }

    private final FakePluginHost host;
    private final PluginInfo info;
    final Set<String> requires;
    final Plugin plugin;
    volatile State state = State.STARTING;
    volatile Map<String, Object> table = Map.of();
    final CopyOnWriteArrayList<Runnable> configListeners = new CopyOnWriteArrayList<>();
    final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final List<Subscription> owned = new ArrayList<>();

    FakePluginContext(FakePluginHost host, PluginInfo info, Set<String> requires, Plugin plugin) {
        this.host = host; this.info = info; this.requires = requires; this.plugin = plugin;
    }

    void requireOpen() {
        if (state == State.CLOSED) throw new IllegalStateException("Plugin context is closed: " + info.id());
    }

    void closeOwned() {
        List<Subscription> copy;
        synchronized (owned) { copy = new ArrayList<>(owned); owned.clear(); }
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).close();
    }

    @Override public PluginInfo plugin() { return info; }
    @Override public System.Logger log() { return System.getLogger("dev.jasper.plugins." + info.id()); }

    @Override public Path dataDirectory() {
        try { return Files.createDirectories(host.dataRoot().resolve(info.id())); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public PluginConfig config() {
        return new FakePluginConfig(() -> table, "", configListeners,
            (key, message) -> host.reports.add(info.id() + ": " + key + ": " + message));
    }

    @Override public Executor background() {
        return task -> {
            if (state == State.CLOSED) throw new RejectedExecutionException("Plugin context is closed: " + info.id());
            host.background.add(task);
        };
    }

    @Override public Events events() {
        return new Events() {
            @Override public <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler) {
                requireOpen();
                Subscription subscription = host.subscribe(info.id(), topic, handler);
                synchronized (owned) { owned.add(subscription); }
                return subscription;
            }
            @Override public <T> void publish(Topic<T> topic, T payload) {
                requireOpen();
                host.publish(info.id(), topic, payload);
            }
        };
    }

    @Override public Activities activities() {
        return new Activities() {
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.begin(info.id(), spec);
            }
            @Override public List<ActivityEvent> current() { return host.currentActivities(); }
        };
    }

    @Override public Services services() {
        return new Services() {
            @Override public <T> void publish(Class<T> api, T implementation) {
                publishPerConsumer(api, consumer -> implementation);
            }
            @Override public <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer) {
                if (state != State.STARTING)
                    throw new IllegalStateException("Services may be published only during start(): " + info.id());
                host.stage(info.id(), api, perConsumer);
            }
            @Override public <T> T require(Class<T> api) {
                return find(api).orElseThrow(() -> new ServiceUnavailableException(api));
            }
            @Override public <T> Optional<T> find(Class<T> api) {
                return Optional.ofNullable(api.cast(services.get(api)));
            }
        };
    }
}
