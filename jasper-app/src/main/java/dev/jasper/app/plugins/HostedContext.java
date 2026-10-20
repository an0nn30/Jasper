package dev.jasper.app.plugins;

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
import dev.jasper.sdk.notifications.Notifications;
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
import java.net.URI;
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
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.palette.Palette;
import dev.jasper.sdk.ui.Platform;

/** One plugin's view of the host. Closed after a failed start and after stop: nothing can be contributed through it. */
final class HostedContext implements PluginContext {
    enum State { STARTING, ACTIVE, STOPPING, CLOSED }

    private final PluginHost host;
    final HostedPlugin hosted;
    final DeclaredSettings declared;
    private final String id;
    private final List<Subscription> owned = new ArrayList<>();
    void ownPage(PluginPanelPage page){Subscription cleanup=page::dispose;owned.add(cleanup);page.onDisposed=() -> owned.remove(cleanup);}
    private final ExecutorService executor;
    volatile State state = State.STARTING;
    private final HostedUi ui;
    private final HostedKit kit;
    final CapabilityGate gate;
    final HostedTerminals terminals;
    final HostedPalette palette;
    final HostedFileSources fileSources;
    final HostedNotifications notifications;
    final HostedCommandLine commandLine;
    private boolean observing;
    Plugin plugin;

    HostedContext(PluginHost host, HostedPlugin hosted, DeclaredSettings declared) {
        this.host = host; this.hosted = hosted; this.declared = declared;
        this.id = hosted.info().id();
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jasper-plugin-" + id + "-", 0).factory());
        this.gate = new CapabilityGate(id, hosted.info().capabilities());
        HostedTerminals[] self = new HostedTerminals[1];
        var sessions = new HostedSessions(id, host.containment, host.cleanup, paneId -> self[0].detachedPaneHandle(paneId), name -> uiIcon(name));
        this.terminals = new HostedTerminals(id, gate, host.environment.terminals(), host.environment.ui(), host.environment.onUi(),
            () -> state != State.CLOSED, sessions, host.environment.profiles(), host.containment, host.shellContributions);
        self[0] = terminals;
        this.ui = new HostedUi(id, host.environment.contributions(), host.containment, host.environment.ui(),
            host.environment.onUi(), () -> state != State.CLOSED, hosted.loader(),
            () -> host.environment.dark().getAsBoolean() ? Variant.DARK : Variant.LIGHT,
            handler -> events().subscribe(AppEvents.THEME_CHANGED, event -> handler.accept(event.variant())),
            host.environment.windows(), terminals, host.environment.appearance(), host.environment.appearanceSubscriber(),
            host.environment.glass());
        ui.presentation(() -> host.presentation);
        this.kit = new HostedKit(id, () -> state != State.CLOSED, ui::kitThemes, ui.appearance(), host.containment, () -> host.animations.getAsBoolean(), action -> host.shortcuts.apply(action), () -> host.kit.get());
        ui.kit(kit);
        this.palette = new HostedPalette(id, gate, host.environment.contributions(), host.containment, ui, terminals, hosted.iconHue());
        this.fileSources = new HostedFileSources(id, gate, host.environment.contributions(), host.containment, ui, terminals);
        this.notifications = new HostedNotifications(id, hosted.info().name(), host.environment.notifications(), host.containment,
            host.environment.ui(), host.environment.onUi(), ui::icon, ui::validateIcon);
        this.commandLine = new HostedCommandLine(id, host.commands, ui, terminals, () -> host.commandClient);
    }

    private javax.swing.Icon uiIcon(String name){return ui.icon(name);}

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
        kit.close();
        ui.closeAll();
        terminals.closeAll();
        notifications.close();
        host.clipboard.ownerStopped(id);
        host.commands.stopped(id);
        List<Subscription> copy;
        synchronized (owned) { copy = new ArrayList<>(owned); owned.clear(); }
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).close();
        host.bus.removeAll(id);
        host.activities.failAll(id, reason);
        host.connections.closeAll(id);
        declared.close();
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

    @Override public dev.jasper.sdk.settings.PluginSettings settings() { return declared; }

    @Override public Actions actions() { return ui.actions(); }
    /** UI thread: whether this plugin registered the action and has not closed it. */
    boolean ownsAction(String actionId) { return ui.owns(actionId); }
    @Override public Toolbar toolbar() { return ui.toolbar(); }
    @Override public Menus menus() { return ui.menus(); }
    @Override public StatusBar statusBar() { return ui.statusBar(); }
    @Override public dev.jasper.sdk.ui.UiKit kit() { return kit; }
    @Override public Appearance appearance() { return ui.appearance(); }
    @Override public Panels panels() { return ui.panels(); }
    @Override public Rail rail() { return ui.rail(); }
    @Override public Windows windows() { return ui.windows(); }
    @Override public Terminals terminals() { return terminals; }
        @Override public Palette palette() { return palette; }
        @Override public dev.jasper.sdk.files.FileSources fileSources() { return fileSources; }

        @Override public Notifications notifications() { return notifications; }

        @Override public dev.jasper.sdk.cli.CommandLine commandLine() { return commandLine; }

        @Override public Platform platform() {
            return new Platform() {
                @Override public void openInEditor(Path file) {
                    requireOpen();
                    Objects.requireNonNull(file, "file");
                    background().execute(() -> {
                        try {
                            host.fileEditors.openFromWorker(file,
                                () -> host.environment.terminals().activeWindow().map(dev.jasper.app.terminals.WindowEntry::id), host.environment.editor());
                        } catch (RuntimeException failure) {
                            log().log(System.Logger.Level.WARNING, "Could not open " + file + " in an editor", failure);
                            notifications.group().error("Could not open a file in an editor", String.valueOf(file.getFileName()), failure);
                        }
                    });
                }
                @Override public void browse(URI uri) {
                    requireOpen();
                    URI address = webAddress(uri);
                    background().execute(() -> {
                        try { host.environment.browser().accept(address); }
                        catch (RuntimeException failure) {
                            log().log(System.Logger.Level.WARNING, "Could not open " + address + " in a browser", failure);
                            notifications.group().error("Could not open a web address", address.toString(), failure);
                        }
                    });
                }
                @Override public boolean copy(String text) {
                    requireOpen();
                    requireUi("Platform.copy");
                    Objects.requireNonNull(text, "text");
                    return copied(() -> host.clipboard.copy(text));
                }
                @Override public boolean copySecret(String text) {
                    requireOpen();
                    requireUi("Platform.copySecret");
                    Objects.requireNonNull(text, "text");
                    return copied(() -> host.clipboard.copySecret(text, id));
                }
                @Override public Optional<String> clipboardText() {
                    requireOpen();
                    requireUi("Platform.clipboardText");
                    gate.require(Capabilities.CLIPBOARD_READ);
                    Optional<String> text = host.clipboard.text();
                    gate.audit(Capabilities.CLIPBOARD_READ, "read " + text.map(String::length).orElse(0) + " characters from the clipboard");
                    return text;
                }
                @Override public Subscription editor(dev.jasper.sdk.ui.FileEditor editor) {
                    requireOpen();
                    requireUi("Platform.editor");
                    gate.require(Capabilities.EDITOR_PROVIDE);
                    Objects.requireNonNull(editor, "editor");
                    dev.jasper.app.lifecycle.Subscription registration = host.fileEditors.register(id, editor,
                        window -> host.environment.terminals().window(window)
                            .<dev.jasper.sdk.WindowOwner>map(entry -> terminals.windowHandle(entry.id())));
                    gate.audit(Capabilities.EDITOR_PROVIDE, "became the editor Jasper opens files with");
                    // HostedUi.closeAll, which teardown runs, closes it when the plugin stops or fails to start.
                    return ui.tracked(registration::close);
                }
            };
        }

        /** Runs a clipboard write; a clipboard that cannot be written is posted in the plugin's group, never thrown. */
        private boolean copied(Runnable write) {
            try {
                write.run();
                return true;
            } catch (IllegalStateException | java.awt.HeadlessException unavailable) {
                log().log(System.Logger.Level.WARNING, "Could not copy to the clipboard", unavailable);
                notifications.group().error("Could not copy", unavailable);
                return false;
            }
        }

        /** Only an absolute http or https address opens in a browser. */
        private static URI webAddress(URI uri) {
            Objects.requireNonNull(uri, "uri");
            String scheme = uri.getScheme();
            if (scheme == null || uri.isOpaque() || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                throw new IllegalArgumentException("Only an http or https address opens in a browser: " + uri);
            return uri;
        }

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
                if (TerminalEvents.owns(topic)) {
                    gate.require(Capabilities.TERMINAL_OBSERVE);
                    if (!observing) { observing = true; gate.audit(Capabilities.TERMINAL_OBSERVE, "subscribed to terminal events"); }
                }
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
            @Override public ConnectionHandle connection(ConnectionSpec spec) {
                requireOpen();
                return host.connections.open(id, spec);
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
