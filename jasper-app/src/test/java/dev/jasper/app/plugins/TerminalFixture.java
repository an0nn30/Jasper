package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.terminal.session.AttachedConnection;

/** A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. EDT only. */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); dev.jasper.app.terminals.RemoteLocation remote;
        SessionRequest request; SessionAttempt attempt; AttachedConnection connection; boolean everAttached; String status = ""; String state = ""; }
    private static final class Tab { UUID id = UUID.randomUUID(); Window window; String title; List<Pane> panes = new ArrayList<>(); Pane focused; }
    private static final class Window { UUID id = UUID.randomUUID(); List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; }

    final TerminalRegistry registry = new TerminalRegistry();
    private final Map<UUID, Window> windows = new LinkedHashMap<>();
    private final Map<UUID, Tab> tabs = new LinkedHashMap<>();
    private final Map<UUID, Pane> panes = new LinkedHashMap<>();
    /** Kept so a test can still read what was sent to a pane that closed, as the testkit's fake allows. */
    private final Map<UUID, Pane> closedPanes = new LinkedHashMap<>();
    private final List<String> opened = new ArrayList<>();

    private PaneEntry entry(Pane pane) {
        return new PaneEntry(pane.id, pane.tab.id,
            () -> new PaneSnapshot(pane.title, pane.remote == null ? Optional.ofNullable(pane.directory) : Optional.empty(), 80, 24, true,
                pane.state.equals("CONNECTING") ? PaneSnapshot.State.STARTING : pane.state.equals("EXITED") ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING,
                OptionalInt.empty(), Optional.ofNullable(pane.request).map(SessionRequest::providerId), Optional.ofNullable(pane.remote)),
            () -> CompletableFuture.completedFuture(Optional.of("vim")),
            bytes -> pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)), text -> pane.sent.add("paste:" + text),
            () -> Optional.ofNullable(pane.selection), () -> focusPane(pane.id),
            (axis, spec) -> {
                UUID created = switch (spec) {
                    case OpenSpec.Local local -> {
                        opened.add("split|" + pane.id + "|" + axis + "|" + local.directory().map(Path::toString).orElse("-"));
                        yield addPane(pane.tab.id, "split", local.directory().orElse(pane.directory));
                    }
                    case OpenSpec.Session session -> {
                        opened.add("session-split|" + pane.id + "|" + axis + "|" + session.request().title());
                        yield addSessionPane(pane.tab.id, session.request());
                    }
                };
                focusPane(created);
                return Optional.of(entry(panes.get(created)));
            });
    }

    private TabEntry entry(Tab tab) {
        return new TabEntry(tab.id, tab.window.id, () -> tab.panes.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(tab.focused).map(this::entry), () -> tab.title, () -> selectTab(tab));
    }

    UUID addWindow() {
        var window = new Window();
        windows.put(window.id, window);
        window.registration = registry.addWindow(new WindowEntry(window.id, () -> window.tabs.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(window.selected).map(this::entry), () -> registry.activeWindow().map(WindowEntry::id).equals(Optional.of(window.id)),
            () -> opened.add("front|" + window.id), spec -> switch (spec) {
                case OpenSpec.Local local -> {
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-"));
                    yield Optional.of(entry(panes.get(addPane(addTab(window.id, "opened"), "opened", local.directory().orElse(null)))));
                }
                case OpenSpec.Session session -> {
                    opened.add("session-tab|" + window.id + "|" + session.request().title());
                    yield Optional.of(entry(panes.get(addSessionPane(addTab(window.id, session.request().title()), session.request()))));
                }
            }));
        return window.id;
    }

    UUID addTab(UUID windowId, String title) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.window = window; tab.title = title;
        tabs.put(tab.id, tab);
        window.tabs.add(tab);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); selectTab(tab); });
        return tab.id;
    }

    private void selectTab(Tab tab) {
        if (tab.window.selected == tab) return;
        tab.window.selected = tab;
        registry.publish(new TerminalEvent.TabSelected(tab.window.id, tab.id));
    }

    UUID addPane(UUID tabId, String title, Path directory) {
        Tab tab = tabs.get(tabId);
        var pane = new Pane(); pane.tab = tab; pane.title = title; pane.directory = directory;
        panes.put(pane.id, pane);
        tab.panes.add(pane);
        if (tab.focused == null) tab.focused = pane;
        registry.publish(new TerminalEvent.PaneOpened(tab.id, pane.id));
        return pane.id;
    }

    void activateWindow(UUID windowId) { registry.windowActivated(windowId); }

    void focusPane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null) return;
        pane.tab.focused = pane;
        registry.publish(new TerminalEvent.PaneFocused(pane.tab.id, pane.id));
    }

    /** The last pane takes its tab with it, and the last tab its window, as in the application. */
    void closePane(UUID paneId) {
        Pane pane = panes.remove(paneId);
        if (pane == null) return;
        closedPanes.put(paneId, pane);
        if (pane.attempt != null) pane.attempt.cancel();
        if (pane.connection != null) { pane.connection.close().run(); pane.connection = null; }
        Tab tab = pane.tab; Window window = tab.window;
        registry.atomically(() -> {
            tab.panes.remove(pane);
            if (tab.focused == pane) tab.focused = tab.panes.isEmpty() ? null : tab.panes.get(0);
            registry.publish(new TerminalEvent.PaneClosed(tab.id, pane.id));
            if (!tab.panes.isEmpty()) return;
            tabs.remove(tab.id); window.tabs.remove(tab);
            registry.publish(new TerminalEvent.TabClosed(window.id, tab.id));
            if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) selectTab(window.tabs.get(0)); }
            if (window.tabs.isEmpty()) { windows.remove(window.id); window.registration.close(); }
        });
    }

    void select(UUID paneId, String text) { panes.get(paneId).selection = text; }
    List<String> sent(UUID paneId) {
        Pane pane = panes.containsKey(paneId) ? panes.get(paneId) : closedPanes.get(paneId);
        return pane == null ? List.of() : List.copyOf(pane.sent);
    }
    List<String> opened() { return List.copyOf(opened); }

    void finishCommand(UUID paneId, String command, int exitStatus) {
        registry.publish(new TerminalEvent.CommandFinished(paneId, command, OptionalInt.of(exitStatus), Duration.ofMillis(1500),
            Optional.ofNullable(panes.get(paneId)).filter(pane -> pane.remote == null).map(pane -> pane.directory),
            Optional.ofNullable(panes.get(paneId)).map(pane -> pane.remote)));
    }

    private UUID addSessionPane(UUID tabId, SessionRequest request) {
        UUID id = addPane(tabId, request.title(), null);
        panes.get(id).request = request;
        connect(panes.get(id));
        return id;
    }

    /** What a pane does for a provided session, without a pane: the attempt, the attach, the exit and the close. */
    private void connect(Pane pane) {
        // Attempts are driven from any thread; the registry belongs to the EDT. Inline when already there, so EDT tests stay synchronous.
        var attempt = new SessionAttempt(pane.id, 80, 24, pane.request.cleanup(),
            task -> { if (javax.swing.SwingUtilities.isEventDispatchThread()) task.run(); else javax.swing.SwingUtilities.invokeLater(task); });
        pane.attempt = attempt;
        pane.state = "CONNECTING"; pane.status = "";
        attempt.onStatus = text -> { if (pane.attempt == attempt) pane.status = text; };
        attempt.onAttached = connection -> {
            if (pane.attempt != attempt || !panes.containsKey(pane.id)) { connection.close().run(); return; }
            pane.connection = connection; pane.everAttached = true; pane.state = "RUNNING"; pane.status = "";
            registry.publish(new TerminalEvent.SessionStarted(pane.id));
            connection.exited().whenComplete((status, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
                if (pane.connection != connection) return;
                connection.close().run();
                pane.connection = null; pane.state = "EXITED";
                pane.status = error == null ? "exit " + status : String.valueOf(error.getMessage());
                registry.publish(new TerminalEvent.SessionExited(pane.id, error == null ? OptionalInt.of(status) : OptionalInt.empty()));
                if (pane.request.closeOnExit()) closePane(pane.id);
            }));
        };
        attempt.onFailed = message -> { if (pane.attempt == attempt) { pane.state = "EXITED"; pane.status = message; } };
        registry.publish(new TerminalEvent.SessionConnecting(pane.id));
        pane.request.connector().accept(attempt);
    }

    String sessionState(UUID paneId) {
        Pane pane = panes.get(paneId);
        return pane == null ? "CLOSED|" : pane.state + "|" + pane.status;
    }

    void cancelSession(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.attempt == null) return;
        pane.attempt.cancel();
        // As in a real pane: with nothing ever shown there is nothing to return to.
        if (!pane.everAttached) closePane(paneId); else { pane.state = "EXITED"; pane.status = "Connection cancelled"; }
    }

    void reconnectSession(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane != null && pane.request != null && pane.state.equals("EXITED")) connect(pane);
    }

    void typeIntoSession(UUID paneId, String text) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.connection == null) return;
        try { pane.connection.input().write(text.getBytes(StandardCharsets.UTF_8)); pane.connection.input().flush(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    String sessionOutput(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.connection == null) return "";
        try {
            var stream = pane.connection.output();
            return new String(stream.readNBytes(stream.available()), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** What the pane's shell reports: a local directory when {@code hostOrEmpty} is empty, a remote one otherwise. */
    void reportDirectory(UUID paneId, String hostOrEmpty, String path) {
        Pane pane = panes.get(paneId);
        if (hostOrEmpty.isEmpty()) { pane.remote = null; pane.directory = Path.of(path); }
        else { pane.remote = new dev.jasper.app.terminals.RemoteLocation(hostOrEmpty, path); }
        registry.publish(new TerminalEvent.DirectoryChanged(paneId, hostOrEmpty.isEmpty() ? Optional.of(Path.of(path)) : Optional.empty(),
            Optional.ofNullable(pane.remote)));
    }
}
