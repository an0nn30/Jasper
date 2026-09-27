package dev.jasper.app.testsupport;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/** A real terminal session whose transport stays idle until its owner closes it. */
public final class IdleTerminalSession {
    private IdleTerminalSession() { }

    public static TerminalSession start() {
        var stopped = new CountDownLatch(1);
        var exited = new CompletableFuture<Integer>();
        var output = new InputStream() {
            @Override public int read() throws IOException {
                try { stopped.await(); return -1; }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
            @Override public void close() { stopped.countDown(); }
        };
        return TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
            (columns, rows) -> { }, exited, () -> { output.close(); exited.complete(0); }),
            new GridSize(80, 24), 100);
    }
}
