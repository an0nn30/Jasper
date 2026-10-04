package dev.jasper.app.application;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether a session has printed anything yet. It watches for the first screen change caused by output, which the
 * terminal processes on the session's reader thread; changes delivered on the Event Dispatch Thread (a resize or a
 * cleared scrollback from the view) are not output and are ignored. The watch attaches right after the session
 * starts, and output that arrives before it attaches is noticed at the next change (a shell that prints only once in
 * that window would fall back to the trace's 10-second cap). Any thread.
 */
final class SessionOutput implements TerminalSessionListener {
    private static final System.Logger LOG = System.getLogger(SessionOutput.class.getName());
    private final TerminalSession session;
    private final Runnable first;
    private final List<Runnable> waiting = new ArrayList<>(); // guarded by this
    private boolean seen; // guarded by this

    private SessionOutput(TerminalSession session, Runnable first) {
        this.session = session;
        this.first = first;
    }

    /** Starts watching; {@code first} runs once, on the thread that delivers the first screen change. */
    static SessionOutput watch(TerminalSession session, Runnable first) {
        var watch = new SessionOutput(session, first);
        session.addListener(watch);
        return watch;
    }

    @Override public void screenChanged() {
        if (java.awt.EventQueue.isDispatchThread()) return; // a resize or cleared scrollback, not shell output
        List<Runnable> ready;
        synchronized (this) {
            if (seen) return;
            seen = true;
            ready = List.copyOf(waiting);
            waiting.clear();
        }
        session.removeListener(this);
        run(first);
        ready.forEach(SessionOutput::run);
    }

    /** Runs {@code action} once output exists: now, on the caller's thread, if it already does; otherwise on the session's reader thread at the first screen change. Actions that touch Swing must hand off to the EDT themselves. */
    void whenSeen(Runnable action) {
        synchronized (this) {
            if (!seen) {
                waiting.add(action);
                return;
            }
        }
        run(action);
    }

    private static void run(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A start-up timing action failed", failure);
        }
    }
}
