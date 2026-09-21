package dev.jasper.terminal.session;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Session notifications. Output-driven callbacks run on the reader thread; explicit
 * resize and clear operations can notify on their calling thread. Screen/reset callbacks
 * may hold the buffer lock: return promptly and marshal UI work onto the EDT.
 * Command-start/finish callbacks run after command capture releases the buffer lock.
 */
public interface TerminalSessionListener {
    public default void screenChanged() {
    }

    public default void titleChanged(String title) {
    }

    public default void bell() {
    }

    public default void workingDirectoryChanged(Path directory) {
    }

    /** The scrollback was erased, so absolute rows held from before now name different lines (or none). */
    public default void scrollbackReset() {
    }

    /** The active terminal buffer changed, so absolute rows from the previous buffer are no longer meaningful. */
    public default void alternateBufferChanged(boolean alternate) {
    }

    /**
     * The shell marked the start of a command it is about to run (OSC 133 C). Reader thread, and
     * fired outside the buffer lock. Exactly one start per {@link #commandExecuted}, except when
     * the pane is closed mid-command — then there is a start and no finish, which is precisely
     * the case a running notice exists to show.
     */
    public default void commandStarted(String command) {
    }

    /**
     * The shell ran a command it marked with OSC 133 B/C (and D when it sends one). Reader thread.
     * {@code duration} is measured from the command-start mark. A cycle that never saw one has no
     * command to report either, so it fires no callback at all rather than one with a zero duration.
     */
    public default void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory,
                                 Duration duration) {
    }
}

