package dev.jasper.remote.client;

import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.IOException;
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
    static TerminalConnection open(ClientSession session, int columns, int rows, Duration timeout, Runnable released) throws IOException {
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
        return new TerminalConnection(channel.getInvertedOut(), channel.getInvertedIn(),
            (newColumns, newRows) -> { try { channel.sendWindowChange(Math.max(1, newColumns), Math.max(1, newRows)); } catch (IOException ignored) { /* closing */ } },
            exited,
            () -> { if (!closedOnce.compareAndSet(false, true)) return; try { channel.close(false); } finally { release.run(); } });
    }
}
