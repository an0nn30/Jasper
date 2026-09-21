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
 * Listener exceptions are not isolated from the source thread; callbacks should return promptly
 * without throwing. Removing a listener does not cancel an already in-flight notification.
 */
public interface TerminalSessionListener {
    /** Visible buffer contents changed; may be called while holding the buffer lock. */
    public default void screenChanged() {
    }

    /** The reader received a program title; the argument is the new title. */
    public default void titleChanged(String title) {
    }

    /** The reader received BEL; schedule presentation work without blocking. */
    public default void bell() {
    }

    /** The reader decoded an OSC 7 working directory; the argument is the reported local path. */
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
     * fired outside the buffer lock when nonempty command text is available. A normal shell cycle
     * pairs this with {@link #commandExecuted}; an incomplete or interrupted protocol cycle may
     * have no finish. Closing the session does not synthesize a command completion.
     */
    public default void commandStarted(String command) {
    }

    /**
     * The shell ran a command it marked with OSC 133 B/C (and D when it sends one). Reader thread.
     * {@code duration} is measured from the command-start mark. A cycle that never saw one has no
     * command to report either, so it fires no callback at all rather than one with a zero duration.
     * The directory is the last OSC 7 value at completion, not a snapshot taken at command start.
     */
    public default void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory,
                                 Duration duration) {
    }

    /**
     * An attached session rejected input because the remote side is not accepting it and the outbound queue is
     * full. Called on the thread that wrote; never wait for the EDT here.
     */
    public default void inputDropped() {
    }
}
