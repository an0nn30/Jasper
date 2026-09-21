package dev.jasper.terminal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Owns the handoff from a newly spawned process to a fully wired running session. */
final class PtySessionFactory {
    private PtySessionFactory() { }

    static TerminalSession start(SessionLaunchOptions options) throws IOException {
        PtyChild child = PtyChild.start(options);
        return finish(child, () -> new TerminalSession(events -> new TerminalAccess(child,
            options.grid().columns(), options.grid().rows(), options.scrollback(), events)));
    }

    static Map<String,String> environment(Map<String,String> source) {
        Map<String,String> copy = new HashMap<>(source);
        copy.put("TERM", "xterm-256color");
        copy.put("COLORTERM", "truecolor");
        return copy;
    }

    static TerminalSession finish(PtyChild child, Supplier<TerminalSession> make) {
        return finish(child::close, make);
    }

    /** Shared failure gate; the callback permits deterministic cleanup-failure tests. */
    static TerminalSession finish(Runnable close, Supplier<TerminalSession> make) {
        try {
            TerminalSession session = make.get();
            session.startReading();
            return session;
        } catch (RuntimeException | Error failure) {
            try { close.run(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
