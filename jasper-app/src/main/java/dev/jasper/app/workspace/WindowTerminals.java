package dev.jasper.app.workspace;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One window's presence in the terminal registry (workspace tabs spec 9.4): entries that read this window's terminal
 * tabs, one pane each, on demand, and the facts the window publishes. A pane's split opens a terminal tab in a new group
 * beside the pane's group. Owned and closed by {@link WindowContent}. EDT only.
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
            () -> Optional.ofNullable(owner.currentTerminal()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);
        registration = registry.addWindow(entry);
        registry.atomically(() -> {
            for (TerminalContent terminal : owner.terminalTabs()) {
                tabOpened(terminal);
                publish(new TerminalEvent.PaneOpened(terminal.id().value(), terminal.pane().id()));
            }
            tabSelected();
        });
    }

    TabEntry entry(TerminalContent terminal) {
        return new TabEntry(terminal.id().value(), owner.id(),
            () -> terminal.closed() ? List.of() : List.of(entry(terminal, terminal.pane())),
            () -> terminal.closed() ? Optional.empty() : Optional.of(entry(terminal, terminal.pane())),
            terminal::title, () -> owner.selectTab(terminal));
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

    void tabOpened(TerminalContent terminal) { publish(new TerminalEvent.TabOpened(owner.id(), terminal.id().value())); }
    void tabClosed(TerminalContent terminal) { publish(new TerminalEvent.TabClosed(owner.id(), terminal.id().value())); }

    /** Reports the active terminal tab when it actually changed; layout changes happen for other reasons too. */
    void tabSelected() {
        TerminalContent current = owner.currentTerminal();
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
