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
                           TerminalRegistry terminals, List<String> notices, List<Path> edited) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, new CopyOnWriteArrayList<>());
    }

    /** As above; configuration problems land in {@code diagnostics}, and a file's reload or save clears that file's. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, diagnostics, PluginRuntime.ShellProfiles.NONE);
    }

    /** As above, with the shell profiles plugins list and open. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, diagnostics, profiles, new CopyOnWriteArrayList<>());
    }

    /** As above; the web addresses plugins ask to open land in {@code browsed}. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles,
                           List<java.net.URI> browsed) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, diagnostics, profiles, browsed, null);
    }

    private static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles,
                           List<java.net.URI> browsed, WorkspaceAppearanceFixture appearance) {
        var files = new PluginSettingsFiles(data, diagnostics::add);
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notices::add, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)), profiles,
            browsed::add, appearance == null ? null : appearance::current, appearance == null ? null : appearance::subscribe)));
    }

    /** UI thread: {@link #headlessWindows(UiState)} over state that is never saved. */
    static AuxiliaryWindows headlessWindows() { return headlessWindows(UiState.inMemory()); }

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
        List<String> notices = new CopyOnWriteArrayList<>();
        List<Path> edited = new CopyOnWriteArrayList<>();
        TestShellProfiles shells = new TestShellProfiles();
        List<dev.jasper.app.config.ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
        List<java.net.URI> browsed = new CopyOnWriteArrayList<>();
        var appearance = onEdtValue(WorkspaceAppearanceFixture::new);
        PluginHost host = host(data, Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited,
            diagnostics, shells.source(), browsed, appearance);
        PluginSettingsFiles files = host.environment.settingsFiles();
        List<ActivityEvent> log = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.TOPIC, log::add));
        List<dev.jasper.sdk.activity.ConnectionEvent> connections = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.CONNECTIONS, connections::add));
        List<String> opens = new CopyOnWriteArrayList<>();
        onEdt(() -> contributions.onPaletteRequest(request -> opens.add(request.windowId() + " " + request.scopeId() + " "
            + request.query().orElse("-") + " " + request.rowId().orElse("-"))));
        Map<String, Map<String, dev.jasper.app.palette.PaletteRow>> lastRows = new java.util.HashMap<>();
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
            @Override public javax.swing.JComponent openPanel(String panelId, java.util.UUID windowId) {
                return onEdtValue(() -> contributions.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                    .map(panel -> panel.factory().apply(new dev.jasper.app.contributions.PanelSite(windowId, () -> { }, () -> { }, () -> true)))
                    .orElse(null));
            }
            @Override public List<String> rail() {
                return onEdtValue(() -> contributions.railActions().stream().filter(id -> contributions.action(id).isPresent()).toList());
            }
            @Override public List<String> windows() {
                return onEdtValue(() -> auxiliary.open().stream()
                    .map(surface -> (switch (surface.kind()) { case DIALOG -> "dialog"; case OVERLAY -> "overlay"; case WINDOW -> surface.id(); })
                        + "|" + surface.title() + "|" + surface.shown()).toList());
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
                        @Override public List<String> notices() { return List.copyOf(notices); }
                        @Override public List<Path> openedInEditor() { return List.copyOf(edited); }
                        @Override public List<java.net.URI> browsed() { return List.copyOf(browsed); }
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
