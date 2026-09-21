package dev.jasper.sdk.terminal;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A connected session, handed to {@link PendingSession#attach}. From that call on Jasper owns it: it invokes
 * {@code close} exactly once, whatever happens, so release everything that belongs to the session there.
 * None of these members is ever called on the event thread: {@code output} is read on the session's reader
 * thread, {@code input} and {@code resize} are used only on Jasper's writer thread for this session, and
 * {@code close} runs on Jasper's cleanup thread. They must not touch Swing or event-thread-only SDK methods.
 *
 * @param output remote to terminal, UTF-8; end of stream after the remote side ends. A read may block; {@code close} must unblock it
 * @param input terminal to remote; Jasper flushes after every write and never blocks the user on it
 * @param resize columns and rows of the pane, to pass to the remote pty; request {@link #TERM} there
 * @param exited the exit status; completing it exceptionally means the connection failed, and the message is shown
 * @param close idempotent; unblocks a pending read; returns promptly
 */
public record TerminalConnection(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize,
                                 CompletableFuture<Integer> exited, Runnable close) {
    /** The terminal type to request on the remote pty. */
    public static final String TERM = "xterm-256color";

    /** Rejects nulls. */
    public TerminalConnection {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(resize, "resize");
        Objects.requireNonNull(exited, "exited");
        Objects.requireNonNull(close, "close");
    }
}
