package dev.jasper.app.workspace;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.SplitAxis;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import dev.jasper.app.terminals.OpenSpec;

/**
 * One window's presence in the terminal registry: entries that read this window's tabs and panes on demand,
 * and the facts the window publishes. Owned and closed by {@link WindowContent}. EDT only.
 */
final class WindowTerminals implements AutoCloseable {
    private final WindowContent owner;
    private final TerminalRegistry registry;
    private final Subscription registration;
    private UUID selected;
    private boolean closed;

    WindowTerminals(WindowContent owner, TerminalRegistry registry, Runnable toFront) {
        this.owner = owner; this.registry = registry;
        var entry = new WindowEntry(owner.id(), () -> owner.terminalTabs().stream().map(this::entry).toList(),
            () -> Optional.ofNullable(owner.currentTab()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);
        registration = registry.addWindow(entry);
        registry.atomically(() -> {
            for (TerminalTab tab : owner.terminalTabs()) {
                tabOpened(tab);
                for (TerminalPane pane : tab.panes()) publish(new TerminalEvent.PaneOpened(tab.id(), pane.id()));
            }
            tabSelected();
        });
    }

    TabEntry entry(TerminalTab tab) {
        return new TabEntry(tab.id(), owner.id(), () -> tab.panes().stream().map(pane -> entry(tab, pane)).toList(),
            () -> Optional.ofNullable(tab.focusedPane()).map(pane -> entry(tab, pane)), tab::title, () -> owner.selectTab(tab));
    }

    PaneEntry entry(TerminalTab tab, TerminalPane pane) {
        return new PaneEntry(pane.id(), tab.id(), pane::snapshot, pane::queryForegroundJob, pane::write, pane::paste, pane::selectedText,
            () -> { owner.selectTab(tab); tab.focus(pane); pane.focusTerminal(); },
            (axis, spec) -> {
                SplitTree.Axis direction = axis == SplitAxis.RIGHT ? SplitTree.Axis.RIGHT : SplitTree.Axis.DOWN;
                TerminalPane created = switch (spec) {
                    case OpenSpec.Local local -> tab.split(pane, direction, local.directory().orElse(null), null);
                    case OpenSpec.Session session -> tab.split(pane, direction, home(), session.request());
                };
                return Optional.ofNullable(created).map(split -> entry(tab, split));
            });
    }

    private static Path home() { return Path.of(System.getProperty("user.home")); }

    private Optional<PaneEntry> openTab(OpenSpec spec) {
        if (closed) return Optional.empty();
        TerminalTab tab = switch (spec) {
            case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory));
            case OpenSpec.Session session -> owner.openTab(home(), session.request());
        };
        return tab == null || tab.focusedPane() == null ? Optional.empty() : Optional.of(entry(tab, tab.focusedPane()));
    }

    void publish(TerminalEvent event) { if (!closed) registry.publish(event); }
    void atomically(Runnable change) { if (closed) change.run(); else registry.atomically(change); }
    void refresh() { if (!closed) registry.refresh(); }

    void tabOpened(TerminalTab tab) { publish(new TerminalEvent.TabOpened(owner.id(), tab.id())); }
    void tabClosed(TerminalTab tab) { publish(new TerminalEvent.TabClosed(owner.id(), tab.id())); }

    /** Reports the selected tab when it actually changed; Swing fires selection events for other reasons too. */
    void tabSelected() {
        TerminalTab current = owner.currentTab();
        UUID now = current == null ? null : current.id();
        if (Objects.equals(now, selected)) return;
        selected = now;
        if (now != null) publish(new TerminalEvent.TabSelected(owner.id(), now));
    }

    @Override public void close() {
        if (closed) return;
        registration.close();
        closed = true;
    }
}
