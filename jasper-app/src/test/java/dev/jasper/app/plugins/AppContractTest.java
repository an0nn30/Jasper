package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.testing.contract.ContractCall;
import dev.jasper.sdk.testing.contract.ContractHarness;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import dev.jasper.app.terminals.TerminalRegistry;
import java.util.UUID;
import java.util.Optional;

/** The application's runtime must pass the same contract as the testkit fake, on the real EDT. */
class AppContractTest extends PluginContractTest {
    static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) { action.run(); return; }
        try { SwingUtilities.invokeAndWait(action); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(failure.getCause());
        }
    }

    static PluginHost host(Path data, Duration drainGrace) {
        return host(data, drainGrace, onEdtValue(Contributions::new), new java.util.concurrent.atomic.AtomicBoolean(true));
    }

    static <T> T onEdtValue(java.util.function.Supplier<T> supplier) {
        var value = new AtomicReference<T>();
        onEdt(() -> value.set(supplier.get()));
        return value.get();
    }

    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark) {
        return host(data, drainGrace, contributions, dark, onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new),
            new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
    }

    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> problems, List<Path> edited) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, problems, edited, new CopyOnWriteArrayList<>());
    }

    /** As above; configuration problems land in {@code diagnostics}, and a file's reload or save clears that file's. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> problems, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, problems, edited, diagnostics, PluginRuntime.ShellProfiles.NONE);
    }

    /** As above, with the shell profiles plugins list and open. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> problems, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, problems, edited, diagnostics, profiles, new CopyOnWriteArrayList<>());
    }

    /** As above; the web addresses plugins ask to open land in {@code browsed}. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> problems, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles,
                           List<java.net.URI> browsed) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, new NotificationsFixture(problems), edited, diagnostics, profiles, browsed, null,
            GlassSource.NONE);
    }

    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, NotificationsFixture notifications, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles,
                           List<java.net.URI> browsed, WorkspaceAppearanceFixture appearance, GlassSource glass) {
        var files = new PluginSettingsFiles(data, diagnostics::add);
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notifications.host, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)), profiles,
            browsed::add, appearance == null ? null : appearance::current, appearance == null ? null : appearance::subscribe,
            glass)));
    }

    /** UI thread: {@link #headlessWindows(UiState)} over state that is never saved. */
    static AuxiliaryWindows headlessWindows() { return headlessWindows(UiState.inMemory()); }

    /** UI thread: {@link #headlessWindows()} whose file chooser is {@code chooser}, for tests that pick files. */
    static AuxiliaryWindows headlessWindows(dev.jasper.app.windows.PathChooser chooser) {
        return new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> java.util.Optional.empty()), chooser);
    }

    /** UI thread: plugin windows over shells that touch no native window. */
    static AuxiliaryWindows headlessWindows(UiState state) {
        return new AuxiliaryWindows(state, surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> java.util.Optional.empty()));
    }

    /** The application's value for an SDK profile; the command is never run by the contract fixture. */
    static dev.jasper.app.launch.ShellProfile appProfile(dev.jasper.sdk.terminal.ShellProfileInfo info) {
        var kind = switch (info.kind()) {
            case LOGIN_SHELL -> dev.jasper.app.launch.ShellProfile.Kind.LOGIN_SHELL;
            case LOCAL_SHELL -> dev.jasper.app.launch.ShellProfile.Kind.LOCAL_SHELL;
            case WSL -> dev.jasper.app.launch.ShellProfile.Kind.WSL;
            case CUSTOM -> dev.jasper.app.launch.ShellProfile.Kind.CUSTOM;
        };
        return new dev.jasper.app.launch.ShellProfile(info.id(), info.name(), kind, info.detail(), List.of("sh"));
    }

    @Override protected ContractHarness newHarness() {
        Path data;
        try { data = Files.createTempDirectory("jasper-contract"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        Contributions contributions = onEdtValue(Contributions::new);
        var dark = new java.util.concurrent.atomic.AtomicBoolean(true);
        AuxiliaryWindows auxiliary = onEdtValue(AppContractTest::headlessWindows);
        TerminalFixture terminalFixture = onEdtValue(TerminalFixture::new);
        onEdt(() -> contributions.content().setFactory(separate -> {
            UUID id = terminalFixture.addWindow();
            contributions.content().addHost(new dev.jasper.app.contributions.ContentHost(id, separate,
                entry -> {}, entry -> {}, entry -> {
                    for (var other : contributions.content().inWindow(id)) other.active(other == entry);
                }, () -> {}));
            return id;
        }));
        var notifications = new NotificationsFixture();
        List<Path> edited = new CopyOnWriteArrayList<>();
        TestShellProfiles shells = new TestShellProfiles();
        List<dev.jasper.app.config.ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
        List<java.net.URI> browsed = new CopyOnWriteArrayList<>();
        var appearance = onEdtValue(WorkspaceAppearanceFixture::new);
        double[] opacity = {1.0};
        List<Runnable> glassListeners = new java.util.ArrayList<>();
        var glass = new GlassSource(component -> opacity[0], listener -> {
            glassListeners.add(listener);
            return new dev.jasper.app.lifecycle.Subscription(() -> glassListeners.remove(listener));
        });
        PluginHost host = host(data, Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notifications, edited,
            diagnostics, shells.source(), browsed, appearance, glass);
        boolean[] clipboardBusy = {false};
        var clipboardContents = new java.awt.datatransfer.Clipboard("contract") {
            @Override public synchronized void setContents(java.awt.datatransfer.Transferable contents, java.awt.datatransfer.ClipboardOwner owner) {
                if (clipboardBusy[0]) throw new IllegalStateException("cannot open system clipboard");
                super.setContents(contents, owner);
            }
        };
        List<Runnable> clipboardClears = new CopyOnWriteArrayList<>();
        onEdt(() -> host.clipboard = new dev.jasper.app.platform.HostClipboard(() -> clipboardContents,
            (delay, task) -> { clipboardClears.add(task); return () -> clipboardClears.remove(task); }));
        PluginSettingsFiles files = host.environment.settingsFiles();
        List<ActivityEvent> log = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.TOPIC, log::add));
        List<dev.jasper.sdk.activity.ConnectionEvent> connections = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.CONNECTIONS, connections::add));
        List<String> opens = new CopyOnWriteArrayList<>();
        onEdt(() -> contributions.onPaletteRequest(request -> opens.add(request.windowId() + " " + request.scopeId() + " "
            + request.query().orElse("-") + " " + request.rowId().orElse("-"))));
        Map<String, Map<String, dev.jasper.app.palette.PaletteRow>> lastRows = new java.util.HashMap<>();
        List<Subcommands.Owner> declared = new java.util.concurrent.CopyOnWriteArrayList<>();
        Set<String> bundled = java.util.concurrent.ConcurrentHashMap.newKeySet();
        return new ContractHarness() {
            @Override public void setSetting(String pluginId, String key, Object rawValue) {
                Path file = files.file(pluginId);
                try {
                    String text = Files.exists(file) ? Files.readString(file) : "";
                    var document = dev.jasper.app.settings.TomlDocument.parse(text).set(org.tomlj.Toml.parseDottedKey(key), literal(rawValue));
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, document.text());
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
                onEdt(() -> {
                    var changed = files.refresh(true);
                    for (String id : changed) diagnostics.removeIf(diagnostic -> diagnostic.file().equals(files.file(id)));
                    host.settingsChanged(changed);
                });
            }
            @Override public java.util.Optional<Object> savedSetting(String pluginId, String key) {
                Path file = files.file(pluginId);
                try {
                    if (!Files.exists(file)) return java.util.Optional.empty();
                    return java.util.Optional.ofNullable(dev.jasper.app.settings.TomlDocument.leaves(Files.readString(file))
                        .get(org.tomlj.Toml.parseDottedKey(key)));
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
            @Override public List<String> settingsReports(String pluginId) {
                return diagnostics.stream().filter(diagnostic -> diagnostic.file().equals(files.file(pluginId)))
                    .map(diagnostic -> diagnostic.key() + ": " + diagnostic.message()).toList();
            }
            @Override public void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
                onEdt(() -> host.start(new HostedPlugin(info, requires, optional,
                    Set.of(PluginContractTest.class.getPackageName()), PluginContractTest.class.getClassLoader(), () -> plugin)));
            }
            @Override public boolean active(String pluginId) { return host.active(pluginId); }
            @Override public void ui(Runnable action) { onEdt(action); }
            @Override public void flush() {
                do { onEdt(() -> { }); } while (host.bus.pending() > 0);
                // Closes and cancellation handlers run on the cleanup thread; an exit is noticed on the EDT after that thread completed a future.
                host.cleanup.drained().join();
                do { onEdt(() -> { }); } while (host.bus.pending() > 0);
            }
            @Override public <T> void publishApp(Topic<T> topic, T payload) { host.bus.publish(EventBus.APP, topic, payload); }
            @Override public List<ActivityEvent> activityLog() { return List.copyOf(log); }
            @Override public List<dev.jasper.sdk.activity.ConnectionEvent> connectionLog() { return List.copyOf(connections); }
            @Override public List<String> actions() {
                return onEdtValue(() -> contributions.actions().stream()
                    .map(action -> action.id() + "|" + action.title() + "|" + action.enabled()).toList());
            }
            @Override public boolean invoke(String actionId, java.util.UUID windowId, java.util.UUID paneIdOrNull) {
                return onEdtValue(() -> contributions.action(actionId).filter(dev.jasper.app.contributions.ActionEntry::enabled).map(action -> {
                    action.invoke(new Contributions.Invocation(windowId, java.util.Optional.ofNullable(paneIdOrNull)));
                    return true;
                }).orElse(false));
            }
            @Override public List<String> toolbar() {
                return onEdtValue(() -> {
                    List<String> lines = new java.util.ArrayList<>();
                    for (var entry : contributions.toolbar()) {
                        switch (entry) {
                            case dev.jasper.app.contributions.ToolbarEntry.Button button -> {
                                if (contributions.action(button.actionId()).isPresent()) lines.add("button:" + button.actionId());
                            }
                            case dev.jasper.app.contributions.ToolbarEntry.Dropdown dropdown -> {
                                List<String> live = dropdown.actionIds().stream().filter(id -> contributions.action(id).isPresent()).toList();
                                if (!live.isEmpty()) lines.add("menu:" + dropdown.title() + ":" + String.join(",", live));
                            }
                            case dev.jasper.app.contributions.ToolbarEntry.View view -> lines.add("view");
                        }
                    }
                    return lines;
                });
            }
            @Override public List<String> menu(String target) {
                return onEdtValue(() -> {
                    List<String> lines = new java.util.ArrayList<>();
                    boolean first = true;
                    for (var section : contributions.menus()) {
                        var where = section.target();
                        String key = switch (where.type()) {
                            case STANDARD -> where.key(); case TOP_LEVEL -> "top:" + where.key(); case CONTEXT -> "context";
                        };
                        if (!key.equals(target)) continue;
                        if (!first) lines.add("===");
                        first = false;
                        render(contributions, section.entries(), "", lines);
                    }
                    return lines;
                });
            }
            @Override public List<String> status() {
                return onEdtValue(() -> contributions.status().stream().filter(dev.jasper.app.contributions.StatusEntry::visible)
                    .map(item -> item.id() + "|" + (item.left() ? "LEFT" : "RIGHT") + "|" + item.text() + "|"
                        + (item.tooltip() == null ? "" : item.tooltip()) + "|" + (item.actionId() == null ? "" : item.actionId())).toList());
            }
            @Override public List<String> panels() {
                return onEdtValue(() -> contributions.panels().stream()
                    .map(panel -> panel.id() + "|" + panel.title() + "|" + panel.defaultSlot().side()
                        + (panel.defaultSlot().split() ? "+split" : "")
                        + (panel.namedToggle() ? "|" + panel.toggleActionId() : "")).toList());
            }
            /** Open instances' sites and the header metadata each last published, keyed by panel id and window, as a window keeps them. */
            final java.util.Map<String, dev.jasper.app.contributions.PanelSite> sites = new java.util.HashMap<>();
            final java.util.Map<String, dev.jasper.app.contributions.PanelHeaderState> headers = new java.util.HashMap<>();
            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) {
                String key = panelId + "|" + windowId;
                return onEdtValue(() -> contributions.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                    .map(panel -> {
                        var site = new dev.jasper.app.contributions.PanelSite(windowId, () -> { }, () -> { }, () -> true, header -> headers.put(key, header));
                        var built = panel.factory().apply(site);
                        if (built != null) sites.put(key, site);
                        return built;
                    })
                    .orElse(null));
            }
            @Override public String panelHeader(String panelId, java.util.UUID windowId) {
                return onEdtValue(() -> {
                    var header = headers.getOrDefault(panelId + "|" + windowId, dev.jasper.app.contributions.PanelHeaderState.EMPTY);
                    var labels = header.actions().stream().map(dev.jasper.app.contributions.PanelHeaderState.Action::label).toList();
                    return header.detail() + "|" + header.active() + "|" + (labels.isEmpty() ? "-" : String.join(";", labels));
                });
            }
            @Override public boolean runPanelHeaderAction(String panelId, java.util.UUID windowId, String label) {
                return onEdtValue(() -> {
                    var action = headers.getOrDefault(panelId + "|" + windowId, dev.jasper.app.contributions.PanelHeaderState.EMPTY)
                        .actions().stream().filter(a -> a.label().equals(label)).findFirst();
                    action.ifPresent(a -> a.invoke().run());
                    return action.isPresent();
                });
            }
            @Override public String viewRoute(dev.jasper.sdk.ui.UiView view) { return ((HostedKitView) view).component().route().name(); }
            @Override public void closePanel(String panelId, java.util.UUID windowId) {
                onEdt(() -> { var site = sites.remove(panelId + "|" + windowId); if (site != null) site.notifyClosed(); });
            }
            /** Each toolbar view's instance per window, built once as a window builds it when it opens; empty when it failed. */
            final java.util.Map<dev.jasper.app.contributions.ToolbarEntry.View, java.util.Map<UUID, Optional<dev.jasper.app.contributions.ToolbarEntry.Mounted>>> toolbarViews =
                new java.util.IdentityHashMap<>();
            /** Windows whose toolbar views closed: like a closed window, they build none again. */
            final java.util.Set<UUID> closedToolbarWindows = new java.util.HashSet<>();
            @Override public List<String> openToolbarViews(UUID windowId) {
                return onEdtValue(() -> {
                    List<String> roots = new java.util.ArrayList<>();
                    if (closedToolbarWindows.contains(windowId) || terminalFixture.registry.window(windowId).isEmpty()) return roots;
                    for (var entry : contributions.toolbar()) {
                        if (!(entry instanceof dev.jasper.app.contributions.ToolbarEntry.View view)) continue;
                        var built = toolbarViews.computeIfAbsent(view, key -> new java.util.HashMap<>());
                        var mounted = built.get(windowId);
                        if (mounted == null) built.put(windowId, mounted = view.factory().apply(windowId));
                        else if (mounted.isPresent()) {
                            // Asks the host again, as a second pass of a window's toolbar could: it keeps the window's view.
                            var again = view.factory().apply(windowId);
                            if (again.isPresent()) throw new AssertionError("The host built a second toolbar view for " + windowId);
                        }
                        mounted.ifPresent(shown -> roots.add(((dev.jasper.ui.swing.KitView) shown.component()).spec().root().id()));
                    }
                    return roots;
                });
            }
            @Override public void closeToolbarViews(UUID windowId) {
                onEdt(() -> {
                    closedToolbarWindows.add(windowId);
                    for (var built : toolbarViews.values()) {
                        var mounted = built.remove(windowId);
                        if (mounted != null) mounted.ifPresent(dev.jasper.app.contributions.ToolbarEntry.Mounted::close);
                    }
                });
            }
            @Override public void clickToolbarViewButton(UUID windowId, String buttonId) {
                onEdt(() -> {
                    for (var built : toolbarViews.values()) {
                        var button = built.getOrDefault(windowId, Optional.empty())
                            .map(mounted -> WorkspaceContentRouteTest.named(mounted.component(), buttonId, javax.swing.AbstractButton.class)).orElse(null);
                        if (button != null) { button.doClick(); return; }
                    }
                    throw new AssertionError("No toolbar view button " + buttonId + " in " + windowId);
                });
            }
            @Override public List<String> rail() {
                return onEdtValue(() -> contributions.railActions().stream().filter(id -> contributions.action(id).isPresent()).toList());
            }
            @Override public List<String> windows() {
                return onEdtValue(() -> auxiliary.open().stream()
                    .map(surface -> (switch (surface.kind()) { case DIALOG -> "dialog"; case OVERLAY -> "overlay"; case WINDOW -> surface.id(); })
                        + "|" + surface.title() + "|" + surface.shown()).toList());
            }
            @Override public String dialogSwatch(String pluginId, String title) {
                return onEdtValue(() -> auxiliary.open().stream()
                    .filter(surface -> surface.kind() != AuxiliarySurface.Kind.WINDOW && surface.title().equals(title))
                    .findFirst().map(surface -> surface.swatch().name()).orElse("-"));
            }
            @Override public boolean requestClose(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::requestClose).orElse(false));
            }
            @Override public boolean closeCommand(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::closeCommand).orElse(false));
            }
            @Override public boolean requestQuit() { return onEdtValue(() -> contributions.content().mayClose(contributions.content().entries()) && auxiliary.mayQuit() && contributions.quitGuards().mayQuit()); }
            @Override public UUID addTerminalWindow() { return onEdtValue(terminalFixture::addWindow); }
            @Override public UUID addTerminalTab(UUID windowId, String title) { return onEdtValue(() -> terminalFixture.addTab(windowId, title)); }
            @Override public UUID addTerminalPane(UUID tabId, String title, java.nio.file.Path directory) {
                return onEdtValue(() -> terminalFixture.addPane(tabId, title, directory));
            }
            @Override public void activateTerminalWindow(UUID windowId) { onEdt(() -> terminalFixture.activateWindow(windowId)); }
            @Override public void focusTerminalPane(UUID paneId) { onEdt(() -> terminalFixture.focusPane(paneId)); }
            @Override public void closeTerminalPane(UUID paneId) { onEdt(() -> terminalFixture.closePane(paneId)); }
            @Override public void selectInPane(UUID paneId, String text) { onEdt(() -> terminalFixture.select(paneId, text)); }
            @Override public List<String> sentToPane(UUID paneId) { return onEdtValue(() -> terminalFixture.sent(paneId)); }
            @Override public void reportDirectory(UUID paneId, String hostOrEmpty, String path) { onEdt(() -> terminalFixture.reportDirectory(paneId, hostOrEmpty, path)); }
            @Override public String sessionState(UUID paneId) { return onEdtValue(() -> terminalFixture.sessionState(paneId)); }
            @Override public void cancelSession(UUID paneId) { onEdt(() -> terminalFixture.cancelSession(paneId)); }
            @Override public void reconnectSession(UUID paneId) { onEdt(() -> terminalFixture.reconnectSession(paneId)); }
            @Override public void typeIntoSession(UUID paneId, String text) { onEdt(() -> terminalFixture.typeIntoSession(paneId, text)); }
            @Override public String sessionOutput(UUID paneId) { return onEdtValue(() -> terminalFixture.sessionOutput(paneId)); }
            @Override public List<String> openRequests() {
                return onEdtValue(() -> terminalFixture.opened().stream().filter(line -> !line.startsWith("front|")).toList());
            }
            @Override public void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles) {
                onEdt(() -> shells.set(profiles.stream().map(AppContractTest::appProfile).toList()));
            }
            @Override public void finishCommand(UUID paneId, String command, int exitStatus) { onEdt(() -> terminalFixture.finishCommand(paneId, command, exitStatus)); }
            @Override public void setWorkspace(dev.jasper.sdk.Variant variant, dev.jasper.sdk.ui.WorkspacePalette palette,
                    dev.jasper.sdk.ui.EditorPalette editor, dev.jasper.sdk.ui.SyntaxPalette syntax) {
                onEdt(() -> appearance.set(variant, palette, editor, syntax));
            }
            @Override public void setWorkspaceOpacity(double next) {
                // Like a window's glass: listeners hear only real changes, on the UI thread.
                onEdt(() -> {
                    if (opacity[0] == next) return;
                    opacity[0] = next;
                    for (Runnable listener : List.copyOf(glassListeners)) listener.run();
                });
            }
            @Override public void setVariant(dev.jasper.sdk.Variant variant) {
                onEdt(() -> appearance.setVariant(variant));
                dark.set(variant == dev.jasper.sdk.Variant.DARK);
                host.bus.publish(EventBus.APP, dev.jasper.sdk.events.AppEvents.THEME_CHANGED, new dev.jasper.sdk.events.AppEvents.ThemeChanged(variant));
            }
            @Override public void stopAll() {
                var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
                onEdt(() -> pending.set(host.stop()));
                CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
            }
            @Override public void declareSubcommand(String pluginId, String name, String summary) {
                declared.add(new Subcommands.Owner(name, summary, pluginId, pluginId));
                onEdt(() -> host.commands.declare(Subcommands.of(declared)));
            }
            @Override public ContractCall call(String name, List<String> arguments, Path workingDirectory, UUID paneOrNull) {
                var out = new StringBuffer();
                var err = new StringBuffer();
                var all = new java.util.ArrayList<String>();
                all.add(name);
                all.addAll(arguments);
                var invocation = new dev.jasper.app.commandline.Invocation(all, workingDirectory,
                    java.util.Optional.ofNullable(paneOrNull), out::append, err::append);
                var exit = host.commands.dispatch(invocation).toCompletableFuture();
                onEdt(() -> { });
                return new ContractCall() {
                    @Override public int exitCode() {
                        try { return exit.get(5, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    }
                    @Override public boolean finished() { return exit.isDone(); }
                    @Override public String out() { return out.toString(); }
                    @Override public String err() { return err.toString(); }
                    @Override public void cancel() { invocation.cancel(); onEdt(() -> { }); }
                };
            }
                        private dev.jasper.app.palette.PaletteScope scope(String scopeId) {
                            return contributions.scopes().stream().filter(scope -> scope.id().equals(scopeId)).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("No such scope: " + scopeId));
                        }
                        private dev.jasper.app.palette.PaletteContext context(UUID windowId, UUID paneIdOrNull) {
                            var target = new dev.jasper.app.palette.PaletteTarget(text -> { }, () -> { }, Optional::empty, () -> true,
                                Optional.of(windowId), Optional.ofNullable(paneIdOrNull));
                            return new dev.jasper.app.palette.PaletteContext(true, target, 5);
                        }
                        private dev.jasper.app.palette.PaletteRow row(String scopeId, String rowId) {
                            var row = lastRows.getOrDefault(scopeId, Map.of()).get(rowId);
                            if (row == null) throw new IllegalArgumentException("Search the scope first; no such row: " + rowId);
                            return row;
                        }
                        private static dev.jasper.app.palette.PaletteVerb verb(dev.jasper.app.palette.PaletteScope scope, String verbId) {
                            return scope.verbs().stream().filter(verb -> verb.id().equals(verbId)).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("No such verb: " + verbId));
                        }
                        @Override public List<String> fileSources() {
                            return onEdtValue(() -> contributions.fileSources().stream().map(source -> source.id() + "|" + source.heading()).toList());
                        }
                        @Override public List<String> scopes() {
                            return onEdtValue(() -> contributions.scopes().stream().map(scope -> scope.id() + "|" + scope.label() + "|"
                                + String.join(",", scope.verbs().stream().map(dev.jasper.app.palette.PaletteVerb::id).toList())).toList());
                        }
                        @Override public String scopePresentation(String scopeId) {
                            return onEdtValue(() -> { var scope = scope(scopeId); return scope.hue().name() + "|" + scope.itemNoun() + "|"
                                + String.join(",", scope.verbs().stream().map(verb -> verb.role().name()).toList()); });
                        }
                        @Override public List<String> searchScope(String scopeId, String query, UUID windowId, UUID paneIdOrNull) {
                            return onEdtValue(() -> {
                                var results = scope(scopeId).search(query, context(windowId, paneIdOrNull));
                                var rows = new java.util.LinkedHashMap<String, dev.jasper.app.palette.PaletteRow>();
                                for (var row : results.rows()) rows.put(row.id(), row);
                                lastRows.put(scopeId, rows);
                                return results.rows().stream().map(row -> row.id() + "|" + row.title() + "|" + row.enabled()).toList();
                            });
                        }
                        @Override public boolean availableInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
                            return onEdtValue(() -> { var scope = scope(scopeId); return scope.available(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull)); });
                        }
                        @Override public Optional<String> stepInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
                            return onEdtValue(() -> { var scope = scope(scopeId);
                                return Optional.ofNullable(scope.step(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull))).map(dev.jasper.app.palette.PaletteStep::title); });
                        }
                        @Override public String completeStep(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull, Map<String, String> values) {
                            return onEdtValue(() -> {
                                var scope = scope(scopeId);
                                var step = scope.step(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull));
                                if (step == null) throw new IllegalArgumentException("That verb shows no step");
                                var answer = new AtomicReference<String>();
                                step.complete().accept(Map.copyOf(values), result -> answer.set(
                                    result.error() != null ? "error:" + result.error()
                                        : result.reopenScopeId() != null ? "reopen:" + result.reopenScopeId() + ":" + (result.reopenRowId() == null ? "-" : result.reopenRowId())
                                            + ":" + (result.reopenQuery() == null ? "-" : result.reopenQuery())
                                        : "done"));
                                return java.util.Objects.requireNonNull(answer.get(), "the step did not answer");
                            });
                        }
                        @Override public void executeInScope(String scopeId, String rowId, String verbId, UUID windowId, UUID paneIdOrNull) {
                            onEdt(() -> { var scope = scope(scopeId); scope.execute(row(scopeId, rowId), verb(scope, verbId), context(windowId, paneIdOrNull)); });
                        }
                        @Override public List<String> paletteOpens() { return List.copyOf(opens); }
                        @Override public List<String> notifications() { return notifications.lines(); }
                        @Override public List<String> notificationGroups() { return notifications.groups(); }
                        @Override public boolean runNotificationAction(String title, String label) { return notifications.runAction(title, label); }
                        @Override public boolean clickNotification(String title) { return notifications.click(title); }
                        @Override public List<Path> openedInEditor() { return List.copyOf(edited); }
                        @Override public List<java.net.URI> browsed() { return List.copyOf(browsed); }
                        @Override public java.util.Optional<String> clipboard() { return onEdtValue(() -> host.clipboard.text()); }
                        @Override public void userCopies(String text) {
                            onEdt(() -> clipboardContents.setContents(new java.awt.datatransfer.StringSelection(text), null));
                        }
                        @Override public void clipboardBusy(boolean busy) { clipboardBusy[0] = busy; }
                        @Override public void expireClipboardSecret() { onEdt(() -> List.copyOf(clipboardClears).forEach(Runnable::run)); }
            @Override public Map<String, String> shellVariables(Map<String, String> jasperEnvironment) {
                return onEdtValue(() -> host.shellContributions.variables(jasperEnvironment));
            }
            @Override public void declareBundled(String pluginId) {
                bundled.add(pluginId);
                onEdt(() -> host.fileEditors.bundled(Set.copyOf(bundled)));
            }
            @Override public boolean edit(Path file, java.util.OptionalInt line, Optional<UUID> windowId) {
                return onEdtValue(() -> host.fileEditors.edit(file, line, windowId));
            }
            @Override public void commandClient(Path client) { onEdt(() -> host.commandClient = Optional.of(client)); }
            @Override public void close() { stopAll(); flush(); }
        };
    }

    private static void render(Contributions contributions, List<dev.jasper.app.contributions.MenuEntry> entries, String indent, List<String> lines) {
        for (var entry : entries) {
            switch (entry) {
                case dev.jasper.app.contributions.MenuEntry.Item item -> {
                    if (contributions.action(item.actionId()).isPresent()) lines.add(indent + "item:" + item.actionId());
                }
                case dev.jasper.app.contributions.MenuEntry.Separator separator -> lines.add(indent + "---");
                case dev.jasper.app.contributions.MenuEntry.Submenu submenu -> {
                    lines.add(indent + "submenu:" + submenu.title());
                    render(contributions, submenu.entries(), indent + "  ", lines);
                }
            }
        }
    }

    /** A raw TOML value as source text. */
    private static String literal(Object raw) {
        if (raw instanceof List<?> items)
            return items.stream().map(item -> dev.jasper.app.settings.TomlLiterals.string((String) item))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        return dev.jasper.app.settings.TomlLiterals.format(raw);
    }
}
