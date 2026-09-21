package dev.jasper.terminal.session;

import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.process.PtyChild;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

/** Owns the handoff from a newly spawned process to a fully wired running session. */
final class PtySessionFactory {
    private PtySessionFactory() { }

    static TerminalSession start(SessionLaunchOptions options) throws IOException {
        PtyChild child = PtyChild.start(options.command(), options.environment(), options.workingDirectory(), options.grid().columns(), options.grid().rows());
        return finish(child, () -> new TerminalSession(events -> new TerminalAccess(child,
            options.grid().columns(), options.grid().rows(), options.scrollback(), events)));
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
