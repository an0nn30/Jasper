package dev.jasper.sample;

import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A loopback "remote": whatever is typed comes back, Enter starts a new line, Ctrl-D ends the session. It shows
 * the whole contract of a connection in a few lines: a blocking read that close unblocks, an exit status, and a
 * close that is safe to call more than once.
 */
final class EchoSession {
    private static final byte[] END = new byte[0];
    private final LinkedBlockingQueue<byte[]> toTerminal = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final InputStream output = new InputStream() {
        private byte[] current = END;
        private int position;
        private boolean ended;
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            while (position >= current.length) {
                if (ended) return -1;
                try { current = toTerminal.take(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                position = 0;
                if (current == END) { ended = true; return -1; }
            }
            int count = Math.min(length, current.length - position);
            System.arraycopy(current, position, buffer, offset, count);
            position += count;
            return count;
        }
        @Override public int available() {
            int queued = 0;
            for (byte[] chunk : toTerminal) queued += chunk.length;
            return current.length - position + queued;
        }
    };

    private final OutputStream input = new OutputStream() {
        @Override public void write(int value) { write(new byte[]{(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) {
            for (int i = offset; i < offset + length && !exited.isDone(); i++) {
                if (bytes[i] == 4) { say("\r\n[sample echo ended]\r\n"); toTerminal.add(END); exited.complete(0); }
                else if (bytes[i] == '\r') say("\r\n");
                else toTerminal.add(new byte[]{bytes[i]});
            }
        }
    };

    EchoSession() { say("Sample echo session. Type anything; Ctrl-D ends it.\r\n"); }

    private void say(String text) { toTerminal.add(text.getBytes(StandardCharsets.UTF_8)); }

    TerminalConnection connection() {
        return new TerminalConnection(output, input, (columns, rows) -> { }, exited, () -> {
            // Idempotent, prompt, and it unblocks a reader waiting in take().
            if (closed.compareAndSet(false, true)) toTerminal.add(END);
        });
    }
}
