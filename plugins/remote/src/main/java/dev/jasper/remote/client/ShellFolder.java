package dev.jasper.remote.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.apache.sshd.client.session.ClientSession;

/**
 * The folder of a shell on a dedicated connection: a read-only probe on a separate exec channel and the
 * shell's Enter signal. Nothing is written to the shell. {@link #read} blocks; call it off the UI thread.
 */
public final class ShellFolder {
    public static final Duration TIMEOUT = Duration.ofSeconds(2);

    /** The host is neither Linux nor macOS. */
    public static final class Unsupported extends IOException {
        public Unsupported() { super("This host does not report the shell's folder"); }
    }

    private final ClientSession session;
    private volatile Runnable enter = () -> { };

    ShellFolder(ClientSession session) { this.session = session; }

    /** Runs on the terminal's writer thread whenever the pane sends a carriage return. */
    public void onEnter(Runnable listener) { enter = listener; }

    void entered() { enter.run(); }

    /** The foreground process's directory; empty when the host cannot tell. */
    public Optional<String> read() throws IOException { return DirectoryProbe.read(session, TIMEOUT); }
}
