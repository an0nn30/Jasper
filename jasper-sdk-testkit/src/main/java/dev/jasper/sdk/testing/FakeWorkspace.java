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
 * The windows, tabs and panes a test scripts, with the application's rules (workspace tabs spec 6 and 15; tabs and panes
 * spec 4 and 13). The window opened while no main window is open is the main window, which stays open with an empty
 * workspace when its last tab leaves; any other window is a child window, which closes with its last tab. A window holds
 * tabs in strip order, and a tab holds panes, each showing one item, which the SDK calls a tab ({@code TabHandle}); a
 * window lists its items tab by tab, then pane by pane. Whatever opens without a pane, a moved item included, opens in a
 * new tab at the end of the strip; a split or a placement beside a pane opens a new pane right after it, in its tab.
 * Closing the selected item selects the item its tab showed before it, or, with the tab's last pane, the tab shown
 * before. A terminal item holds one terminal pane, the active pane is the terminal pane of the selected item of the
 * window activated last, focusing a pane selects its item, a pane closes with its item, an item moved to another window
 * is announced once as a move, and every change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        FakeSessions.Session session;
        /**
         * Set while the pane is held (restored and not shown yet): the snapshot it starts with. A held pane reports
         * NOT_STARTED with no grid, drops what is sent to it, and starts on its tab's next selection or focus.
         */
        PaneInfo held;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    /** One tab of a window's strip: its items, one per pane; {@code recent} holds them most recently selected first. */
    static final class Group {
        final List<Tab> recent = new ArrayList<>();
    }
    /** An item, the one a pane shows: what the SDK calls a tab. */
    static final class Tab {
        final UUID id = UUID.randomUUID(); final TabHandle.Kind kind; Window window; Group group; String title; Pane pane; boolean open = true;
        /** The built-in name pieces a test scripts, by name. */
        java.util.Map<String, String> namePieces = java.util.Map.of();
        Tab(Window window, TabHandle.Kind kind, String title) { this.window = window; this.kind = kind; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID();
        /** Whether this is the main window, which outlives its last tab. */
        final boolean main;
        /** Every item, tab by tab in strip order, then pane by pane. */
        final List<Tab> tabs = new ArrayList<>();
        /** The window's tabs, most recently shown first: the first is the shown tab. */
        final List<Group> recent = new ArrayList<>();
        Tab selected; boolean open = true;
        Window(boolean main) { this.main = main; }
        /** @return the shown tab, or null in an empty workspace */
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
    /** The saved tab a plugin's restorer is rebuilding, while it runs (workspace tabs spec 13). */
    Slot slot;

    /** A saved tab being rebuilt: the first tab its plugin opens takes it, in the window being restored. */
    static final class Slot {
        final Window window; final FakePluginContext owner; Tab taken;
        Slot(Window window, FakePluginContext owner) { this.window = window; this.owner = owner; }
    }

    /** @return the slot, while {@code context}'s plugin has not opened its tab yet; the caller sets what took it */
    Optional<Slot> takeSlot(FakePluginContext context) {
        return slot != null && slot.taken == null && slot.owner == context ? Optional.of(slot) : Optional.empty();
    }

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

    /** The window's tabs in strip order, each its items' ids pane by pane. */
    List<List<UUID>> tabsOf(Window window) {
        var tabs = new ArrayList<List<UUID>>();
        Group current = null;
        for (Tab tab : window.tabs) {
            if (tab.group != current) { tabs.add(new ArrayList<>()); current = tab.group; }
            tabs.getLast().add(tab.id);
        }
        return tabs.stream().map(List::copyOf).toList();
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

    /** A terminal item in a new tab, selected; {@link #addPane} gives it its one terminal pane. */
    Tab addTab(Window window, String title) { return addTab(window, TabHandle.Kind.TERMINAL, title, null); }

    /** An item of {@code kind} in a new tab at the end of the strip, selected. */
    Tab addTab(Window window, TabHandle.Kind kind, String title) { return addTab(window, kind, title, null); }

    /** An item of {@code kind}, selected, in a new pane right after {@code beside}; in a new tab when it is null. */
    Tab addTab(Window window, TabHandle.Kind kind, String title, Tab beside) {
        var tab = announceTab(window, kind, title, beside);
        select(tab);
        return tab;
    }

    /**
     * A terminal item with its one pane in a new tab, as the application opens one for {@code openTab}: announced, given
     * its pane, then selected, so the active pane goes straight to the new pane.
     */
    Pane openTerminal(Window window, String title, PaneInfo info) { return openTerminal(window, title, info, null); }

    /** As {@link #openTerminal(Window, String, PaneInfo)}, a split's item in a new pane right after {@code beside}. */
    Pane openTerminal(Window window, String title, PaneInfo info, Tab beside) {
        var pane = addPane(announceTab(window, TabHandle.Kind.TERMINAL, title, beside), info);
        select(pane.tab);
        return pane;
    }

    /** An item of {@code kind} in a new tab at the end of {@code window}'s strip, announced and not selected: a restored one. */
    Tab placeTab(Window window, TabHandle.Kind kind, String title) { return announceTab(window, kind, title, null); }

    /** A terminal item with its one pane, placed as {@link #placeTab} places an item: a restored session's. */
    Pane placeTerminal(Window window, String title, PaneInfo info) {
        return addPane(announceTab(window, TabHandle.Kind.TERMINAL, title, null), info);
    }

    private Tab announceTab(Window window, TabHandle.Kind kind, String title, Tab beside) {
        var tab = new Tab(window, kind, title);
        everyTab.add(tab);
        place(tab, window, beside);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        return tab;
    }

    /**
     * Puts {@code tab} in a new pane right after {@code beside}, in its tab, or in a new tab at the end of the strip when
     * {@code beside} is null or not an open item of {@code window}.
     */
    private void place(Tab tab, Window window, Tab beside) {
        Group group;
        int index;
        if (beside != null && beside.open && beside.window == window) {
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

    void select(Tab tab) {
        if (!tab.open) return;
        Window window = tab.window;
        tab.group.recent.remove(tab);
        tab.group.recent.addFirst(tab);
        window.recent.remove(tab.group);
        window.recent.addFirst(tab.group);
        if (window.selected != tab) {
            window.selected = tab;
            host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window.id, tab.id));
            refreshActive();
            // As the application's window does on every selection: each content tab is active while it is its window's selected tab.
            for (var content : host.contentTabs())
                if (content instanceof FakeContent shown && !shown.closed && shown.tab.window == window) shown.active(shown.tab == tab);
        }
        // A held pane starts once its tab is shown, after the selection, as Jasper starts a restored terminal tab at the
        // end of the turn that shows it (workspace tabs spec 13).
        if (tab.pane != null && tab.pane.held != null) start(tab.pane);
    }

    /** A held pane starts: a provided session connects; a local shell runs, with the grid it had before it was held. */
    private void start(Pane pane) {
        if (pane.session != null) {
            pane.info = pane.held;
            pane.held = null;
            sessions.connect(pane.session.owner, pane, pane.session.spec);
            return;
        }
        pane.info = FakeTerminals.withState(pane.held, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty());
        pane.held = null;
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED,
            new TerminalEvents.SessionStateChanged(pane.id, dev.jasper.sdk.terminal.SessionState.RUNNING, java.util.OptionalInt.empty()));
    }

    /** A terminal item's one pane; a second, or one in a content item, is refused. */
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
     * Moves an item to a new tab at the end of another open window's strip, where it is selected; a child window left
     * with no item closes after the move is announced.
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

    /**
     * Move Pane to New Tab (tabs and panes spec 9): {@code tab} leaves its tab, which keeps its other panes, for a new tab
     * right after it, where it is selected. A tab's only pane stays.
     */
    void moveToNewTab(Tab tab) {
        Window window = tab.window;
        Group from = tab.group;
        if (!tab.open || from.recent.size() < 2) return;
        int end = 0;
        for (int i = 0; i < window.tabs.size(); i++) if (window.tabs.get(i).group == from) end = i;
        window.tabs.remove(tab);
        from.recent.remove(tab);
        var group = new Group();
        window.recent.add(group);
        tab.group = group;
        group.recent.add(tab);
        // The tab's old run of items ends one place earlier now: the new tab's item goes right after it.
        window.tabs.add(end, tab);
        select(tab);
    }

    /** The window's tabs in strip order. */
    List<Group> strip(Window window) {
        var groups = new ArrayList<Group>();
        for (Tab tab : window.tabs) if (groups.isEmpty() || groups.getLast() != tab.group) groups.add(tab.group);
        return groups;
    }

    /**
     * Move Pane to Tab (pane flexibility spec 4): {@code tab} leaves its tab for a new pane right after the shown item of
     * the tab numbered {@code number} in its window's strip, where it is selected; its old tab goes with its last pane.
     * Its own tab's number and one no tab has move nothing.
     */
    void moveToTab(Tab tab, int number) {
        Window window = tab.window;
        List<Group> strip = strip(window);
        if (!tab.open || number < 1 || number > strip.size() || strip.get(number - 1) == tab.group) return;
        Group to = strip.get(number - 1), from = tab.group;
        Tab beside = to.recent.getFirst();
        window.tabs.remove(tab);
        from.recent.remove(tab);
        if (from.recent.isEmpty()) window.recent.remove(from);
        tab.group = to;
        to.recent.add(tab);
        window.tabs.add(window.tabs.indexOf(beside) + 1, tab);
        select(tab);
    }

    /** A pane closes with its item. */
    void close(Pane pane) { if (pane.open) close(pane.tab); }

    /** Closes an item, its terminal pane with it; a child window's last item takes the window with it. */
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
        host.namePieceValues.remove(tab.id);
        boolean selected = takeOut(tab);
        host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
        if (selected) reselect(window);
        if (window.tabs.isEmpty() && !window.main) closeWindow(window);
        refreshActive();
    }

    /** Takes {@code tab} out of its window's tabs, its tab going with its last pane; true when it was the selected item. */
    private boolean takeOut(Tab tab) {
        Window window = tab.window;
        window.tabs.remove(tab);
        tab.group.recent.remove(tab);
        if (tab.group.recent.isEmpty()) window.recent.remove(tab.group);
        if (window.selected != tab) return false;
        window.selected = null;
        return true;
    }

    /** Its selected item left: the item its tab showed before it, else the tab shown before, takes over. */
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
