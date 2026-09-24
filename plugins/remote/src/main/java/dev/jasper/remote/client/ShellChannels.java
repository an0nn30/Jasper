package dev.jasper.remote.client;

import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyChannelConfiguration;

/** Opens a PTY shell channel and wraps it as the SDK's connection record. */
final class ShellChannels {
    private ShellChannels() { }

    /**
     * Blocks (background thread) until the channel is open. {@code released} runs exactly once, on the
     * caller's thread of {@code close}, after the channel is closed; the registry drops a reference there.
     */
    static TerminalConnection open(ClientSession session, int columns, int rows, Duration timeout, Runnable entered, Runnable released) throws IOException {
        var pty = new PtyChannelConfiguration();
        pty.setPtyType(TerminalConnection.TERM);
        pty.setPtyColumns(Math.max(1, columns)); pty.setPtyLines(Math.max(1, rows));
        pty.setPtyWidth(Math.max(1, columns) * 8); pty.setPtyHeight(Math.max(1, rows) * 16);
        ChannelShell channel = session.createShellChannel(pty, Map.of("TERM", TerminalConnection.TERM, "COLORTERM", "truecolor"));
        var exited = new CompletableFuture<Integer>();
        var releasedOnce = new AtomicBoolean();
        Runnable release = () -> { if (releasedOnce.compareAndSet(false, true)) released.run(); };
        channel.addCloseFutureListener(closed -> {
            release.run();
            Integer status = channel.getExitStatus();
            if (status != null) exited.complete(status);
            else exited.completeExceptionally(new IOException("connection lost"));
        });
        try { channel.open().verify(timeout); } catch (IOException | RuntimeException failure) { channel.close(true); release.run(); throw failure; }
        var closedOnce = new AtomicBoolean();
        return new TerminalConnection(channel.getInvertedOut(), new EnterWatch(channel.getInvertedIn(), entered),
            (newColumns, newRows) -> { try { channel.sendWindowChange(Math.max(1, newColumns), Math.max(1, newRows)); } catch (IOException ignored) { /* closing */ } },
            exited,
            () -> { if (!closedOnce.compareAndSet(false, true)) return; try { channel.close(false); } finally { release.run(); } });
    }

    /** Passes the terminal's input through unchanged and signals once per write that contains a carriage return. */
    static final class EnterWatch extends OutputStream {
        private final OutputStream out;
        private final Runnable entered;
        EnterWatch(OutputStream out, Runnable entered) { this.out = out; this.entered = entered; }
        @Override public void write(int value) throws IOException { out.write(value); if ((byte) value == '\r') entered.run(); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            for (int i = offset; i < offset + length; i++) if (bytes[i] == '\r') { entered.run(); return; }
        }
        @Override public void flush() throws IOException { out.flush(); }
        @Override public void close() throws IOException { out.close(); }
    }
}
