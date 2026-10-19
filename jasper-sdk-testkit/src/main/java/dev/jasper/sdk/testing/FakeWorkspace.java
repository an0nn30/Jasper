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
 * The windows, tabs and panes a test scripts, with the application's rules (workspace tabs spec 9.4): a terminal tab
 * holds one pane, a split opens a terminal tab of its own, the active pane is the pane of the active tab of the window
 * activated last when that tab is a terminal tab, focusing a pane selects its tab, a pane closes with its tab and the last
 * tab takes its window with it, a tab moved to another window is announced once as a move, and every change is
 * announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    static final class Tab {
        final UUID id = UUID.randomUUID(); final TabHandle.Kind kind; Window window; String title; Pane pane; boolean open = true;
        Tab(Window window, TabHandle.Kind kind, String title) { this.window = window; this.kind = kind; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID(); final List<Tab> tabs = new ArrayList<>(); Tab selected; boolean open = true; boolean detached;
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

    Window addWindow() {
        var window = new Window();
        windows.add(window);
        host.publishApp(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(window.id));
        return window;
    }

    /** A terminal tab, selected; {@link #addPane} gives it its one pane. */
    Tab addTab(Window window, String title) { return addTab(window, TabHandle.Kind.TERMINAL, title); }

    /** A tab of {@code kind}, selected. */
    Tab addTab(Window window, TabHandle.Kind kind, String title) {
        var tab = announceTab(window, kind, title);
        select(tab);
        return tab;
    }

    /**
     * A terminal tab with its one pane, as the application opens one for {@code openTab} or a split: announced, given its
     * pane, then selected, so the active pane goes straight to the new pane.
     */
    Pane openTerminal(Window window, String title, PaneInfo info) {
        var pane = addPane(announceTab(window, TabHandle.Kind.TERMINAL, title), info);
        select(pane.tab);
        return pane;
    }

    private Tab announceTab(Window window, TabHandle.Kind kind, String title) {
        var tab = new Tab(window, kind, title);
        everyTab.add(tab);
        window.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        return tab;
    }

    void select(Tab tab) {
        if (!tab.open || tab.window.selected == tab) return;
        tab.window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(tab.window.id, tab.id));
        refreshActive();
        // As the application's window does on every selection: each content tab is active while it is its window's selected tab.
        for (var content : host.contentTabs())
            if (content instanceof FakeContent shown && !shown.closed && shown.tab.window == tab.window) shown.active(shown.tab == tab);
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

    /** Moves a tab to another open window, where it is selected; a window left with no tab closes after the move is announced. */
    void move(Tab tab, Window to) {
        if (!tab.open || !to.open || tab.window == to) return;
        Window from = tab.window;
        from.tabs.remove(tab);
        if (from.selected == tab) { from.selected = null; if (!from.tabs.isEmpty()) select(from.tabs.get(0)); }
        // As the application's window does: the active pane is reported as soon as the source lets the tab go.
        refreshActive();
        tab.window = to;
        to.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(tab.id, from.id, to.id));
        select(tab);
        if (from.tabs.isEmpty()) closeWindow(from);
        refreshActive();
    }

    /** A pane closes with its tab. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }

    /** Closes a tab, its pane with it; the last tab takes its window with it. */
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
        window.tabs.remove(tab);
        host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
        if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) select(window.tabs.get(0)); }
        if (window.tabs.isEmpty()) closeWindow(window);
        refreshActive();
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
