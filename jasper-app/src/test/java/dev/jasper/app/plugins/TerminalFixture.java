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
 * pane, a split opens a terminal tab of its own in a new pane right after the pane's, in its tab, focusing a pane
 * selects its tab, and a pane closes with its tab. A window holds tabs, each holding panes, as the testkit's fake keeps
 * them (tabs and panes spec 4; the registry calls each pane's item a tab): its items are listed tab by tab, then pane
 * by pane, whatever opens without a pane opens in a new tab at the end of the strip, closing the selected item selects
 * the item its tab showed before it, and closing a tab's last pane shows the tab shown before it. A main window
 * ({@link #addMainWindow}) stays open when its last tab leaves; any other window closes with its last tab (spec 6.4). A
 * plugin's tab is a content tab of its window, and a tab moved to another window is reported as a move. EDT only.
 */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); dev.jasper.app.terminals.RemoteLocation remote;
        SessionRequest request; SessionAttempt attempt; AttachedConnection connection; boolean everAttached; String status = ""; String state = ""; String shell = "zsh"; }
    /** One tab of a window's strip: its items, one per pane; {@code recent} holds them most recently selected first. */
    private static final class Group { final List<Tab> recent = new ArrayList<>(); }
    private static final class Tab { UUID id = UUID.randomUUID(); Window window; Group group; TabEntry.Kind kind = TabEntry.Kind.TERMINAL; Supplier<String> title; Pane pane; }
    private static final class Window {
        UUID id = UUID.randomUUID(); boolean main; List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; Runnable closed = () -> { };
        /** The window's tabs, most recently shown first: the first is the shown tab. */
        final List<Group> recent = new ArrayList<>();
        Group active() { return recent.isEmpty() ? null : recent.getFirst(); }
    }

    final TerminalRegistry registry = new TerminalRegistry();
    private final Map<UUID, Window> windows = new LinkedHashMap<>();
    private final Map<UUID, Tab> tabs = new LinkedHashMap<>();
    private final Map<UUID, Pane> panes = new LinkedHashMap<>();
    /** Kept so a test can still read what was sent to a pane that closed, as the testkit's fake allows. */
    private final Map<UUID, Pane> closedPanes = new LinkedHashMap<>();
    private final List<String> opened = new ArrayList<>();

    private PaneEntry entry(Pane pane) {
        return new PaneEntry(pane.id, pane.tab.id,
            // A pane that has not started has no grid and no shell integration yet, as the application's reports.
            () -> new PaneSnapshot(pane.title, pane.remote == null ? Optional.ofNullable(pane.directory) : Optional.empty(),
                held(pane) ? 0 : 80, held(pane) ? 0 : 24, !held(pane),
                held(pane) ? PaneSnapshot.State.NOT_STARTED : pane.state.equals("CONNECTING") ? PaneSnapshot.State.STARTING
                    : pane.state.equals("EXITED") ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING,
                OptionalInt.empty(), Optional.ofNullable(pane.request).map(SessionRequest::providerId), Optional.ofNullable(pane.remote), pane.shell),
            () -> CompletableFuture.completedFuture(Optional.of("vim")),
            // A pane that has not started drops what is sent to it (workspace tabs spec 13).
            bytes -> { if (!held(pane)) pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)); },
            text -> { if (!held(pane)) pane.sent.add("paste:" + text); },
            () -> Optional.ofNullable(pane.selection), () -> focusPane(pane.id),
            (side, spec) -> {
                // A split is a terminal tab of its own, in a new pane right after the pane's, in its tab (tabs and panes spec 4).
                UUID window = pane.tab.window.id;
                UUID created = switch (spec) {
                    case OpenSpec.Local local -> {
                        opened.add("split|" + pane.id + "|" + side + "|" + local.directory().map(Path::toString).orElse("-")
                            + local.profile().map(profile -> "|" + profile).orElse("")
                            + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));
                        yield openTerminal(window, "split", local.directory().orElse(pane.directory), pane.tab);
                    }
                    case OpenSpec.Session session -> {
                        opened.add("session-split|" + pane.id + "|" + side + "|" + session.request().title());
                        yield openSession(window, session.request(), pane.tab);
                    }
                };
                focusPane(created);
                return Optional.of(entry(panes.get(created)));
            });
    }

    private TabEntry entry(Tab tab) {
        return new TabEntry(tab.id, tab.window.id, tab.kind, () -> Optional.ofNullable(tab.pane).map(this::entry), tab.title, () -> select(tab));
    }

    /** A child window, which closes with its last tab. */
    UUID addWindow() { return addWindow(false); }

    /** The main window, which stays open with an empty workspace when its last tab leaves. */
    UUID addMainWindow() { return addWindow(true); }

    private UUID addWindow(boolean main) {
        var window = new Window();
        window.main = main;
        windows.put(window.id, window);
        window.registration = registry.addWindow(new WindowEntry(window.id, () -> window.tabs.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(window.selected).map(this::entry), () -> registry.activeWindow().map(WindowEntry::id).equals(Optional.of(window.id)),
            () -> window.main, () -> opened.add("front|" + window.id), spec -> switch (spec) {
                case OpenSpec.Local local -> {
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-")
                        + local.profile().map(profile -> "|" + profile).orElse("")
                        + local.program().map(program -> "|run:" + String.join(" ", program)).orElse(""));
                    yield Optional.of(entry(panes.get(openTerminal(window.id, "opened", local.directory().orElse(null), null))));
                }
                case OpenSpec.Session session -> {
                    opened.add("session-tab|" + window.id + "|" + session.request().title());
                    yield Optional.of(entry(panes.get(openSession(window.id, session.request(), null))));
                }
            }));
        return window.id;
    }

    /** A terminal tab in a new tab at the end of the strip, selected; {@link #addPane} gives it its one pane. */
    UUID addTab(UUID windowId, String title) { return addTab(windowId, title, null); }

    /** A terminal tab, selected, in a new pane right after {@code beside}; in a new tab when it is null. */
    private UUID addTab(UUID windowId, String title, Tab beside) {
        Tab tab = newTab(windows.get(windowId), title, beside);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(tab.window.id, tab.id)); select(tab); });
        return tab.id;
    }

    /**
     * A terminal tab with its one pane, as a window opens one for {@code openTab} or a split (a split's in a new pane right
     * after {@code beside}, in its tab): announced, given its pane, then selected, so the active pane goes straight to the
     * new pane. Returns the pane's id.
     */
    private UUID openTerminal(UUID windowId, String title, Path directory, Tab beside) {
        Tab tab = newTab(windows.get(windowId), title, beside);
        registry.publish(new TerminalEvent.TabOpened(tab.window.id, tab.id));
        UUID pane = addPane(tab.id, title, directory);
        select(tab);
        return pane;
    }

    private Tab newTab(Window window, String title, Tab beside) {
        var tab = new Tab(); tab.title = () -> title;
        tabs.put(tab.id, tab);
        place(tab, window, beside);
        return tab;
    }

    /**
     * Puts {@code tab} in a new pane right after {@code beside}, in its tab, or in a new tab at the end of {@code window}'s
     * strip when {@code beside} is null or not one of its items.
     */
    private void place(Tab tab, Window window, Tab beside) {
        Group group;
        int index;
        if (beside != null && beside.window == window && window.tabs.contains(beside)) {
            group = beside.group;
            index = window.tabs.indexOf(beside) + 1;
        } else {
            group = new Group();
            window.recent.add(group);
            index = window.tabs.size();
        }
        tab.window = window;
        tab.group = group;
        window.tabs.add(index, tab);
        group.recent.add(tab);
    }

    private void select(Tab tab) {
        Window window = tab.window;
        tab.group.recent.remove(tab);
        tab.group.recent.addFirst(tab);
        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        if (window.selected != tab) {
            window.selected = tab;
            registry.publish(new TerminalEvent.TabSelected(window.id, tab.id));
        }
        // A held pane starts once its tab is shown, after the selection, as the application starts a restored terminal
        // tab at the end of the turn that shows it (workspace tabs spec 13).
        if (tab.pane != null && held(tab.pane)) {
            tab.pane.state = "";
            registry.publish(new TerminalEvent.SessionStarted(tab.pane.id));
        }
    }

    private static boolean held(Pane pane) { return pane.state.equals("NOT_STARTED"); }

    /** Takes {@code tab} out of its window's tabs, its tab going with its last pane; true when it was the selected tab. */
    private boolean takeOut(Tab tab) {
        Window window = tab.window;
        window.tabs.remove(tab);
        tab.group.recent.remove(tab);
        if (tab.group.recent.isEmpty()) window.recent.remove(tab.group);
        if (window.selected != tab) return false;
        window.selected = null;
        return true;
    }

    /** Its selected tab left: the item its tab showed before it, else the tab shown before, takes over. */
    private void reselect(Window window) {
        if (!window.recent.isEmpty()) select(window.active().recent.getFirst());
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

    /** A content tab, such as a plugin's view, with the id and title its window gives it, in a new tab at the end of the strip; it opens selected. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title) { addContentTab(windowId, tabId, title, false); }

    /** As above; with {@code beside}, in a new pane right after the window's selected item, as a split placement opens it. */
    void addContentTab(UUID windowId, UUID tabId, Supplier<String> title, boolean beside) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.id = tabId; tab.title = title; tab.kind = TabEntry.Kind.CONTENT;
        tabs.put(tab.id, tab);
        place(tab, window, beside ? window.selected : null);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); select(tab); });
    }

    /** Selects a tab in its window, as a click on it does. */
    void selectTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) select(tab);
    }

    /**
     * Moves a tab to a new tab at the end of another window's strip, where it is selected: the move is reported once, its pane
     * keeps its id with no PaneOpened or PaneClosed, and a child window left with no tab closes after the move is
     * reported (workspace plan 3b Rulings 5, 6; plan 4a Rulings 23).
     */
    void moveTab(UUID tabId, UUID windowId) {
        Tab tab = tabs.get(tabId);
        Window to = windows.get(windowId);
        if (tab == null || to == null || tab.window == to) return;
        Window from = tab.window;
        // Not atomically: as the application's window does, the active pane is reported as soon as the source lets the tab
        // go, before the move (workspace plan 4a's real-window probe).
        if (takeOut(tab)) reselect(from);
        registry.refresh();
        place(tab, to, null);
        registry.publish(new TerminalEvent.TabMoved(tab.id, from.id, to.id));
        select(tab);
        if (from.tabs.isEmpty() && !from.main) closeWindow(from);
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

    /** Closes a tab, its pane with it; a child window's last tab takes the window with it. */
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
            boolean selected = takeOut(tab);
            registry.publish(new TerminalEvent.TabClosed(window.id, tab.id));
            if (selected) reselect(window);
            if (window.tabs.isEmpty() && !window.main) closeWindow(window);
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

    /**
     * A terminal in a new pane right after the item {@code tabId}, in its tab, focused, as Split with a choice's Terminal
     * Right and Terminal Down open one beside a plugin's item (tabs and panes spec 8): either side lists it right after,
     * and {@link #opened()} records {@code beside|<tab id>|<side>}.
     *
     * @return whether it opened, as a window answers: false for an item the fixture does not hold
     */
    boolean splitBeside(UUID tabId, dev.jasper.app.layout.Side side) {
        Tab beside = tabs.get(tabId);
        if (beside == null) return false;
        opened.add("beside|" + tabId + "|" + side);
        focusPane(openTerminal(beside.window.id, "split", null, beside));
        return true;
    }

    /** Whether Move Pane to New Tab would move the item {@code tabId}: its tab holds another pane (tabs and panes spec 9). */
    boolean mayMoveToNewTab(UUID tabId) {
        Tab tab = tabs.get(tabId);
        return tab != null && tab.group.recent.size() > 1;
    }

    /** Move Pane to New Tab: the item {@code tabId} leaves its tab for a new tab right after it, where it is selected. */
    void moveToNewTab(UUID tabId) {
        if (!mayMoveToNewTab(tabId)) return;
        Tab tab = tabs.get(tabId);
        Window window = tab.window;
        Group from = tab.group;
        int end = 0;
        for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == from) end = i;
        window.tabs.remove(tab);
        from.recent.remove(tab);
        var group = new Group();
        window.recent.add(group);
        tab.group = group;
        group.recent.add(tab);
        window.tabs.add(end, tab);
        select(tab);
    }

    /** A window's tabs in strip order, each its items' ids pane by pane (tabs and panes spec 4); empty for a closed window. */
    List<List<UUID>> windowTabs(UUID windowId) {
        Window window = windows.get(windowId);
        if (window == null) return List.of();
        var result = new ArrayList<List<UUID>>();
        Group current = null;
        for (Tab tab : window.tabs) {
            if (tab.group != current) { result.add(new ArrayList<>()); current = tab.group; }
            result.getLast().add(tab.id);
        }
        return result.stream().map(List::copyOf).toList();
    }

    void finishCommand(UUID paneId, String command, int exitStatus) {
        registry.publish(new TerminalEvent.CommandFinished(paneId, command, OptionalInt.of(exitStatus), Duration.ofMillis(1500),
            Optional.ofNullable(panes.get(paneId)).filter(pane -> pane.remote == null).map(pane -> pane.directory),
            Optional.ofNullable(panes.get(paneId)).map(pane -> pane.remote)));
    }

    /** A provided session's terminal tab, opened as {@link #openTerminal} opens one, then connected. */
    private UUID openSession(UUID windowId, SessionRequest request, Tab beside) {
        UUID id = openTerminal(windowId, request.title(), null, beside);
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

    /**
     * The local pane waits, as a restored terminal tab not yet shown does (workspace tabs spec 13): it starts on its tab's
     * next selection or focus. A pane that shows a provided session is refused, as the testkit's fake refuses it.
     */
    void holdPane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane.request != null)
            throw new IllegalArgumentException("Only a local pane can be held: pane " + paneId + " shows a provided session");
        pane.state = "NOT_STARTED";
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
