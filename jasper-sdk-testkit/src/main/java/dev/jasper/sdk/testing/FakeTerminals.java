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
            synchronized (workspace) { model().ifPresent(pane -> pane.sent.add(line)); }
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
        return workspace.window(window.id()).map(target -> switch (request) {
            case OpenRequest.Local local -> {
                workspace.openRequests.add("tab|" + target.id + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-") + profile(local) + program(local));
                FakeWorkspace.Pane pane = workspace.openTerminal(target, "opened", openedInfo(local));
                yield new Pane(pane.id, pane.tab.id);
            }
            case OpenRequest.Session session -> {
                workspace.openRequests.add("session-tab|" + target.id + "|" + session.spec().title());
                FakeWorkspace.Pane pane = workspace.openTerminal(target, session.spec().title(), sessionInfo(session));
                workspace.sessions.connect(context, pane, session.spec());
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
        return workspace.pane(target.id()).map(model -> {
            // A split is a terminal tab of its own in the target's window, as in the application (spec 9.4).
            FakeWorkspace.Window window = model.tab.window;
            FakeWorkspace.Pane pane = switch (request) {
                case OpenRequest.Local local -> {
                    workspace.openRequests.add("split|" + model.id + "|" + direction + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-")
                        + profile(local) + program(local));
                    yield workspace.openTerminal(window, "opened", openedInfo(local));
                }
                case OpenRequest.Session session -> {
                    workspace.openRequests.add("session-split|" + model.id + "|" + direction + "|" + session.spec().title());
                    FakeWorkspace.Pane created = workspace.openTerminal(window, session.spec().title(), sessionInfo(session));
                    workspace.sessions.connect(context, created, session.spec());
                    yield created;
                }
            };
            workspace.focus(pane);
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

    private static PaneInfo openedInfo(OpenRequest.Local local) {
        PaneInfo blank = PaneInfo.unknown();
        return new PaneInfo("", local.spec().workingDirectory(), Optional.empty(), 80, 24, false, blank.kind(), Optional.empty(),
            SessionState.RUNNING, blank.exitStatus(), "sh");
    }

    private PaneInfo sessionInfo(OpenRequest.Session session) {
        return new PaneInfo(session.spec().title(), Optional.empty(), Optional.empty(), 80, 24, false, SessionKind.PLUGIN,
            Optional.of(context.plugin().id()), SessionState.CONNECTING, OptionalInt.empty(), session.spec().title());
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
