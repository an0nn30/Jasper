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

    /** UI thread: plugin dialogs over shells that touch no native window. */
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
        // Every window is a workspace, as every application window is (workspace plan 3b Rulings 15): a plugin's tab is a
        // content tab of the window that shows it, the window it moves to reports the move, and a window whose last tab
        // left closes, taking its workspace with it.
        // The window manager's part (full windows spec 5): no window is special; a window outlives its last tab, closes
        // when a move takes its last tab, and its only tab stays where it is when asked to move to a new window.
        java.util.function.Supplier<UUID> workspace = () -> {
            UUID id = terminalFixture.addWindow();
            var tabs = contributions.tabs();
            var registration = tabs.addHost(new dev.jasper.app.contributions.WorkspaceHost(id,
                tab -> tab.movingFrom().ifPresentOrElse(from -> terminalFixture.moveTab(tab.id().value(), id),
                    () -> terminalFixture.addContentTab(id, tab.id().value(), tab::title)),
                tab -> { if (!tab.moving()) terminalFixture.closeTab(tab.id().value()); },
                entry -> {
                    for (var other : tabs.inWindow(id)) other.active(other == entry);
                    terminalFixture.selectTab(entry.id().value());
                }, () -> {}, tab -> Optional.empty(),
                (tab, group, side) -> terminalFixture.addContentTab(id, tab.id().value(), tab::title, true),
                (tab, side) -> terminalFixture.splitBeside(tab.id().value(), side),
                tab -> terminalFixture.mayMoveToNewTab(tab.id().value()),
                tab -> {
                    terminalFixture.moveToNewTab(tab.id().value());
                    for (var other : tabs.inWindow(id)) other.active(other == tab);
                },
                tab -> terminalFixture.tabChoices(tab.id().value()),
                (tab, number) -> {
                    if (terminalFixture.moveToTab(tab.id().value(), number))
                        for (var other : tabs.inWindow(id)) other.active(other == tab);
                }));
            terminalFixture.onWindowClosed(id, registration::close);
            return id;
        };
        onEdt(() -> contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(
            () -> terminalFixture.recentWindow().orElseGet(workspace),
            tab -> {
                boolean only = terminalFixture.registry.window(tab.window()).map(window -> window.tabs().get().size() == 1).orElse(false);
                if (only) return;
                contributions.tabs().move(tab, workspace.get());
            }, window -> { }, () -> {
                // A tab placed in a new window while none is open: a window first, then the tab's own, as the window
                // manager's emptyWindow restores first (full windows plan 1, Task 2's Ruling 4).
                if (terminalFixture.recentWindow().isEmpty()) workspace.get();
                return workspace.get();
            })));
        // A restorer's session takes the saved tab's id in the window being restored, as WindowContent.openTerminal takes it
        // (workspace tabs spec 13): registered under that id, it is the tab the restore hands back.
        onEdt(() -> terminalFixture.restoringSessions(
            (provider, window) -> contributions.tabs().slot(provider, window).map(dev.jasper.app.layout.TabId::value),
            (tab, window) -> contributions.tabs().restored(contributions.tabs().add(new dev.jasper.app.layout.TabId(tab),
                dev.jasper.app.contributions.Tab.Kind.TERMINAL, new dev.jasper.app.contributions.TabContent() {
                    private final javax.swing.JComponent view = new javax.swing.JPanel();
                    @Override public javax.swing.JComponent component() { return view; }
                    @Override public void dispose() { }
                }, window)),
            tab -> contributions.tabs().tab(new dev.jasper.app.layout.TabId(tab)).ifPresent(contributions.tabs()::remove)));
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
        List<String> lookProblems = new CopyOnWriteArrayList<>();
        onEdt(() -> host.lookDefaultProblem = lookProblems::add);
        return new ContractHarness() {
            @Override public void setKitDefault(String pluginId, String key, Object rawValue) {
                Object raw = org.tomlj.Toml.parse("v = " + literal(rawValue)).get("v");
                onEdt(() -> {
                    var tables = new java.util.HashMap<String, Map<String, Object>>(host.defaults.plugins());
                    var table = new java.util.LinkedHashMap<String, Object>(host.defaults.plugin(pluginId));
                    table.put(key, raw);
                    tables.put(pluginId, table);
                    host.defaults = new dev.jasper.app.config.DefaultSource(host.defaults.overrides(), tables);
                });
            }
            @Override public List<String> lookDefaultProblems(String pluginId) {
                return lookProblems.stream().filter(line -> line.startsWith(pluginId + ": ")).toList();
            }
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
                        render(contributions, section.entries(), "", lines, true);
                    }
                    return lines;
                });
            }
            @Override public List<String> contextMenu(UUID paneId) {
                return onEdtValue(() -> {
                    var pane = terminalFixture.registry.pane(paneId);
                    if (pane.isEmpty()) return List.<String>of();
                    // The application's rule, as the window reads it while it builds the menu.
                    boolean webAddress = pane.get().selection().get().flatMap(dev.jasper.app.contributions.MenuEntry.WebAddressItem::addressIn).isPresent();
                    List<String> lines = new java.util.ArrayList<>();
                    boolean first = true;
                    for (var section : contributions.menus()) {
                        if (section.target().type() != dev.jasper.app.contributions.MenuTarget.Type.CONTEXT) continue;
                        if (!first) lines.add("===");
                        first = false;
                        render(contributions, section.entries(), "", lines, webAddress);
                    }
                    return lines;
                });
            }
            @Override public boolean chooseFromContextMenu(UUID paneId, String actionId) {
                return onEdtValue(() -> {
                    var pane = terminalFixture.registry.pane(paneId);
                    if (pane.isEmpty()) return false;
                    UUID windowId = terminalFixture.registry.tab(pane.get().tabId()).orElseThrow().windowId();
                    Optional<java.net.URI> address = pane.get().selection().get().flatMap(dev.jasper.app.contributions.MenuEntry.WebAddressItem::addressIn);
                    for (var section : contributions.menus()) {
                        if (section.target().type() != dev.jasper.app.contributions.MenuTarget.Type.CONTEXT) continue;
                        Optional<dev.jasper.app.contributions.MenuEntry> item = shown(contributions, section.entries(), actionId, address.isPresent());
                        if (item.isEmpty()) continue;
                        var action = contributions.action(actionId).filter(dev.jasper.app.contributions.ActionEntry::enabled);
                        action.ifPresent(entry -> entry.invoke(new Contributions.Invocation(windowId, Optional.of(paneId),
                            item.get() instanceof dev.jasper.app.contributions.MenuEntry.WebAddressItem ? address : Optional.empty())));
                        return action.isPresent();
                    }
                    return false;
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
            /**
             * Open panels' sites and components, keyed by window id and panel id ({@code <window>|<panel>}), as each
             * window's dock keeps its own instance (full windows spec 9), and the header metadata each panel last published.
             */
            final java.util.Map<String, dev.jasper.app.contributions.PanelSite> sites = new java.util.HashMap<>();
            final java.util.Map<String, javax.swing.JComponent> components = new java.util.HashMap<>();
            // A panel request (Panels.show and toggle) builds the panel in the window it names, as that window's dock does.
            { onEdt(() -> contributions.onPanelRequest(request -> {
                if (request.op() != dev.jasper.app.contributions.Contributions.PanelRequest.Op.HIDE
                        && terminalFixture.registry.window(request.windowId()).isPresent())
                    build(request.windowId(), request.panelId());
            })); }
            final java.util.Map<String, dev.jasper.app.contributions.PanelHeaderState> headers = new java.util.HashMap<>();
            /**
             * Each registered panel's place, one cell per panel id as a window's dock keeps it, built or not and across
             * {@code closePanel}; the site reads it, {@code placePanel} moves it.
             */
            final java.util.Map<String, dev.jasper.app.contributions.PanelSite.Placement[]> placements = new java.util.HashMap<>();
            /** The registered panel's place cell, its default slot, docked, until it is moved. */
            dev.jasper.app.contributions.PanelSite.Placement[] placementOf(dev.jasper.app.contributions.PanelEntry panel) {
                return placements.computeIfAbsent(panel.id(), id -> new dev.jasper.app.contributions.PanelSite.Placement[] {
                    new dev.jasper.app.contributions.PanelSite.Placement(panel.defaultSlot(), dev.jasper.app.dock.ToolWindowMode.DOCKED) });
            }
            @Override public javax.swing.JComponent openPanel(String panelId) {
                return onEdtValue(() -> {
                    UUID window = terminalFixture.recentWindow().orElse(null);
                    return window == null ? null : build(window, panelId);
                });
            }
            /** UI thread: builds the panel in {@code window}'s dock, or returns the instance built there. */
            javax.swing.JComponent build(UUID window, String panelId) {
                String key = window + "|" + panelId;
                if (components.containsKey(key)) return components.get(key);
                return contributions.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                    .map(panel -> {
                        var current = placementOf(panel);
                        var site = new dev.jasper.app.contributions.PanelSite(window, () -> { }, () -> { }, () -> true, header -> headers.put(panelId, header),
                            () -> current[0]);
                        var built = panel.factory().apply(site);
                        if (built != null) { sites.put(key, site); components.put(key, built); }
                        return built;
                    })
                    .orElse(null);
            }
            /** The keys of the panel's open instances, one per window. */
            List<String> keysOf(String panelId) { return sites.keySet().stream().filter(key -> key.endsWith("|" + panelId)).toList(); }
            @Override public String panelHeader(String panelId) {
                return onEdtValue(() -> {
                    var header = headers.getOrDefault(panelId, dev.jasper.app.contributions.PanelHeaderState.EMPTY);
                    var labels = header.actions().stream().map(dev.jasper.app.contributions.PanelHeaderState.Action::label).toList();
                    return header.detail() + "|" + header.active() + "|" + (labels.isEmpty() ? "-" : String.join(";", labels));
                });
            }
            @Override public boolean runPanelHeaderAction(String panelId, String label) {
                return onEdtValue(() -> {
                    var action = headers.getOrDefault(panelId, dev.jasper.app.contributions.PanelHeaderState.EMPTY)
                        .actions().stream().filter(a -> a.label().equals(label)).findFirst();
                    action.ifPresent(a -> a.invoke().run());
                    return action.isPresent();
                });
            }
            @Override public String viewRoute(dev.jasper.sdk.ui.UiView view) { return ((HostedKitView) view).component().route().name(); }
            @Override public void closePanel(String panelId) {
                onEdt(() -> { for (String key : keysOf(panelId)) { components.remove(key); sites.remove(key).notifyClosed(); } });
            }
            @Override public void placePanel(String panelId, dev.jasper.sdk.ui.PanelPlacement placement) {
                onEdt(() -> {
                    var panel = contributions.panels().stream().filter(entry -> entry.id().equals(panelId)).findFirst();
                    if (panel.isEmpty()) return;
                    var current = placementOf(panel.get());
                    var value = new dev.jasper.app.contributions.PanelSite.Placement(
                        dev.jasper.app.dock.Slot.of(dev.jasper.app.dock.Side.valueOf(placement.anchor().name()), placement.split()),
                        dev.jasper.app.dock.ToolWindowMode.valueOf(placement.mode().name()));
                    if (value.equals(current[0])) return;
                    current[0] = value;
                    // A panel that is not open is built there and hears nothing, as the dock's.
                    for (String key : keysOf(panelId)) sites.get(key).notifyPlacement(value);
                });
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
                    .map(surface -> (surface.kind() == AuxiliarySurface.Kind.OVERLAY ? "overlay" : "dialog")
                        + "|" + surface.title() + "|" + surface.shown()).toList());
            }
            @Override public String dialogSwatch(String pluginId, String title) {
                return onEdtValue(() -> auxiliary.open().stream()
                    .filter(surface -> surface.title().equals(title))
                    .findFirst().map(surface -> surface.swatch().name()).orElse("-"));
            }
            @Override public boolean requestClose(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream()
                    .filter(surface -> (surface.kind() == AuxiliarySurface.Kind.OVERLAY ? "overlay" : "dialog").equals(windowId)).findFirst()
                    .map(surface -> surface.kind() != AuxiliarySurface.Kind.OVERLAY && surface.requestClose()).orElse(false));
            }
            @Override public boolean requestQuit() { return onEdtValue(() -> contributions.tabs().mayClose(contributions.tabs().hosted()) && contributions.quitGuards().mayQuit()); }
            @Override public UUID addTerminalWindow() { return onEdtValue(workspace); }
            @Override public UUID addContentTab(UUID windowId, String title) {
                return onEdtValue(() -> { UUID id = UUID.randomUUID(); terminalFixture.addContentTab(windowId, id, () -> title); return id; });
            }
            @Override public void moveTab(UUID tabId, UUID windowId) { onEdt(() -> terminalFixture.moveTab(tabId, windowId)); }
            @Override public Optional<UUID> restoreTab(String pluginId, UUID windowId, String title, dev.jasper.sdk.RestoreState state) {
                return onEdtValue(() -> {
                    var restored = contributions.tabs().restore(dev.jasper.app.layout.TabId.random(), pluginId,
                        new dev.jasper.app.contributions.TabRegistry.Restoring(windowId, title,
                            new dev.jasper.app.layout.TabRestore(state.kind(), state.values())));
                    // The window being restored places the tab; here the scripted workspace shows it in that window. A
                    // session's terminal tab is already there, as the window opened it itself.
                    restored.tab().filter(tab -> tab.kind() != dev.jasper.app.contributions.Tab.Kind.TERMINAL)
                        .ifPresent(tab -> terminalFixture.addContentTab(windowId, tab.id().value(), tab::title));
                    return restored.tab().map(tab -> tab.id().value());
                });
            }
            @Override public Optional<dev.jasper.sdk.RestoreState> restoreState(UUID tabId) {
                return onEdtValue(() -> contributions.tabs().tab(new dev.jasper.app.layout.TabId(tabId))
                    .flatMap(dev.jasper.app.contributions.Tab::restore).or(() -> terminalFixture.sessionRestore(tabId))
                    .map(saved -> new dev.jasper.sdk.RestoreState(saved.kind(), saved.state())));
            }
            @Override public void closeTab(UUID tabId) { onEdt(() -> terminalFixture.closeTab(tabId)); }
            @Override public List<List<UUID>> windowTabs(UUID windowId) { return onEdtValue(() -> terminalFixture.windowTabs(windowId)); }
            @Override public UUID addTerminalTab(UUID windowId, String title) { return onEdtValue(() -> terminalFixture.addTab(windowId, title)); }
            @Override public UUID addTerminalPane(UUID tabId, String title, java.nio.file.Path directory) {
                return onEdtValue(() -> terminalFixture.addPane(tabId, title, directory));
            }
            @Override public void holdTerminalPane(UUID paneId) { onEdt(() -> terminalFixture.holdPane(paneId)); }
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
            @Override public void setTerminalTypography(dev.jasper.sdk.ui.TerminalTypography typography) { onEdt(() -> host.typography.publish(typography)); }
            @Override public void setPresentation(dev.jasper.sdk.ui.Presentation presentation) { onEdt(() -> host.presentation = presentation); }
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
            @Override public Map<String, String> tabNamePieces(UUID tabId) {
                return onEdtValue(() -> contributions.tabNamePieces().values(tabId));
            }
            @Override public void declareBundled(String pluginId) {
                bundled.add(pluginId);
                onEdt(() -> host.fileEditors.bundled(Set.copyOf(bundled)));
            }
            @Override public boolean edit(Path file, java.util.OptionalInt line, Optional<UUID> windowId) {
                return onEdtValue(() -> host.fileEditors.edit(file, line, windowId));
            }
            @Override public boolean openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, dev.jasper.sdk.ui.LinkPlacement placement,
                                              Optional<String> target, Optional<UUID> windowId) {
                return onEdtValue(() -> host.webBrowsers.open(address, origin, placement, target, windowId));
            }
            @Override public void commandClient(Path client) { onEdt(() -> host.commandClient = Optional.of(client)); }
            @Override public void close() { stopAll(); flush(); }
        };
    }

    /**
     * Workspace plan 8b's final review, the application alone: Terminal Right beside a content tab, through a real
     * {@code HostedContext}, writes exactly one audit line, naming the content tab.
     */
    @org.junit.jupiter.api.Test void aContentTabsTerminalSplitWritesOneAuditLine() {
        ContractHarness harness = newHarness();
        try {
            UUID main = harness.addTerminalWindow(), shell = harness.addTerminalTab(main, "zsh");
            harness.addTerminalPane(shell, "zsh", Path.of("/"));
            var alpha = new AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
            harness.start(new PluginInfo("test.alpha", "test.alpha", "1.0.0",
                Set.of(dev.jasper.sdk.Capabilities.TERMINAL_OBSERVE, dev.jasper.sdk.Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(), alpha::set);
            var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            var note = new AtomicReference<UUID>();
            harness.ui(() -> {
                var owner = alpha.get().terminals().window(main).orElseThrow();
                var view = alpha.get().kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body",
                    dev.jasper.sdk.ui.ControlState.READY, "Content", dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { });
                notes.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, view));
                note.set(owner.activeTab().orElseThrow().id());
            });
            List<String> lines = HostedTerminalsTest.audited(() -> harness.ui(() ->
                notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, dev.jasper.sdk.terminal.Direction.RIGHT)));
            org.assertj.core.api.Assertions.assertThat(lines).as("one line, for the terminal that opened")
                .containsExactly("Plugin test.alpha used terminal.open: opened a terminal beside content tab " + note.get());
            org.assertj.core.api.Assertions.assertThat(harness.openRequests()).containsExactly("beside|" + note.get() + "|RIGHT");
        } finally {
            harness.close();
        }
    }

    /**
     * Pane flexibility spec 4, the application alone: a plugin that holds {@code terminal.observe} reads other tabs' titles
     * through its content tab's choices, and its first such answer is audited once, as its subscription is; a plugin that
     * does not read none, and writes none.
     */
    @org.junit.jupiter.api.Test void aPluginsFirstTitledTabChoicesAreAuditedOnce() {
        ContractHarness harness = newHarness();
        try {
            UUID main = harness.addTerminalWindow(), shell = harness.addTerminalTab(main, "zsh");
            harness.addTerminalPane(shell, "zsh", Path.of("/"));
            var alpha = new AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
            var beta = new AtomicReference<dev.jasper.sdk.plugin.PluginContext>();
            harness.start(new PluginInfo("test.alpha", "test.alpha", "1.0.0", Set.of(dev.jasper.sdk.Capabilities.TERMINAL_OBSERVE)),
                Set.of(), Set.of(), alpha::set);
            harness.start(new PluginInfo("test.beta", "test.beta", "1.0.0", Set.of()), Set.of(), Set.of(), beta::set);
            var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            var sketch = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
            harness.ui(() -> {
                for (var context : List.of(alpha.get(), beta.get())) {
                    var owner = context.terminals().window(main).orElseThrow();
                    var view = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body",
                        dev.jasper.sdk.ui.ControlState.READY, "Content", dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { });
                    boolean first = context == alpha.get();
                    (first ? notes : sketch).set(context.windows().openTab(
                        new dev.jasper.sdk.ui.ContentSpec(first ? "notes" : "sketch", first ? "Notes" : "Sketch"), owner, view));
                }
            });
            List<String> lines = HostedTerminalsTest.audited(() -> harness.ui(() -> {
                org.assertj.core.api.Assertions.assertThat(sketch.get().tabChoices()).extracting(dev.jasper.sdk.ui.TabChoice::title)
                    .as("without the capability").containsOnly("");
                org.assertj.core.api.Assertions.assertThat(notes.get().tabChoices()).extracting(dev.jasper.sdk.ui.TabChoice::title)
                    .as("with it").contains("zsh");
                notes.get().tabChoices();
            }));
            org.assertj.core.api.Assertions.assertThat(lines).as("one line, for the plugin that read titles")
                .containsExactly("Plugin test.alpha used terminal.observe: read tab titles");
        } finally {
            harness.close();
        }
    }

    /** A section's entries as lines; web-address items only when {@code webAddress} says they show. */
    private static void render(Contributions contributions, List<dev.jasper.app.contributions.MenuEntry> entries, String indent,
                               List<String> lines, boolean webAddress) {
        for (var entry : entries) {
            switch (entry) {
                case dev.jasper.app.contributions.MenuEntry.Item item -> {
                    if (contributions.action(item.actionId()).isPresent()) lines.add(indent + "item:" + item.actionId());
                }
                case dev.jasper.app.contributions.MenuEntry.WebAddressItem item -> {
                    if (webAddress && contributions.action(item.actionId()).isPresent()) lines.add(indent + "web-address:" + item.actionId());
                }
                case dev.jasper.app.contributions.MenuEntry.Separator separator -> lines.add(indent + "---");
                case dev.jasper.app.contributions.MenuEntry.Submenu submenu -> {
                    lines.add(indent + "submenu:" + submenu.title());
                    render(contributions, submenu.entries(), indent + "  ", lines, webAddress);
                }
            }
        }
    }

    /** The first entry of a section, submenus in place, that shows an item for the action; web-address items only when {@code webAddress}. */
    private static Optional<dev.jasper.app.contributions.MenuEntry> shown(Contributions contributions, List<dev.jasper.app.contributions.MenuEntry> entries,
                                                                          String actionId, boolean webAddress) {
        if (contributions.action(actionId).isEmpty()) return Optional.empty();
        for (var entry : entries) {
            boolean shows = switch (entry) {
                case dev.jasper.app.contributions.MenuEntry.Item item -> item.actionId().equals(actionId);
                case dev.jasper.app.contributions.MenuEntry.WebAddressItem item -> webAddress && item.actionId().equals(actionId);
                case dev.jasper.app.contributions.MenuEntry.Separator separator -> false;
                case dev.jasper.app.contributions.MenuEntry.Submenu submenu -> false;
            };
            if (shows) return Optional.of(entry);
            if (entry instanceof dev.jasper.app.contributions.MenuEntry.Submenu submenu) {
                var nested = shown(contributions, submenu.entries(), actionId, webAddress);
                if (nested.isPresent()) return nested;
            }
        }
        return Optional.empty();
    }

    /** A raw TOML value as source text. */
    private static String literal(Object raw) {
        if (raw instanceof List<?> items)
            return items.stream().map(item -> dev.jasper.app.settings.TomlLiterals.string((String) item))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        return dev.jasper.app.settings.TomlLiterals.format(raw);
    }
}
