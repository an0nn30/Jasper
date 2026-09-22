package dev.jasper.app.plugins;

import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.SplitAxis;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.sdk.terminal.RemoteDirectory;

/**
 * One plugin's view of the terminal registry. Handles hold ids and the last values they saw, never an entry:
 * every call looks the target up again, so a closed pane is simply absent. Gated methods ask the plugin's
 * {@link CapabilityGate} first. EDT only, except the three sending methods.
 */
final class HostedTerminals implements Terminals {
    private static final System.Logger LOG = System.getLogger(HostedTerminals.class.getName());
    private static final UUID NOWHERE = new UUID(0, 0);

    private final String pluginId;
    private final CapabilityGate gate;
    private final TerminalRegistry registry;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final BooleanSupplier open;
    private final HostedSessions sessions;

    HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi,
                    BooleanSupplier open, HostedSessions sessions) {
        this.pluginId = pluginId; this.gate = gate; this.registry = registry; this.ui = ui; this.onUi = onUi; this.open = open;
        this.sessions = sessions;
    }

    private void requireUi(String what) {
        if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
        if (!onUi.getAsBoolean()) throw new IllegalStateException(what + " must be called on the UI thread: " + pluginId);
    }

    private static PaneInfo info(PaneSnapshot snapshot) {
        SessionState state = switch (snapshot.state()) {
            case STARTING -> SessionState.CONNECTING; case RUNNING -> SessionState.RUNNING; case EXITED -> SessionState.EXITED;
        };
        return new PaneInfo(snapshot.title(), snapshot.workingDirectory(),
            snapshot.remoteDirectory().map(location -> new RemoteDirectory(location.host(), location.path())), snapshot.columns(), snapshot.rows(),
            snapshot.shellIntegration(), snapshot.providerId().isPresent() ? SessionKind.PLUGIN : SessionKind.LOCAL, snapshot.providerId(), state,
            snapshot.exitStatus(), snapshot.shell());
    }

    private final class Window implements WindowHandle {
        private final UUID id;
        Window(UUID id) { this.id = id; }
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() {
            requireUi("tabs");
            return registry.window(id).map(window -> window.tabs().get().stream().<TabHandle>map(Tab::new).toList()).orElse(List.of());
        }
        @Override public Optional<TabHandle> activeTab() {
            requireUi("activeTab");
            return registry.window(id).flatMap(window -> window.selectedTab().get()).map(Tab::new);
        }
        @Override public boolean isActive() { requireUi("isActive"); return registry.window(id).map(window -> window.active().getAsBoolean()).orElse(false); }
        @Override public boolean isOpen() { requireUi("isOpen"); return registry.window(id).isPresent(); }
        @Override public void toFront() { requireUi("toFront"); registry.window(id).ifPresent(window -> window.toFront().run()); }
        @Override public boolean equals(Object other) { return other instanceof WindowHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "window " + id; }
    }

    private final class Tab implements TabHandle {
        private final UUID id;
        private UUID windowId;
        private String title = "";
        Tab(TabEntry entry) { this(entry.id(), entry.windowId()); }
        Tab(UUID id, UUID windowId) { this.id = id; this.windowId = windowId; }
        private Optional<TabEntry> entry() {
            Optional<TabEntry> found = registry.tab(id);
            found.ifPresent(entry -> windowId = entry.windowId());
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { requireUi("window"); entry(); return new Window(windowId); }
        @Override public List<PaneHandle> panes() {
            requireUi("panes");
            return entry().map(entry -> entry.panes().get().stream().<PaneHandle>map(Pane::new).toList()).orElse(List.of());
        }
        @Override public Optional<PaneHandle> activePane() { requireUi("activePane"); return entry().flatMap(entry -> entry.focusedPane().get()).map(Pane::new); }
        @Override public String title() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("title");
            entry().ifPresent(entry -> title = entry.title().get());
            return title;
        }
        @Override public void select() { requireUi("select"); entry().ifPresent(entry -> entry.select().run()); }
        @Override public boolean isOpen() { requireUi("isOpen"); return entry().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof TabHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "tab " + id; }
    }

    private final class Pane implements PaneHandle {
        private final UUID id;
        private UUID tabId;
        private PaneInfo last = PaneInfo.unknown();
        Pane(PaneEntry entry) { this(entry.id(), entry.tabId()); }
        Pane(UUID id, UUID tabId) { this.id = id; this.tabId = tabId; }
        private Optional<PaneEntry> entry() {
            Optional<PaneEntry> found = registry.pane(id);
            found.ifPresent(entry -> tabId = entry.tabId());
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public TabHandle tab() {
            requireUi("tab");
            entry();
            return registry.tab(tabId).<TabHandle>map(Tab::new).orElseGet(() -> new Tab(tabId, NOWHERE));
        }
        @Override public PaneInfo info() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("info");
            entry().ifPresent(entry -> last = HostedTerminals.info(entry.snapshot().get()));
            return last;
        }
        @Override public CompletableFuture<Optional<String>> foregroundJob() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("foregroundJob");
            return entry().map(entry -> entry.foregroundJob().get()).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        }
        @Override public void sendText(String text) {
            byte[] bytes = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
            inject("typed", bytes.length, entry -> entry.write().accept(bytes));
        }
        @Override public void sendBytes(byte[] bytes) {
            byte[] copy = Objects.requireNonNull(bytes, "bytes").clone();
            inject("wrote", copy.length, entry -> entry.write().accept(copy));
        }
        @Override public void paste(String text) {
            Objects.requireNonNull(text, "text");
            inject("pasted", text.getBytes(StandardCharsets.UTF_8).length, entry -> entry.paste().accept(text));
        }
        private void inject(String verb, int byteCount, Consumer<PaneEntry> action) {
            gate.require(Capabilities.TERMINAL_INJECT);
            if (!open.getAsBoolean()) return;
            gate.audit(Capabilities.TERMINAL_INJECT, verb + " " + byteCount + " bytes into pane " + id);
            Runnable deliver = () -> entry().ifPresentOrElse(action,
                () -> LOG.log(System.Logger.Level.DEBUG, "Plugin " + pluginId + " sent input to the closed pane " + id));
            if (onUi.getAsBoolean()) deliver.run(); else ui.accept(deliver);
        }
        @Override public Optional<String> selection() {
            gate.require(Capabilities.TERMINAL_SELECTION);
            requireUi("selection");
            Optional<String> selected = entry().flatMap(entry -> entry.selection().get());
            gate.audit(Capabilities.TERMINAL_SELECTION, "read " + selected.map(String::length).orElse(0) + " selected characters from pane " + id);
            return selected;
        }
        @Override public void focus() { requireUi("focus"); entry().ifPresent(entry -> entry.focus().run()); }
        @Override public boolean isOpen() { requireUi("isOpen"); return entry().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof PaneHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "pane " + id; }
    }

    /** A handle for an id an action or a panel was given; the window may already be gone. */
    WindowHandle windowHandle(UUID id) { return new Window(id); }

    /** A handle for an id an action was given; it finds its tab if the pane is open. */
    PaneHandle paneHandle(UUID id) { return registry.pane(id).<PaneHandle>map(Pane::new).orElseGet(() -> new Pane(id, NOWHERE)); }

    @Override public Optional<WindowHandle> activeWindow() { requireUi("activeWindow"); return registry.activeWindow().map(window -> new Window(window.id())); }
    @Override public Optional<PaneHandle> activePane() { requireUi("activePane"); return registry.activePane().map(Pane::new); }
    @Override public List<WindowHandle> windows() {
        requireUi("windows");
        return registry.windows().stream().<WindowHandle>map(window -> new Window(window.id())).toList();
    }
    @Override public Optional<PaneHandle> pane(UUID id) { requireUi("pane"); return registry.pane(id).map(Pane::new); }
    @Override public Optional<TabHandle> tab(UUID id) { requireUi("tab"); return registry.tab(id).map(Tab::new); }
    @Override public Optional<WindowHandle> window(UUID id) { requireUi("window"); return registry.window(id).map(window -> new Window(window.id())); }

    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        OpenSpec spec = spec(request);
        requireUi("openTab");
        Optional<WindowEntry> target = registry.window(window.id());
        if (target.isEmpty()) return Optional.empty();
        gate.audit(capability(request), "opened a tab in window " + window.id());
        return target.get().openTab().apply(spec).map(Pane::new);
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        OpenSpec spec = spec(request);
        requireUi("split");
        Optional<PaneEntry> entry = registry.pane(target.id());
        if (entry.isEmpty()) return Optional.empty();
        gate.audit(capability(request), "split pane " + target.id());
        return entry.get().split().apply(direction == Direction.RIGHT ? SplitAxis.RIGHT : SplitAxis.DOWN, spec)
            .map(Pane::new);
    }

    /** Every request kind names its capability here, before anything else happens. */
    private OpenSpec spec(OpenRequest request) {
        return switch (Objects.requireNonNull(request, "request")) {
            case OpenRequest.Local local -> { gate.require(Capabilities.TERMINAL_OPEN); yield new OpenSpec.Local(local.spec().workingDirectory()); }
            case OpenRequest.Session session -> { gate.require(Capabilities.SESSION_PROVIDE); yield new OpenSpec.Session(sessions.request(session.spec())); }
        };
    }

    private static String capability(OpenRequest request) {
        return request instanceof OpenRequest.Session ? Capabilities.SESSION_PROVIDE : Capabilities.TERMINAL_OPEN;
    }

    /** A handle that finds its tab lazily, for callers that are not on the UI thread. */
    PaneHandle detachedPaneHandle(UUID id) { return new Pane(id, NOWHERE); }

    /** The plugin is stopping. */
    void closeAll() { sessions.cancelAll(); }
}
