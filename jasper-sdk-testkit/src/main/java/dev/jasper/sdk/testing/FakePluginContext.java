package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.Panels;
import dev.jasper.sdk.ui.Rail;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.Windows;
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
import java.util.Objects;

/** One fake plugin's context. Obtain it from {@link FakePluginHost#start}. */
public final class FakePluginContext implements PluginContext {
    enum State { STARTING, ACTIVE, STOPPING, CLOSED }

    private final FakePluginHost host;
    private final PluginInfo info;
    final Set<String> requires;
    final Plugin plugin;
    volatile State state = State.STARTING;
    final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final List<Subscription> owned = new ArrayList<>();
    private final FakeKit kit;
    @Override public FakeKit kit() { return kit; }
    final FakeTerminals terminals;
    final FakeUi ui;
    final FakePalette palette;
    final FakeFileSources fileSources;
    final FakeCommandLine commandLine;
    final FakePluginSettings settings;
    final dev.jasper.sdk.notifications.Notifications notifications;

    FakePluginContext(FakePluginHost host, PluginInfo info, Set<String> requires, Plugin plugin) {
        this.kit = new FakeKit(info.id()); this.kit.anchorHost=host;
        this.host = host; this.info = info; this.requires = requires; this.plugin = plugin;
        this.terminals = new FakeTerminals(this, host.workspace);
        this.ui = new FakeUi(host, this);
        this.palette = new FakePalette(host, this);
        this.fileSources = new FakeFileSources(host, this);
        this.commandLine = new FakeCommandLine(host, this);
        this.settings = new FakePluginSettings(host, info.id(), host.dataRoot().resolve(info.id()).resolve(info.id() + ".toml"));
        this.notifications = host.notifications.of(info.id(), () -> state != State.CLOSED, name -> kit().validateIcon(name));
    }

    /** The host this context belongs to, for the fakes that keep host-wide registries. */
    FakePluginHost host() { return host; }

    void requireOpen() {
        if (state == State.CLOSED) throw new IllegalStateException("Plugin context is closed: " + info.id());
    }

    void closeOwned() {
        kit.close();
        List<Subscription> copy;
        synchronized (owned) { copy = new ArrayList<>(owned); owned.clear(); }
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).close();
    }

    /** Closes the registration when the plugin stops, like everything else the context handed out. */
    void own(Subscription subscription) {
        synchronized (owned) { owned.add(subscription); }
    }

    @Override public PluginInfo plugin() { return info; }
    @Override public System.Logger log() { return System.getLogger("dev.jasper.plugins." + info.id()); }

    @Override public Path dataDirectory() {
        // The application's layout: <plugins>/<id>/data.
        try { return Files.createDirectories(host.dataRoot().resolve(info.id()).resolve("data")); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public dev.jasper.sdk.settings.PluginSettings settings() { return settings; }

    @Override public Executor background() {
        return task -> {
            if (state == State.CLOSED) throw new RejectedExecutionException("Plugin context is closed: " + info.id());
            host.background.add(task);
        };
    }

    /** The scripted windows, tabs and panes. */
    @Override public dev.jasper.sdk.terminal.Terminals terminals() { return terminals; }
        @Override public dev.jasper.sdk.palette.Palette palette() { return palette; }
    @Override public dev.jasper.sdk.files.FileSources fileSources() { return fileSources; }
    @Override public dev.jasper.sdk.notifications.Notifications notifications() { return notifications; }
    @Override public dev.jasper.sdk.cli.CommandLine commandLine() { return commandLine; }
        @Override public dev.jasper.sdk.ui.Platform platform() {
            return new dev.jasper.sdk.ui.Platform() {
                @Override public void openInEditor(Path file) {
                    requireOpen();
                    Objects.requireNonNull(file, "file");
                    // As the application: the editor a plugin provides first, here on the caller's thread and with no window.
                    if (!host.editProvided(file, java.util.OptionalInt.empty(), Optional.empty())) host.openedInEditor.add(file);
                }
                @Override public Subscription editor(dev.jasper.sdk.ui.FileEditor editor) {
                    requireOpen();
                    if (!info.capabilities().contains(dev.jasper.sdk.Capabilities.EDITOR_PROVIDE))
                        throw new dev.jasper.sdk.MissingCapabilityException(info.id(), dev.jasper.sdk.Capabilities.EDITOR_PROVIDE);
                    Objects.requireNonNull(editor, "editor");
                    return host.provideEditor(FakePluginContext.this, editor);
                }
                @Override public void browse(java.net.URI uri) { requireOpen(); host.browsed.add(webAddress(uri)); }
                @Override public boolean copy(String text) { requireOpen(); return copied(text, false); }
                @Override public boolean copySecret(String text) { requireOpen(); return copied(text, true); }
                @Override public Optional<String> clipboardText() {
                    requireOpen();
                    if (!info.capabilities().contains(dev.jasper.sdk.Capabilities.CLIPBOARD_READ))
                        throw new dev.jasper.sdk.MissingCapabilityException(info.id(), dev.jasper.sdk.Capabilities.CLIPBOARD_READ);
                    return host.clipboard.text();
                }
            };
        }

        /** As the application: a busy clipboard is posted in the plugin's group, never thrown. */
        private boolean copied(String text, boolean secret) {
            Objects.requireNonNull(text, "text");
            try {
                host.clipboard.copy(info.id(), text, secret);
                return true;
            } catch (IllegalStateException busy) {
                notifications.group().error("Could not copy", busy);
                return false;
            }
        }

        /** Only an absolute http or https address opens in a browser. */
        private static java.net.URI webAddress(java.net.URI uri) {
            Objects.requireNonNull(uri, "uri");
            String scheme = uri.getScheme();
            if (scheme == null || uri.isOpaque() || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                throw new IllegalArgumentException("Only an http or https address opens in a browser: " + uri);
            return uri;
        }

    @Override public Events events() {
        return new Events() {
            @Override public <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler) {
                requireOpen();
                if (dev.jasper.sdk.terminal.TerminalEvents.owns(topic)) terminals.require(dev.jasper.sdk.Capabilities.TERMINAL_OBSERVE);
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
            @Override public ConnectionHandle connection(ConnectionSpec spec) {
                requireOpen();
                return host.connection(info.id(), spec);
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

    /** Action registration. */
    @Override public Actions actions() { return ui.actions(); }

    /** Toolbar placements. */
    @Override public Toolbar toolbar() { return ui.toolbar(); }

    /** Menu placements. */
    @Override public Menus menus() { return ui.menus(); }

    /** Status bar items. */
    @Override public StatusBar statusBar() { return ui.statusBar(); }

    /** Tool windows. */
    @Override public Panels panels() { return ui.panels(); }

    /** Rail buttons. */
    @Override public Rail rail() { return ui.rail(); }

    /** Plugin windows and dialogs. */
    @Override public Windows windows() { return ui.windows(); }

    /** The host's semantic colours, variants, workspace, editor and syntax palettes, opacity, and change notifications. */
    @Override public Appearance appearance() {
        return new Appearance() {
            @Override public java.awt.Color color(dev.jasper.sdk.ui.SemanticColor role) { host.requireAppearanceThread(); return host.semanticColor(role); }
            @Override public Variant variant() { return ui.variant(); }
            @Override public Variant workspaceVariant() { return host.workspaceAppearance().variant(); }
            @Override public dev.jasper.sdk.ui.WorkspacePalette workspacePalette() {
                host.requireAppearanceThread(); return host.workspaceAppearance().palette();
            }
            @Override public dev.jasper.sdk.ui.EditorPalette editorPalette() {
                host.requireAppearanceThread(); return host.workspaceAppearance().editor();
            }
            @Override public dev.jasper.sdk.ui.SyntaxPalette syntaxPalette() {
                host.requireAppearanceThread(); return host.workspaceAppearance().syntax();
            }
            @Override public double workspaceOpacity(java.awt.Component component) {
                host.requireAppearanceThread();
                java.util.Objects.requireNonNull(component, "component");
                return host.workspaceOpacity();
            }
            @Override public Subscription onWorkspaceChanged(Consumer<Variant> handler) {
                requireOpen();
                var subscription = host.subscribeWorkspace(info.id(), handler); own(subscription); return subscription;
            }
            @Override public Subscription onChanged(java.util.function.Consumer<Variant> handler) {
                requireOpen();var subscription=host.subscribeAppearance(info.id(),handler);own(subscription);return subscription;
            }
        };
    }
}
