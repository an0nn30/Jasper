package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One window's presence in the terminal registry (workspace tabs spec 9.4): entries that read every tab of this window,
 * terminal and content, in workspace order, and its terminal tabs' panes, on demand, and the facts the window publishes.
 * A tab that moved here from another window is reported once, as a move (workspace plan 3b Rulings 5). A pane's split
 * opens a terminal tab in a new group beside the pane's group. Owned and closed by {@link WindowContent}. EDT only.
 */
final class WindowTerminals implements AutoCloseable {
    private final WindowContent owner;
    private final TerminalRegistry registry;
    private final Subscription registration;
    private UUID selected;
    private boolean closed;

    WindowTerminals(WindowContent owner, TerminalRegistry registry, Runnable toFront) {
        this.owner = owner; this.registry = registry;
        var entry = new WindowEntry(owner.id(), () -> owner.tabs().stream().map(this::entry).toList(),
            () -> Optional.ofNullable(owner.selectedTab()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);
        registration = registry.addWindow(entry);
        registry.atomically(() -> {
            for (Tab tab : owner.tabs()) {
                publish(new TerminalEvent.TabOpened(owner.id(), tab.id().value()));
                if (tab.content() instanceof TerminalContent terminal)
                    publish(new TerminalEvent.PaneOpened(tab.id().value(), terminal.pane().id()));
            }
            tabSelected();
        });
    }

    /** A tab of this window: a terminal tab, or content (a plugin's tab, and later a placeholder; Rulings 13, 14). */
    TabEntry entry(Tab tab) {
        if (tab.content() instanceof TerminalContent terminal)
            return new TabEntry(tab.id().value(), owner.id(), TabEntry.Kind.TERMINAL,
                () -> terminal.closed() ? Optional.empty() : Optional.of(entry(terminal, terminal.pane())),
                terminal::title, () -> owner.selectTab(tab));
        return new TabEntry(tab.id().value(), owner.id(), TabEntry.Kind.CONTENT, Optional::empty, tab::title, () -> owner.selectTab(tab));
    }

    PaneEntry entry(TerminalContent terminal, TerminalPane pane) {
        return new PaneEntry(pane.id(), terminal.id().value(), pane::snapshot, pane::queryForegroundJob, pane::write, pane::paste,
            pane::selectedText, () -> { owner.selectTab(terminal); pane.focusTerminal(); },
            (side, spec) -> {
                TerminalContent created = switch (spec) {
                    case OpenSpec.Local local -> owner.openBeside(terminal, side, local.directory().orElse(null), null,
                        local.profile(), local.program());
                    case OpenSpec.Session session -> owner.openBeside(terminal, side, home(), session.request(),
                        Optional.empty(), Optional.empty());
                };
                return Optional.ofNullable(created).map(split -> entry(split, split.pane()));
            });
    }

    private static Path home() { return Path.of(System.getProperty("user.home")); }

    private Optional<PaneEntry> openTab(OpenSpec spec) {
        if (closed) return Optional.empty();
        TerminalContent terminal = switch (spec) {
            case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory), null, local.profile(), local.program());
            case OpenSpec.Session session -> owner.openTab(home(), session.request());
        };
        return terminal == null ? Optional.empty() : Optional.of(entry(terminal, terminal.pane()));
    }

    void publish(TerminalEvent event) { if (!closed) registry.publish(event); }
    void atomically(Runnable change) { if (closed) change.run(); else registry.atomically(change); }
    void refresh() { if (!closed) registry.refresh(); }

    /**
     * A tab this window now shows: opened here, or moved here from {@code from}, which reports the move. A move that ended
     * back in this window, as a failed move's recovery does, reports nothing.
     */
    void tabArrived(Tab tab, Optional<UUID> from) {
        UUID id = tab.id().value();
        if (from.isEmpty()) publish(new TerminalEvent.TabOpened(owner.id(), id));
        else if (!from.get().equals(owner.id())) publish(new TerminalEvent.TabMoved(id, from.get(), owner.id()));
    }

    /**
     * Takes back a {@link #tabArrived} when this window then failed to show the tab: a tab that opened here is reported
     * closed, and a tab that moved here moves back to {@code from}; wherever the registry's recovery then attaches it
     * reports its own arrival (workspace plan 3b Rulings 5).
     */
    void tabWithdrawn(Tab tab, Optional<UUID> from) {
        UUID id = tab.id().value();
        if (from.isEmpty()) publish(new TerminalEvent.TabClosed(owner.id(), id));
        else if (!from.get().equals(owner.id())) publish(new TerminalEvent.TabMoved(id, owner.id(), from.get()));
    }

    /** A tab of this window closed; a tab leaving for another window is reported where it arrives. */
    void tabClosed(Tab tab) { publish(new TerminalEvent.TabClosed(owner.id(), tab.id().value())); }

    /** Reports the active tab, terminal or content, when it actually changed; layout changes happen for other reasons too. */
    void tabSelected() {
        Tab current = owner.selectedTab();
        UUID now = current == null ? null : current.id().value();
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
