package dev.jasper.sdk.testing;

import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The windows, tabs and panes a test scripts, with the application's rules: the active pane is the focused
 * pane of the selected tab of the window activated last, the last pane takes its tab with it and the last
 * tab its window, and every change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    static final class Tab {
        final UUID id = UUID.randomUUID(); final Window window; final String title; final List<Pane> panes = new ArrayList<>(); Pane focused; boolean open = true;
        Tab(Window window, String title) { this.window = window; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID(); final List<Tab> tabs = new ArrayList<>(); Tab selected; boolean open = true;
    }

    private final FakePluginHost host;
    final List<Window> windows = new ArrayList<>();
    /** Every pane ever added, so a test can still read what was sent to one that closed. */
    private final List<Pane> everyPane = new ArrayList<>();
    private final List<Tab> everyTab = new ArrayList<>();
    final List<String> openRequests = new ArrayList<>();
    Window lastActive;
    private UUID reportedActive;

    FakeWorkspace(FakePluginHost host) { this.host = host; }

    Optional<Window> window(UUID id) { return windows.stream().filter(window -> window.id.equals(id)).findFirst(); }
    Optional<Tab> tab(UUID id) { return everyTab.stream().filter(tab -> tab.open && tab.id.equals(id)).findFirst(); }
    Optional<Pane> pane(UUID id) { return everyPane.stream().filter(pane -> pane.open && pane.id.equals(id)).findFirst(); }
    Optional<Pane> anyPane(UUID id) { return everyPane.stream().filter(pane -> pane.id.equals(id)).findFirst(); }
    Optional<Pane> activePane() {
        return Optional.ofNullable(lastActive).filter(window -> window.open).map(window -> window.selected).map(tab -> tab.focused);
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

    Tab addTab(Window window, String title) {
        var tab = new Tab(window, title);
        everyTab.add(tab);
        window.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        select(tab);
        return tab;
    }

    void select(Tab tab) {
        if (!tab.open || tab.window.selected == tab) return;
        tab.window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(tab.window.id, tab.id));
        refreshActive();
    }

    Pane addPane(Tab tab, PaneInfo info) {
        var pane = new Pane(tab, info);
        everyPane.add(pane);
        tab.panes.add(pane);
        if (tab.focused == null) tab.focused = pane;
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
        pane.tab.focused = pane;
        host.publishApp(TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(pane.tab.id, pane.id));
        refreshActive();
    }

    void close(Pane pane) {
        if (!pane.open) return;
        pane.open = false;
        Tab tab = pane.tab; Window window = tab.window;
        tab.panes.remove(pane);
        if (tab.focused == pane) tab.focused = tab.panes.isEmpty() ? null : tab.panes.get(0);
        host.publishApp(TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        if (tab.panes.isEmpty()) {
            tab.open = false;
            window.tabs.remove(tab);
            host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
            if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) select(window.tabs.get(0)); }
            if (window.tabs.isEmpty()) {
                window.open = false;
                windows.remove(window);
                if (lastActive == window) lastActive = null;
                host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
            }
        }
        refreshActive();
    }
}
