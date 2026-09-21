package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Closed family of id-only terminal facts, delivered on the EDT. Not an external extension API. */
public sealed interface TerminalEvent {
    record WindowOpened(UUID windowId) implements TerminalEvent { }
    record WindowClosed(UUID windowId) implements TerminalEvent { }
    record WindowActivated(UUID windowId) implements TerminalEvent { }
    record TabOpened(UUID windowId, UUID tabId) implements TerminalEvent { }
    record TabClosed(UUID windowId, UUID tabId) implements TerminalEvent { }
    record TabSelected(UUID windowId, UUID tabId) implements TerminalEvent { }
    record PaneOpened(UUID tabId, UUID paneId) implements TerminalEvent { }
    record PaneClosed(UUID tabId, UUID paneId) implements TerminalEvent { }
    record PaneFocused(UUID tabId, UUID paneId) implements TerminalEvent { }
    /** Derived by the registry; windows never publish it. */
    record ActivePaneChanged(Optional<UUID> paneId) implements TerminalEvent { }
    record TitleChanged(UUID paneId, String title) implements TerminalEvent { }
    record DirectoryChanged(UUID paneId, Optional<Path> directory) implements TerminalEvent { }
    record CommandStarted(UUID paneId, String command) implements TerminalEvent { }
    record CommandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration,
                           Optional<Path> directory) implements TerminalEvent { }
    /** A provided session started connecting, first or again. */
    record SessionConnecting(UUID paneId) implements TerminalEvent { }
    record SessionStarted(UUID paneId) implements TerminalEvent { }
    record SessionExited(UUID paneId, OptionalInt exitStatus) implements TerminalEvent { }
    record Bell(UUID tabId, UUID paneId) implements TerminalEvent { }
}
