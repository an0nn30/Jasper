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

    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace) {
        return host(data, tables, drainGrace, onEdtValue(Contributions::new), new java.util.concurrent.atomic.AtomicBoolean(true));
    }

    static <T> T onEdtValue(java.util.function.Supplier<T> supplier) {
        var value = new AtomicReference<T>();
        onEdt(() -> value.set(supplier.get()));
        return value.get();
    }

    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark) {
        return host(data, tables, drainGrace, contributions, dark, onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new));
    }

    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals) {
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()),
            (key, message) -> { }, drainGrace, contributions, dark::get, auxiliary, terminals, message -> { }, path -> { })));
    }

    /** UI thread: {@link #headlessWindows(UiState)} over state that is never saved. */
    static AuxiliaryWindows headlessWindows() { return headlessWindows(UiState.inMemory()); }

    /** UI thread: plugin windows over shells that touch no native window. */
    static AuxiliaryWindows headlessWindows(UiState state) {
        return new AuxiliaryWindows(state, surface -> new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new java.awt.Rectangle(0, 0, 10, 10)));
    }

    @Override protected ContractHarness newHarness() {
        Path data;
        try { data = Files.createTempDirectory("jasper-contract"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        Contributions contributions = onEdtValue(Contributions::new);
        var dark = new java.util.concurrent.atomic.AtomicBoolean(true);
        AuxiliaryWindows auxiliary = onEdtValue(AppContractTest::headlessWindows);
        TerminalFixture terminalFixture = onEdtValue(TerminalFixture::new);
        PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry);
        List<ActivityEvent> log = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.TOPIC, log::add));
        return new ContractHarness() {
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
                    .map(panel -> panel.id() + "|" + panel.title() + "|" + panel.defaultRegion()).toList());
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
                    .map(surface -> (surface.kind() == dev.jasper.app.windows.AuxiliarySurface.Kind.DIALOG ? "dialog" : surface.id())
                        + "|" + surface.title() + "|" + surface.shown()).toList());
            }
            @Override public boolean requestClose(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::requestClose).orElse(false));
            }
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
            @Override public void finishCommand(UUID paneId, String command, int exitStatus) { onEdt(() -> terminalFixture.finishCommand(paneId, command, exitStatus)); }
            @Override public void setVariant(dev.jasper.sdk.Variant variant) {
                dark.set(variant == dev.jasper.sdk.Variant.DARK);
                host.bus.publish(EventBus.APP, dev.jasper.sdk.events.AppEvents.THEME_CHANGED, new dev.jasper.sdk.events.AppEvents.ThemeChanged(variant));
            }
            @Override public void stopAll() {
                var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
                onEdt(() -> pending.set(host.stop()));
                CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
            }
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
}
