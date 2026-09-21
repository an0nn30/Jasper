package dev.jasper.terminal.session;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A program that is not a local process, for example a remote shell. The session reads {@code output} on its
 * reader thread, calls {@code input.write}, {@code flush} and {@code resize} only on its own writer thread, and
 * calls {@code close} exactly once. {@code close} must be idempotent, must unblock a pending read and must return
 * promptly; the caller chooses the thread it really runs on by what it passes here.
 *
 * @param output program to terminal, UTF-8; end of stream after the program ends
 * @param input terminal to program
 * @param resize columns and rows of the grid
 * @param exited the exit status; exceptional completion means the connection failed
 * @param close releases the connection
 */
public record AttachedConnection(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize,
                                 CompletableFuture<Integer> exited, Runnable close) {
    /** Rejects nulls. */
    public AttachedConnection {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(resize, "resize");
        Objects.requireNonNull(exited, "exited");
        Objects.requireNonNull(close, "close");
    }
}
