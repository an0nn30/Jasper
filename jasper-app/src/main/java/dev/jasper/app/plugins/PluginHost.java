package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.TerminalRegistry;

/**
 * Plugin lifetimes on the UI thread: start in the order given, roll back a failed start completely,
 * stop in reverse. It knows nothing about jars or descriptors; see {@link HostedPlugin}.
 */
final class PluginHost {
    private static final System.Logger LOG = System.getLogger(PluginHost.class.getName());

    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, PluginRuntime.NotificationHost notifications, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser,
                       java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
                       java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber,
                       Consumer<PluginRuntime.Failure> failures, GlassSource glass) {
        /** Contained failures are only logged and counted; no workspace glass. */
        Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, PluginRuntime.NotificationHost notifications, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser,
                       java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
                       java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber) {
            this(ui, onUi, dataDirectory, configReport, drainGrace, contributions, dark, windows, terminals, notifications,
                editor, settingsFiles, clearReports, profiles, browser, appearance, appearanceSubscriber, failure -> { }, GlassSource.NONE);
        }

        /** No workspace glass. */
        Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, PluginRuntime.NotificationHost notifications, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser,
                       java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
                       java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber,
                       Consumer<PluginRuntime.Failure> failures) {
            this(ui, onUi, dataDirectory, configReport, drainGrace, contributions, dark, windows, terminals, notifications,
                editor, settingsFiles, clearReports, profiles, browser, appearance, appearanceSubscriber, failures, GlassSource.NONE);
        }

        /** Contained failures are only logged and counted. */
        Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, PluginRuntime.NotificationHost notifications, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser,
                       java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
                       java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber,
                       GlassSource glass) {
            this(ui, onUi, dataDirectory, configReport, drainGrace, contributions, dark, windows, terminals, notifications,
                editor, settingsFiles, clearReports, profiles, browser, appearance, appearanceSubscriber, failure -> { }, glass);
        }

        Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, PluginRuntime.NotificationHost notifications, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser) {
            this(ui, onUi, dataDirectory, configReport, drainGrace, contributions, dark, windows, terminals, notifications,
                editor, settingsFiles, clearReports, profiles, browser, null, null, failure -> { }, GlassSource.NONE);
        }
    }

    record Outcome(PluginStatus.State state, String reason) { }

    java.util.function.BooleanSupplier animations = () -> true;
    /** The look every plugin's appearance reports; set before plugins start. */
    volatile dev.jasper.sdk.ui.Presentation presentation = HostedPresentation.standard();
    /** The look's defaults, whose table for a plugin defaults its declared settings (spec 5.8); set before plugins start. */
    volatile dev.jasper.app.config.DefaultSource defaults = dev.jasper.app.config.DefaultSource.CORE;
    /** Hears each look default a plugin cannot use: one line naming the plugin, the key and why. */
    Consumer<String> lookDefaultProblem = message -> LOG.log(System.Logger.Level.WARNING, message);
    /** The kit plugins' views are made with: the presentation's, or the installed look and feel's for a runtime given none. */
    java.util.function.Supplier<dev.jasper.ui.kit.Kit> kit = dev.jasper.ui.swing.FlatLafInstaller::installedKit;
    /** The keymap text a plugin's menus show beside an item's action. */
    java.util.function.Function<String, String> shortcuts = action -> "";
    /** The clipboard every plugin's Platform writes to; the application shares it with its own copies. */
    dev.jasper.app.platform.HostClipboard clipboard = dev.jasper.app.platform.HostClipboard.system();
    final Environment environment;
    final HostedTypography typography;
    /** Runs cancellation handlers and connection closes off the EDT; outlives every plugin's own executor. */
    final CleanupWorker cleanup = new CleanupWorker();
    private final dev.jasper.app.lifecycle.Subscription terminalBridge;
    private final dev.jasper.sdk.Subscription outcomes;
    final Containment containment;
    final PluginCommands commands;
    /** What plugins add to new local shells ({@code Terminals.environment}). UI thread. */
    final ShellContributions shellContributions;
    /** The editor a plugin provides ({@code Platform.editor}), which Jasper's own opens ask first. */
    final FileEditors fileEditors;
    /** The {@code jasper} command plugins see in {@code CommandLine.client()}; set before plugins start, read on any thread. */
    volatile Optional<Path> commandClient = Optional.empty();
    final EventBus bus;
    final ActivityHub activities;
    final ConnectionHub connections;
    final ServiceRegistry services = new ServiceRegistry();
    private final Map<String, HostedContext> contexts = new LinkedHashMap<>();
    private final Map<String, PluginStatus.State> notRunning = new HashMap<>();

    PluginHost(Environment environment) { this(environment, new HostedTypography()); }

    PluginHost(Environment environment, HostedTypography typography) {
        this.typography = Objects.requireNonNull(typography, "typography");
        this.environment = Objects.requireNonNull(environment);
        this.containment = new Containment(environment.onUi(), environment.failures());
        this.commands = new PluginCommands(environment.ui(), containment);
        this.shellContributions = new ShellContributions(containment);
        this.fileEditors = new FileEditors(environment.ui(), environment.onUi(), containment);
        this.bus = new EventBus(environment.ui(), containment);
        this.activities = new ActivityHub(bus);
        this.connections = new ConnectionHub(bus);
        this.terminalBridge = TerminalBridge.connect(environment.terminals(), bus);
        this.outcomes = bus.subscribe(EventBus.APP, Activities.TOPIC, this::outcome);
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
        var declared = new DeclaredSettings(id, environment.settingsFiles(), containment, environment.ui(), environment.configReport(),
            () -> settingsWritten(id), defaults.plugin(id), lookDefaultProblem);
        var context = new HostedContext(this, hosted, declared);
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
        context.declared.complete();
        return new Outcome(PluginStatus.State.ACTIVE, "");
    }

    boolean active(String pluginId) {
        HostedContext context = contexts.get(pluginId);
        return context != null && context.state == HostedContext.State.ACTIVE;
    }

    /** UI thread: whether the plugin has not stopped and has this action registered. */
    boolean ownsAction(String pluginId, String actionId) {
        requireUi();
        HostedContext context = contexts.get(pluginId);
        return context != null && context.state != HostedContext.State.CLOSED && context.ownsAction(actionId);
    }

    void settingsChanged() { settingsChanged(Set.of()); }

    /** Pushes each active plugin's current values; plugins in {@code reloaded} rerun their listeners even when equal. */
    void settingsChanged(Set<String> reloaded) {
        requireUi();
        for (HostedContext context : List.copyOf(contexts.values()))
            if (context.state == HostedContext.State.ACTIVE) {
                String id = context.hosted.info().id();
                context.declared.reload(reloaded.contains(id));
            }
    }

    /** A plugin saved a setting: its file's old reports go and its handles see the file again. UI thread. */
    void settingsWritten(String id) {
        requireUi();
        environment.clearReports().accept(environment.settingsFiles().file(id));
        settingsChanged(Set.of(id));
    }

    /** Reverse start order. The returned futures complete when each plugin's background work has drained. */
    List<CompletableFuture<?>> stop() {
        requireUi();
        terminalBridge.close();
        outcomes.close();
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

    /** Waits for workers, then cleanup callbacks queued by late session attachments. Called on the UI thread. */
    CompletableFuture<Void> workersTerminated() {
        requireUi();
        return CompletableFuture.allOf(contexts.values().stream().map(HostedContext::terminated)
            .toArray(CompletableFuture[]::new)).thenCompose(ignored -> cleanup.drained());
    }

    /** Settings pages for running plugins that declared settings or gave a page of their own, in start order. UI thread. */
    List<SettingsPage> settingsPages(PageContext context) {
        requireUi();
        var pages = new ArrayList<SettingsPage>();
        for (HostedContext hosted : contexts.values()) {
            if (hosted.state != HostedContext.State.ACTIVE) continue;
            var info = hosted.hosted.info();
            var panel = hosted.declared.panel();
            if (panel.isPresent()) {
                var page=new PluginPanelPage(info.id(), info.name(), hosted.declared, panel.get(), containment, context);
                page.kit((HostedKit)hosted.kit());hosted.ownPage(page);pages.add(page);
            }
            else if (hosted.declared.declaresAnything())
                pages.add(new PluginSettingsPage(info.id(), info.name(), hosted.declared, context));
        }
        return pages;
    }

    /** Whether a running plugin has a page in Settings. UI thread. */
    boolean hasSettingsPage(String pluginId) {
        HostedContext hosted = contexts.get(pluginId);
        return hosted != null && hosted.state == HostedContext.State.ACTIVE
            && (hosted.declared.panel().isPresent() || hosted.declared.declaresAnything());
    }

    /**
     * An activity ended (spec section 7, producer 4). One that succeeded or failed is posted in its plugin's
     * {@code <plugin-id>.activities} group, titled after the activity, with its detail as content and its action as the
     * body's; a cancelled one posts nothing. Nor does an activity of a plugin that is no longer running: the host failed
     * it because the plugin stopped or failed to start, and that plugin's incident already says why. UI thread.
     */
    private void outcome(ActivityEvent event) {
        if (event.state() != ActivityEvent.State.SUCCEEDED && event.state() != ActivityEvent.State.FAILED) return;
        String source = event.sourcePluginId();
        if (!active(source)) return;
        try {
            var request = NotificationRequest.of(source + HostedNotifications.ACTIVITIES,
                    event.state() == ActivityEvent.State.SUCCEEDED ? Severity.INFO : Severity.ERROR, event.title())
                .withContent(event.detail()).withSource(source);
            Optional<Runnable> activate = activation(source, event.activateActionId());
            environment.notifications().center().post(activate.map(request::withActivate).orElse(request));
        } catch (IllegalArgumentException unshowable) {
            Containment.logger(source).log(System.Logger.Level.WARNING, "An activity's outcome could not be shown", unshowable);
        }
    }

    /**
     * An activity's own activation: the source plugin's action, invoked in the window the user used last. Empty when
     * the id names no action that plugin registered. UI thread.
     */
    Optional<Runnable> activation(String source, Optional<String> actionId) {
        return actionId.filter(id -> ownsAction(source, id)).<Runnable>map(id -> () -> {
            if (!ownsAction(source, id)) return;
            environment.terminals().activeWindow().ifPresent(window -> environment.contributions().action(id).ifPresent(entry ->
                entry.invoke(new Contributions.Invocation(window.id(), environment.terminals().activePane().map(PaneEntry::id)))));
        });
    }

    String executing() { return containment.executing(); }

    int failures(String pluginId) { return containment.failures(pluginId); }
}
