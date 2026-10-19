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
import java.util.function.Supplier;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.terminal.session.AttachedConnection;

/**
 * A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. A terminal tab holds one
 * pane, a split opens a terminal tab of its own in the pane's window, focusing a pane selects its tab, a pane closes with
 * its tab and the last tab takes its window with it (workspace tabs spec 9.4); a plugin's tab is a content tab of its
 * window, and a tab moved to another window is reported as a move. EDT only.
 */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); dev.jasper.app.terminals.RemoteLocation remote;
        SessionRequest request; SessionAttempt attempt; AttachedConnection connection; boolean everAttached; String status = ""; String state = ""; String shell = "zsh"; }
    private static final class Tab { UUID id = UUID.randomUUID(); Window window; TabEntry.Kind kind = TabEntry.Kind.TERMINAL; Supplier<String> title; Pane pane; }
    private static final class Window { UUID id = UUID.randomUUID(); List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; Runnable closed = () -> { }; }

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
                OptionalInt.empty(), Optional.ofNullable(pane.request).map(SessionRequest::providerId), Optional.ofNullable(pane.remote), pane.shell),
            () -> CompletableFuture.completedFuture(Optional.of("vim")),
            bytes -> pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)), text -> pane.sent.add("paste:" + text),
            () -> Optional.ofNullable(pane.selection), () -> focusPane(pane.id),
            (side, spec) -> {
                // A split is a terminal tab of its own, in a new group beside the pane's group (spec 9.4).
                UUID window = pane.tab.window.id;
                UUID created = switch (spec) {
                    case OpenSpec.Local local -> {
                        opened.add("split|" + pane.id + "|" + side + "|" + local.directory().map(Path::toString).orElse("-")
                            + local.profile().map(profile -> "|" + profile).orElse("")
                            + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));
                        yield addPane(addTab(window, "split"), "split", local.directory().orElse(pane.directory));
                    }
                    case OpenSpec.Session session -> {
                        opened.add("session-split|" + pane.id + "|" + side + "|" + session.request().title());
                        yield addSessionPane(addTab(window, session.request().title()), session.request());
                    }
                };
                focusPane(created);
                return Optional.of(entry(panes.get(created)));
            });
    }

    private TabEntry entry(Tab tab) {
        return new TabEntry(tab.id, tab.window.id, tab.kind, () -> Optional.ofNullable(tab.pane).map(this::entry), tab.title, () -> select(tab));
    }

    UUID addWindow() {
        var window = new Window();
        windows.put(window.id, window);
        window.registration = registry.addWindow(new WindowEntry(window.id, () -> window.tabs.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(window.selected).map(this::entry), () -> registry.activeWindow().map(WindowEntry::id).equals(Optional.of(window.id)),
            () -> opened.add("front|" + window.id), spec -> switch (spec) {
                case OpenSpec.Local local -> {
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-")
                        + local.profile().map(profile -> "|" + profile).orElse("")
                        + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));
                    yield Optional.of(entry(panes.get(addPane(addTab(window.id, "opened"), "opened", local.directory().orElse(null)))));
                }
                case OpenSpec.Session session -> {
                    opened.add("session-tab|" + window.id + "|" + session.request().title());
                    yield Optional.of(entry(panes.get(addSessionPane(addTab(window.id, session.request().title()), session.request()))));
                }
            }));
        return window.id;
    }

    /** A terminal tab, selected; {@link #addPane} gives it its one pane. */
    UUID addTab(UUID windowId, String title) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.window = window; tab.title = () -> title;
        tabs.put(tab.id, tab);
        window.tabs.add(tab);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
        return tab.id;
    }

    private void select(Tab tab) {
        if (tab.window.selected == tab) return;
        tab.window.selected = tab;
        registry.publish(new TerminalEvent.TabSelected(tab.window.id, tab.id));
    }

    /** A terminal tab's one pane; a second, or a pane in a content tab, is refused. */
    UUID addPane(UUID tabId, String title, Path directory) {
        Tab tab = tabs.get(tabId);
        if (tab.kind != TabEntry.Kind.TERMINAL) throw new IllegalArgumentException("A content tab holds no pane");
        if (tab.pane != null) throw new IllegalStateException("A terminal tab holds one pane");
        var pane = new Pane(); pane.tab = tab; pane.title = title; pane.directory = directory;
        panes.put(pane.id, pane);
        tab.pane = pane;
        registry.publish(new TerminalEvent.PaneOpened(tab.id, pane.id));
        return pane.id;
    }

    /** A content tab, such as a plugin's view, with the id and title its window gives it; it opens selected. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.id = tabId; tab.window = window; tab.title = title; tab.kind = TabEntry.Kind.CONTENT;
        tabs.put(tab.id, tab);
        window.tabs.add(tab);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
    }

    /** Selects a tab in its window, as a click on it does. */
    void selectTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) select(tab);
    }

    /**
     * Moves a tab to another window, where it is selected: the move is reported once, its pane keeps its id with no
     * PaneOpened or PaneClosed, and a window left with no tab closes before the move is reported (workspace plan 3b
     * Rulings 5, 6).
     */
    void moveTab(UUID tabId, UUID windowId) {
        Tab tab = tabs.get(tabId);
        Window to = windows.get(windowId);
        if (tab == null || to == null || tab.window == to) return;
        Window from = tab.window;
        registry.atomically(() -> {
            from.tabs.remove(tab);
            if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
            if (from.tabs.isEmpty()) closeWindow(from);
            tab.window = to;
            to.tabs.add(tab);
            registry.publish(new TerminalEvent.TabMoved(tab.id, from.id, to.id));
            select(tab);
        });
    }

    /** Closes a tab of any kind, a terminal tab's pane with it. */
    void closeTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) close(tab);
    }

    /** Runs {@code closed} once the window closes, as the application drops a closed window's workspace. */
    void onWindowClosed(UUID windowId, Runnable closed) { windows.get(windowId).closed = closed; }

    void activateWindow(UUID windowId) { registry.windowActivated(windowId); }

    /** Focusing a pane selects its tab, as in the application. */
    void focusPane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null) return;
        registry.atomically(() -> { select(pane.tab); registry.publish(new TerminalEvent.PaneFocused(pane.tab.id, pane.id)); });
    }

    /** A pane closes with its tab, as in the application. */
    void closePane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane != null) close(pane.tab);
    }

    /** Closes a tab, its pane with it; the last tab takes its window with it. */
    private void close(Tab tab) {
        if (tabs.remove(tab.id) == null) return;
        Window window = tab.window;
        Pane pane = tab.pane;
        if (pane != null && panes.remove(pane.id) != null) {
            closedPanes.put(pane.id, pane);
            if (pane.attempt != null) pane.attempt.cancel();
            if (pane.connection != null) { pane.connection.close().run(); pane.connection = null; }
        }
        registry.atomically(() -> {
            if (pane != null) registry.publish(new TerminalEvent.PaneClosed(tab.id, pane.id));
            window.tabs.remove(tab);
            registry.publish(new TerminalEvent.TabClosed(window.id, tab.id));
            if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) select(window.tabs.get(0)); }
            if (window.tabs.isEmpty()) closeWindow(window);
        });
    }

    private void closeWindow(Window window) {
        windows.remove(window.id);
        window.registration.close();
        window.closed.run();
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
