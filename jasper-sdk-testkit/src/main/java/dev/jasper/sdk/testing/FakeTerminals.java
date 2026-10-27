package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.TabNamePiece;
import dev.jasper.sdk.terminal.TabNamePieceSpec;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import java.util.OptionalInt;

/** One fake plugin's view of the scripted workspace, with the application's capability rules. */
final class FakeTerminals implements Terminals {
    private final FakePluginContext context;
    private final FakeWorkspace workspace;

    FakeTerminals(FakePluginContext context, FakeWorkspace workspace) { this.context = context; this.workspace = workspace; }

    void require(String capability) {
        if (!context.plugin().capabilities().contains(capability)) throw new MissingCapabilityException(context.plugin().id(), capability);
    }

    private final class Window implements WindowHandle {
        private final UUID id;
        FakeWorkspace hostWorkspace() { return workspace; }
        Window(UUID id) { this.id = id; }
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() {
            context.requireOpen();
            return workspace.window(id).map(window -> window.tabs.stream().<TabHandle>map(tab -> new Tab(tab.id, id, tab.kind)).toList()).orElse(List.of());
        }
        @Override public Optional<TabHandle> activeTab() {
            context.requireOpen();
            return workspace.window(id).map(window -> window.selected).map(tab -> new Tab(tab.id, id, tab.kind));
        }
        @Override public boolean isActive() { context.requireOpen(); return workspace.lastActive != null && workspace.lastActive.open && workspace.lastActive.id.equals(id); }
        @Override public boolean isOpen() { context.requireOpen(); return workspace.window(id).isPresent(); }
        @Override public void toFront() { context.requireOpen(); workspace.window(id).ifPresent(window -> workspace.openRequests.add("front|" + id)); }
        @Override public boolean equals(Object other) { return other instanceof WindowHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private final class Tab implements TabHandle {
        private final UUID id;
        private final TabHandle.Kind kind;
        private UUID windowId;
        private String title = "";
        Tab(UUID id, UUID windowId, TabHandle.Kind kind) { this.id = id; this.windowId = windowId; this.kind = kind; }
        private Optional<FakeWorkspace.Tab> model() {
            Optional<FakeWorkspace.Tab> found = workspace.tab(id);
            found.ifPresent(tab -> windowId = tab.window.id);
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { context.requireOpen(); model(); return new Window(windowId); }
        @Override public TabHandle.Kind kind() { return kind; }
        @Override public Optional<PaneHandle> terminal() {
            context.requireOpen();
            return model().map(tab -> tab.pane).filter(pane -> pane.open).map(pane -> new Pane(pane.id, id));
        }
        @Override public String title() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            model().ifPresent(tab -> title = tab.title);
            return title;
        }
        @Override public Map<String, String> namePieces() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            return model().filter(tab -> tab.open && tab.kind == TabHandle.Kind.TERMINAL).map(tab -> {
                var merged = new java.util.HashMap<>(context.host().tabNamePieces(id));
                merged.putAll(tab.namePieces);
                merged.values().removeIf(String::isBlank);
                return Map.copyOf(merged);
            }).orElse(Map.of());
        }
        @Override public void select() { context.requireOpen(); model().ifPresent(workspace::select); }
        @Override public boolean isOpen() { context.requireOpen(); return model().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof TabHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private final class Pane implements PaneHandle {
        private final UUID id;
        private UUID tabId;
        private PaneInfo last = PaneInfo.unknown();
        Pane(UUID id, UUID tabId) { this.id = id; this.tabId = tabId; }
        private Optional<FakeWorkspace.Pane> model() {
            Optional<FakeWorkspace.Pane> found = workspace.pane(id);
            found.ifPresent(pane -> tabId = pane.tab.id);
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public TabHandle tab() {
            context.requireOpen();
            model();
            return new Tab(tabId, workspace.tab(tabId).map(tab -> tab.window.id).orElse(new UUID(0, 0)), TabHandle.Kind.TERMINAL);
        }
        @Override public PaneInfo info() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            model().ifPresent(pane -> {
                var info = pane.info;
                var remote = info.remoteDirectory().or(() -> java.util.Optional.ofNullable(pane.session == null ? null : pane.session.directory));
                last = new PaneInfo(info.title(), info.workingDirectory(), remote, info.columns(), info.rows(), info.shellIntegration(),
                    info.kind(), info.providerPluginId(), info.state(), info.exitStatus(), info.shell());
            });
            return last;
        }
        @Override public CompletableFuture<Optional<String>> foregroundJob() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            return CompletableFuture.completedFuture(model().map(pane -> pane.job));
        }
        @Override public void sendText(String text) { send("write:" + Objects.requireNonNull(text, "text")); }
        @Override public void sendBytes(byte[] bytes) { send("write:" + new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8)); }
        @Override public void paste(String text) { send("paste:" + Objects.requireNonNull(text, "text")); }
        private void send(String line) {
            require(Capabilities.TERMINAL_INJECT);
            if (context.state == FakePluginContext.State.CLOSED) return;
            // A pane that has not started drops what is sent to it, as Jasper's does (workspace tabs spec 13).
            synchronized (workspace) { model().filter(pane -> pane.held == null).ifPresent(pane -> pane.sent.add(line)); }
        }
        @Override public Optional<String> selection() {
            require(Capabilities.TERMINAL_SELECTION);
            context.requireOpen();
            return model().map(pane -> pane.selection);
        }
        @Override public void focus() { context.requireOpen(); model().ifPresent(workspace::focus); }
        @Override public boolean isOpen() { context.requireOpen(); return model().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof PaneHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    boolean ownsWindow(WindowHandle handle) { return handle instanceof Window window && window.hostWorkspace() == workspace; }

    boolean ownsOpenWindow(WindowHandle handle) {
        return handle instanceof Window window && window.hostWorkspace() == workspace && workspace.window(window.id()).isPresent();
    }

    WindowHandle windowHandle(UUID id) { return new Window(id); }
    PaneHandle paneHandle(UUID id) { return new Pane(id, workspace.pane(id).map(pane -> pane.tab.id).orElse(new UUID(0, 0))); }

    @Override public Optional<WindowHandle> activeWindow() {
        context.requireOpen();
        return Optional.ofNullable(workspace.lastActive).filter(window -> window.open).map(window -> new Window(window.id));
    }
    @Override public Optional<PaneHandle> activePane() { context.requireOpen(); return workspace.activePane().map(pane -> new Pane(pane.id, pane.tab.id)); }
    @Override public List<WindowHandle> windows() { context.requireOpen(); return workspace.windows.stream().<WindowHandle>map(window -> new Window(window.id)).toList(); }
    @Override public Optional<PaneHandle> pane(UUID id) { context.requireOpen(); return workspace.pane(id).map(pane -> new Pane(pane.id, pane.tab.id)); }
    @Override public Optional<TabHandle> tab(UUID id) { context.requireOpen(); return workspace.tab(id).map(tab -> new Tab(tab.id, tab.window.id, tab.kind)); }
    @Override public Optional<WindowHandle> window(UUID id) { context.requireOpen(); return workspace.window(id).map(window -> new Window(window.id)); }

    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        requireFor(request);
        context.requireOpen();
        if (!knownProfile(request)) return Optional.empty();
        return workspace.window(window.id())
            // While a restorer runs, the window being restored takes only the saved tab (workspace plan 6b Rulings 24).
            .filter(target -> workspace.slot == null || workspace.slot.window != target
                || request instanceof OpenRequest.Session && workspace.slot.taken == null && workspace.slot.owner == context)
            .map(target -> switch (request) {
            case OpenRequest.Local local -> {
                workspace.openRequests.add("tab|" + target.id + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-") + profile(local) + program(local));
                FakeWorkspace.Pane pane = workspace.openTerminal(target, "opened", openedInfo(local));
                yield new Pane(pane.id, pane.tab.id);
            }
            case OpenRequest.Session session -> {
                // A session a restorer opens in the window being restored is the saved tab: it comes back there, placed and
                // not shown, and waits, not started, until its tab is shown (workspace tabs spec 13).
                var slot = workspace.takeSlot(context).filter(found -> found.window == target);
                FakeWorkspace.Window into = target;
                workspace.openRequests.add("session-tab|" + into.id + "|" + session.spec().title());
                FakeWorkspace.Pane pane;
                if (slot.isPresent()) {
                    pane = workspace.placeTerminal(into, session.spec().title(), sessionInfo(session));
                    slot.get().taken = pane.tab;
                    workspace.sessions.hold(context, pane, session.spec());
                } else {
                    pane = workspace.openTerminal(target, session.spec().title(), sessionInfo(session));
                    workspace.sessions.connect(context, pane, session.spec());
                }
                yield new Pane(pane.id, pane.tab.id);
            }
        });
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        requireFor(request);
        context.requireOpen();
        if (!knownProfile(request)) return Optional.empty();
        return workspace.pane(target.id())
            // While a restorer runs, the window being restored takes only the saved tab, a split there too, as the application's
            // window decides it (workspace plan 6b Rulings 24).
            .filter(model -> workspace.slot == null || workspace.slot.window != model.tab.window
                || request instanceof OpenRequest.Session && workspace.slot.taken == null && workspace.slot.owner == context)
            .map(model -> {
            // A split is a terminal tab of its own in a new pane right after the target, in its tab (tabs and panes spec 4).
            FakeWorkspace.Window window = model.tab.window;
            // A session a restorer splits off in the window being restored is the saved tab, placed as openTab places it.
            var slot = request instanceof OpenRequest.Session ? workspace.takeSlot(context).filter(found -> found.window == window)
                : Optional.<FakeWorkspace.Slot>empty();
            FakeWorkspace.Pane pane = switch (request) {
                case OpenRequest.Local local -> {
                    workspace.openRequests.add("split|" + model.id + "|" + direction + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-")
                        + profile(local) + program(local));
                    yield workspace.openTerminal(window, "opened", openedInfo(local), model.tab);
                }
                case OpenRequest.Session session -> {
                    workspace.openRequests.add("session-split|" + model.id + "|" + direction + "|" + session.spec().title());
                    if (slot.isPresent()) {
                        FakeWorkspace.Pane placed = workspace.placeTerminal(window, session.spec().title(), sessionInfo(session));
                        slot.get().taken = placed.tab;
                        workspace.sessions.hold(context, placed, session.spec());
                        yield placed;
                    }
                    FakeWorkspace.Pane created = workspace.openTerminal(window, session.spec().title(), sessionInfo(session), model.tab);
                    workspace.sessions.connect(context, created, session.spec());
                    yield created;
                }
            };
            // A restored session is not shown: its tab connects when it is.
            if (slot.isEmpty()) workspace.focus(pane);
            return new Pane(pane.id, pane.tab.id);
        });
    }

    @Override public List<ShellProfileInfo> profiles() {
        require(Capabilities.TERMINAL_OPEN);
        context.requireOpen();
        return workspace.profiles;
    }

    @Override public Subscription onProfilesChanged(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        require(Capabilities.TERMINAL_OPEN);
        context.requireOpen();
        Runnable entry = listener::run;
        workspace.profileListeners.add(entry);
        Subscription subscription = () -> workspace.profileListeners.remove(entry);
        context.own(subscription);
        return subscription;
    }

    @Override public Subscription environment(Function<Map<String, String>, Map<String, String>> contributor) {
        Objects.requireNonNull(contributor, "contributor");
        require(Capabilities.TERMINAL_ENVIRONMENT);
        context.requireOpen();
        return context.host().contributeShell(context, contributor);
    }

    @Override public TabNamePiece addTabNamePiece(TabNamePieceSpec spec) {
        Objects.requireNonNull(spec, "spec");
        require(Capabilities.TERMINAL_OBSERVE);
        context.requireOpen();
        return context.host().addTabNamePiece(context, spec);
    }

    /** The default shell, or a profile the test listed; anything else opens nothing, as in the application. */
    private boolean knownProfile(OpenRequest request) {
        if (!(request instanceof OpenRequest.Local local) || local.spec().profile().isEmpty()) return true;
        String id = local.spec().profile().get();
        if (workspace.profiles.stream().anyMatch(profile -> profile.id().equals(id))) return true;
        context.log().log(System.Logger.Level.WARNING, "Unknown shell profile " + id + "; nothing opened");
        return false;
    }

    private static String profile(OpenRequest.Local local) { return local.spec().profile().map(id -> "|" + id).orElse(""); }

    private static String program(OpenRequest.Local local) {
        return local.spec().command().map(command -> "|run:" + String.join(" ", command)).orElse("");
    }

    static PaneInfo openedInfo(OpenRequest.Local local) {
        PaneInfo blank = PaneInfo.unknown();
        return new PaneInfo("", local.spec().workingDirectory(), Optional.empty(), 80, 24, false, blank.kind(), Optional.empty(),
            SessionState.RUNNING, blank.exitStatus(), "sh");
    }

    private PaneInfo sessionInfo(OpenRequest.Session session) {
        return new PaneInfo(session.spec().title(), Optional.empty(), Optional.empty(), 80, 24, false, SessionKind.PLUGIN,
            Optional.of(context.plugin().id()), SessionState.CONNECTING, OptionalInt.empty(), session.spec().title());
    }

    /** A held pane's snapshot: not started, with no grid and no shell integration, as Jasper reports a restored pane. */
    static PaneInfo notStarted(PaneInfo info) {
        return new PaneInfo(info.title(), info.workingDirectory(), info.remoteDirectory(), 0, 0, false, info.kind(),
            info.providerPluginId(), SessionState.NOT_STARTED, OptionalInt.empty(), info.shell());
    }

    /** A copy of a snapshot in another session state; an exit status is kept only for an exited session. */
    static PaneInfo withState(PaneInfo info, SessionState state, OptionalInt exitStatus) {
        return new PaneInfo(info.title(), info.workingDirectory(), info.remoteDirectory(), info.columns(), info.rows(), info.shellIntegration(),
            info.kind(), info.providerPluginId(), state, state == SessionState.EXITED ? exitStatus : OptionalInt.empty(), info.shell());
    }

    /** Every request kind names its capability here, before anything else happens. */
    private void requireFor(OpenRequest request) {
        switch (Objects.requireNonNull(request, "request")) {
            case OpenRequest.Local local -> require(Capabilities.TERMINAL_OPEN);
            case OpenRequest.Session session -> {
                require(Capabilities.SESSION_PROVIDE);
                session.spec().icon().ifPresent(context.kit()::validateIcon);
            }
        }
    }
}
