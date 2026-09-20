package dev.jasper.terminal;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Owns the handoff from a newly spawned process to a running terminal session. */
final class PtySessionFactory {
    private PtySessionFactory() { }
    static TerminalSession start(SessionLaunchOptions options) throws IOException {
        var connector = new PtyConnector(PtyChild.start(options));
        return finish(connector, () -> new TerminalSession(connector,
            options.grid().columns(), options.grid().rows(), options.scrollback()));
    }
static Map<String,String> environment(Map<String,String> source) {
    Map<String,String> copy = new HashMap<>(source);
    copy.put("TERM", "xterm-256color");
    copy.put("COLORTERM", "truecolor");
    return copy;
}

static TerminalSession finish(TtyConnector connector, Supplier<TerminalSession> make) {
    try {
        TerminalSession session = make.get();
        session.startReading();
        return session;
    } catch (RuntimeException | Error failure) {
        try { connector.close(); }
        catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
    }
}
}
