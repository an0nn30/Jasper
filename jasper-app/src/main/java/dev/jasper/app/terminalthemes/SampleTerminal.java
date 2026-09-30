package dev.jasper.app.terminalthemes;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import javax.swing.JComponent;

/**
 * A real terminal showing canned output, drawn by Jasper's renderer in whatever palette it is given: a prompt, a
 * listing in the ANSI colours, and the 16 colours as blocks. It takes no input and never ends until closed. EDT only.
 */
final class SampleTerminal implements AutoCloseable {
    static final String E = "\033[";
    /** The output: plain text, SGR colours and a prompt; lines end in CR LF. */
    static final String TEXT = E + "32m" + "me@jasper" + E + "0m:" + E + "34m" + "~/projects" + E + "0m$ ls -l\r\n"
        + "drwxr-xr-x  " + E + "1;34m" + "src" + E + "0m      " + E + "1;32m" + "build.sh" + E + "0m  "
        + E + "35m" + "logo.png" + E + "0m  " + E + "36m" + "link" + E + "0m -> " + E + "33m" + "notes.md" + E + "0m\r\n"
        + E + "31m" + "error:" + E + "0m something failed   " + E + "33m" + "warning:" + E + "0m check this\r\n"
        + "\r\n" + blocks(40) + "\r\n" + blocks(100) + "\r\n\r\n"
        + E + "32m" + "me@jasper" + E + "0m:" + E + "34m" + "~/projects" + E + "0m$ ";

    private final TerminalSession session;
    private final TerminalView view;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private boolean closed;

    SampleTerminal(TerminalOptions options) {
        var exited = new CompletableFuture<Integer>();
        InputStream idle = new InputStream() {
            @Override public int read() throws IOException {
                try { stopped.await(); return -1; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            @Override public void close() { stopped.countDown(); }
        };
        InputStream output = new SequenceInputStream(new ByteArrayInputStream(TEXT.getBytes(StandardCharsets.UTF_8)), idle);
        session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, exited,
            () -> { stopped.countDown(); exited.complete(0); }), new GridSize(60, 10), 100);
        view = new TerminalView(session, options);
        view.setFocusable(false);
    }

    /** Eight colour blocks, SGR {@code base}..{@code base+7}, separated by spaces. */
    private static String blocks(int base) {
        var line = new StringBuilder();
        for (int i = 0; i < 8; i++) line.append(E).append(base + i).append("m").append("    ").append(E).append("0m ");
        return line.toString();
    }

    JComponent component() { return view; }

    void palette(Palette palette) { view.setPalette(palette); }

    Palette palette() { return view.palette(); }

    boolean closed() { return closed; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        session.close();
    }
}
