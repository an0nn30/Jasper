package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        Window(UUID id) { this.id = id; }
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() {
            context.requireOpen();
            return workspace.window(id).map(window -> window.tabs.stream().<TabHandle>map(tab -> new Tab(tab.id, id)).toList()).orElse(List.of());
        }
        @Override public Optional<TabHandle> activeTab() {
            context.requireOpen();
            return workspace.window(id).map(window -> window.selected).map(tab -> new Tab(tab.id, id));
        }
        @Override public boolean isActive() { context.requireOpen(); return workspace.lastActive != null && workspace.lastActive.open && workspace.lastActive.id.equals(id); }
        @Override public boolean isOpen() { context.requireOpen(); return workspace.window(id).isPresent(); }
        @Override public void toFront() { context.requireOpen(); workspace.window(id).ifPresent(window -> workspace.openRequests.add("front|" + id)); }
        @Override public boolean equals(Object other) { return other instanceof WindowHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private final class Tab implements TabHandle {
        private final UUID id;
        private UUID windowId;
        private String title = "";
        Tab(UUID id, UUID windowId) { this.id = id; this.windowId = windowId; }
        private Optional<FakeWorkspace.Tab> model() {
            Optional<FakeWorkspace.Tab> found = workspace.tab(id);
            found.ifPresent(tab -> windowId = tab.window.id);
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { context.requireOpen(); model(); return new Window(windowId); }
        @Override public List<PaneHandle> panes() {
            context.requireOpen();
            return model().map(tab -> tab.panes.stream().<PaneHandle>map(pane -> new Pane(pane.id, id)).toList()).orElse(List.of());
        }
        @Override public Optional<PaneHandle> activePane() { context.requireOpen(); return model().map(tab -> tab.focused).map(pane -> new Pane(pane.id, id)); }
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
            return new Tab(tabId, workspace.tab(tabId).map(tab -> tab.window.id).orElse(new UUID(0, 0)));
        }
        @Override public PaneInfo info() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            model().ifPresent(pane -> last = pane.info);
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

    WindowHandle windowHandle(UUID id) { return new Window(id); }
    PaneHandle paneHandle(UUID id) { return new Pane(id, workspace.pane(id).map(pane -> pane.tab.id).orElse(new UUID(0, 0))); }

    @Override public Optional<WindowHandle> activeWindow() {
        context.requireOpen();
        return Optional.ofNullable(workspace.lastActive).filter(window -> window.open).map(window -> new Window(window.id));
    }
    @Override public Optional<PaneHandle> activePane() { context.requireOpen(); return workspace.activePane().map(pane -> new Pane(pane.id, pane.tab.id)); }
    @Override public List<WindowHandle> windows() { context.requireOpen(); return workspace.windows.stream().<WindowHandle>map(window -> new Window(window.id)).toList(); }
    @Override public Optional<PaneHandle> pane(UUID id) { context.requireOpen(); return workspace.pane(id).map(pane -> new Pane(pane.id, pane.tab.id)); }
    @Override public Optional<TabHandle> tab(UUID id) { context.requireOpen(); return workspace.tab(id).map(tab -> new Tab(tab.id, tab.window.id)); }
    @Override public Optional<WindowHandle> window(UUID id) { context.requireOpen(); return workspace.window(id).map(window -> new Window(window.id)); }

    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        OpenRequest.Local local = local(request);
        context.requireOpen();
        return workspace.window(window.id()).map(target -> {
            workspace.openRequests.add("tab|" + target.id + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-"));
            FakeWorkspace.Tab tab = workspace.addTab(target, "opened");
            FakeWorkspace.Pane pane = workspace.addPane(tab, openedInfo(local));
            return new Pane(pane.id, tab.id);
        });
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        OpenRequest.Local local = local(request);
        context.requireOpen();
        return workspace.pane(target.id()).map(model -> {
            workspace.openRequests.add("split|" + model.id + "|" + direction + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-"));
            FakeWorkspace.Pane pane = workspace.addPane(model.tab, openedInfo(local));
            workspace.focus(pane);
            return new Pane(pane.id, model.tab.id);
        });
    }

    private static PaneInfo openedInfo(OpenRequest.Local local) {
        PaneInfo blank = PaneInfo.unknown();
        return new PaneInfo("", local.spec().workingDirectory(), Optional.empty(), 80, 24, false, blank.kind(), Optional.empty(),
            dev.jasper.sdk.terminal.SessionState.RUNNING, blank.exitStatus());
    }

    private OpenRequest.Local local(OpenRequest request) {
        return switch (Objects.requireNonNull(request, "request")) {
            case OpenRequest.Local local -> { require(Capabilities.TERMINAL_OPEN); yield local; }
        };
    }
}
