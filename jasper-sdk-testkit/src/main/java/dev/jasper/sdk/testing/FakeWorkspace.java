package dev.jasper.sdk.testing;

import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The windows, tabs and panes a test scripts, with the application's rules (workspace tabs spec 6, 9.4 and 15). The
 * window opened while no main window is open is the main window, which stays open with an empty workspace when its last
 * tab leaves; any other window is a child window, which closes with its last tab. A window's tabs sit in groups and are
 * listed group by group: a new tab opens at the end of the active group, a split or a placement beside it opens a new
 * group after it, closing a group's selected tab selects the tab the group showed before it, and closing a group's last
 * tab makes the group used before it active. A terminal tab holds one pane, a split opens a terminal tab of its own, the
 * active pane is the pane of the active tab of the window activated last when that tab is a terminal tab, focusing a pane
 * selects its tab, a pane closes with its tab, a tab moved to another window is announced once as a move, and every
 * change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        /** Restored and not shown yet: it reports NOT_STARTED, drops what is sent to it, and starts when its tab is shown. */
        boolean held;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    /** One group of a window's tabs; {@code recent} holds them most recently selected first. */
    static final class Group {
        final List<Tab> recent = new ArrayList<>();
    }
    static final class Tab {
        final UUID id = UUID.randomUUID(); final TabHandle.Kind kind; Window window; Group group; String title; Pane pane; boolean open = true;
        Tab(Window window, TabHandle.Kind kind, String title) { this.window = window; this.kind = kind; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID();
        /** Whether this is the main window, which outlives its last tab. */
        final boolean main;
        /** Every tab, group by group. */
        final List<Tab> tabs = new ArrayList<>();
        /** The groups that hold tabs, most recently active first: the first is the active group. */
        final List<Group> recent = new ArrayList<>();
        Tab selected; boolean open = true;
        Window(boolean main) { this.main = main; }
        /** @return the active group, or null in an empty workspace */
        Group active() { return recent.isEmpty() ? null : recent.getFirst(); }
    }

    private final FakePluginHost host;
    final List<Window> windows = new ArrayList<>();
    /** Every pane ever added, so a test can still read what was sent to one that closed. */
    private final List<Pane> everyPane = new ArrayList<>();
    private final List<Tab> everyTab = new ArrayList<>();
    final List<String> openRequests = new ArrayList<>();
    /** The shell profiles the test says the application detected. */
    List<ShellProfileInfo> profiles = List.of();
    /** Every live {@code onProfilesChanged} listener, of every plugin. */
    final List<Runnable> profileListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    Window lastActive;
    private UUID reportedActive;

    final FakeSessions sessions;

    FakeWorkspace(FakePluginHost host) { this.host = host; this.sessions = new FakeSessions(host, this); }

    List<Pane> everyOpenPane() { return everyPane.stream().filter(pane -> pane.open).toList(); }

    Optional<Window> window(UUID id) { return windows.stream().filter(window -> window.id.equals(id)).findFirst(); }
    /** @return the main window, while it is open */
    Optional<Window> main() { return windows.stream().filter(window -> window.main).findFirst(); }
    Optional<Tab> tab(UUID id) { return everyTab.stream().filter(tab -> tab.open && tab.id.equals(id)).findFirst(); }
    Optional<Pane> pane(UUID id) { return everyPane.stream().filter(pane -> pane.open && pane.id.equals(id)).findFirst(); }
    Optional<Pane> anyPane(UUID id) { return everyPane.stream().filter(pane -> pane.id.equals(id)).findFirst(); }
    Optional<Pane> activePane() {
        return Optional.ofNullable(lastActive).filter(window -> window.open).map(window -> window.selected).map(tab -> tab.pane);
    }

    private void refreshActive() {
        UUID now = activePane().map(pane -> pane.id).orElse(null);
        if (Objects.equals(now, reportedActive)) return;
        reportedActive = now;
        host.publishApp(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.ofNullable(now)));
    }

    /** A window: the main window when none is open, else a child window. */
    Window addWindow() {
        var window = new Window(main().isEmpty());
        windows.add(window);
        host.publishApp(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(window.id));
        return window;
    }

    /** A terminal tab, selected; {@link #addPane} gives it its one pane. */
    Tab addTab(Window window, String title) { return addTab(window, TabHandle.Kind.TERMINAL, title, null); }

    /** A tab of {@code kind}, selected, at the end of the active group. */
    Tab addTab(Window window, TabHandle.Kind kind, String title) { return addTab(window, kind, title, null); }

    /** A tab of {@code kind}, selected, in a new group after {@code beside}; at the end of the active group when it is null. */
    Tab addTab(Window window, TabHandle.Kind kind, String title, Group beside) {
        var tab = announceTab(window, kind, title, beside);
        select(tab);
        return tab;
    }

    /**
     * A terminal tab with its one pane, as the application opens one for {@code openTab}: announced, given its pane, then
     * selected, so the active pane goes straight to the new pane.
     */
    Pane openTerminal(Window window, String title, PaneInfo info) { return openTerminal(window, title, info, null); }

    /** As {@link #openTerminal(Window, String, PaneInfo)}, a split's tab in a new group after {@code beside}. */
    Pane openTerminal(Window window, String title, PaneInfo info, Group beside) {
        var pane = addPane(announceTab(window, TabHandle.Kind.TERMINAL, title, beside), info);
        select(pane.tab);
        return pane;
    }

    private Tab announceTab(Window window, TabHandle.Kind kind, String title, Group beside) {
        var tab = new Tab(window, kind, title);
        everyTab.add(tab);
        place(tab, window, beside);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        return tab;
    }

    /** Puts {@code tab} at the end of {@code window}'s active group, or in a new group after {@code beside}. */
    private void place(Tab tab, Window window, Group beside) {
        Group group = beside == null ? window.active() : null;
        Group after = beside != null ? beside : group;
        int index = window.tabs.size();
        if (after != null) for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == after) index = i + 1;
        if (group == null) { group = new Group(); window.recent.add(group); }
        tab.window = window;
        tab.group = group;
        window.tabs.add(index, tab);
        group.recent.add(tab);
    }

    void select(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        tab.group.recent.remove(tab);
        tab.group.recent.addFirst(tab);
        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        // A held pane starts when its tab is shown, as a restored terminal tab does (workspace tabs spec 13).
        if (tab.pane != null && tab.pane.held) start(tab.pane);
        if (window.selected == tab) return;
        window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window.id, tab.id));
        refreshActive();
        // As the application's window does on every selection: each content tab is active while it is its window's selected tab.
        for (var content : host.contentTabs())
            if (content instanceof FakeContent shown && !shown.closed && shown.tab.window == window) shown.active(shown.tab == tab);
    }

    /** A held pane starts: a provided session connects, a local shell runs. */
    private void start(Pane pane) {
        pane.held = false;
        if (pane.session != null) { sessions.connect(pane.session.owner, pane, pane.session.spec); return; }
        pane.info = FakeTerminals.withState(pane.info, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty());
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED,
            new TerminalEvents.SessionStateChanged(pane.id, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty()));
    }

    /** A terminal tab's one pane; a second, or one in a content tab, is refused. */
    Pane addPane(Tab tab, PaneInfo info) {
        if (tab.kind != TabHandle.Kind.TERMINAL) throw new IllegalArgumentException("A content tab holds no pane");
        if (tab.pane != null) throw new IllegalStateException("A terminal tab holds one pane");
        var pane = new Pane(tab, info);
        everyPane.add(pane);
        tab.pane = pane;
        host.publishApp(TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        refreshActive();
        return pane;
    }

    void activate(Window window) {
        if (!window.open) return;
        lastActive = window;
        host.publishApp(TerminalEvents.WINDOW_ACTIVATED, new TerminalEvents.WindowEvent(window.id));
        refreshActive();
    }

    void focus(Pane pane) {
        if (!pane.open) return;
        select(pane.tab);
        host.publishApp(TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(pane.tab.id, pane.id));
        refreshActive();
    }

    /**
     * Moves a tab to the end of another open window's active group, where it is selected; a child window left with no
     * tab closes after the move is announced.
     */
    void move(Tab tab, Window to) {
        if (!tab.open || !to.open || tab.window == to) return;
        Window from = tab.window;
        if (takeOut(tab)) reselect(from);
        // As the application's window does: the active pane is reported as soon as the source lets the tab go.
        refreshActive();
        place(tab, to, null);
        host.publishApp(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(tab.id, from.id, to.id));
        select(tab);
        if (from.tabs.isEmpty() && !from.main) closeWindow(from);
        refreshActive();
    }

    /** A pane closes with its tab. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }

    /** Closes a tab, its pane with it; a child window's last tab takes the window with it. */
    void close(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        Pane pane = tab.pane;
        if (pane != null && pane.open) {
            sessions.closing(pane);
            pane.open = false;
            host.publishApp(TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        }
        tab.open = false;
        boolean selected = takeOut(tab);
        host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
        if (selected) reselect(window);
        if (window.tabs.isEmpty() && !window.main) closeWindow(window);
        refreshActive();
    }

    /** Takes {@code tab} out of its window's groups; true when it was the window's selected tab. */
    private boolean takeOut(Tab tab) {
        Window window = tab.window;
        window.tabs.remove(tab);
        tab.group.recent.remove(tab);
        if (tab.group.recent.isEmpty()) window.recent.remove(tab.group);
        if (window.selected != tab) return false;
        window.selected = null;
        return true;
    }

    /** Its selected tab left: the tab the active group showed before it, else the group used before, takes over. */
    private void reselect(Window window) {
        if (!window.recent.isEmpty()) select(window.active().recent.getFirst());
    }

    private void closeWindow(Window window) {
        window.open = false;
        windows.remove(window);
        if (lastActive == window) lastActive = null;
        host.closeToolbarViews(window.id);
        host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
        refreshActive();
    }
}
