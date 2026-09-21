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

/** A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. EDT only. */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); }
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
            () -> new PaneSnapshot(pane.title, Optional.ofNullable(pane.directory), 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(), Optional.empty()),
            () -> CompletableFuture.completedFuture(Optional.of("vim")),
            bytes -> pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)), text -> pane.sent.add("paste:" + text),
            () -> Optional.ofNullable(pane.selection), () -> focusPane(pane.id),
            (axis, spec) -> {
                Optional<Path> directory = spec instanceof OpenSpec.Local local ? local.directory() : Optional.<Path>empty();
                opened.add("split|" + pane.id + "|" + axis + "|" + directory.map(Path::toString).orElse("-"));
                UUID created = addPane(pane.tab.id, "split", directory.orElse(pane.directory));
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
            () -> opened.add("front|" + window.id), spec -> {
                Optional<Path> directory = spec instanceof OpenSpec.Local local ? local.directory() : Optional.<Path>empty();
                opened.add("tab|" + window.id + "|" + directory.map(Path::toString).orElse("-"));
                UUID tab = addTab(window.id, "opened");
                return Optional.of(entry(panes.get(addPane(tab, "opened", directory.orElse(null)))));
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
            Optional.ofNullable(panes.get(paneId)).map(pane -> pane.directory)));
    }
}
