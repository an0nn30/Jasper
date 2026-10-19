package dev.jasper.app.bootstrap;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Requests that reach the home's owner before its application can take them (workspace-tabs spec 5.3): held in arrival
 * order and run in that order once {@link #open} is called, on a virtual thread; from then on each runs at once on the
 * thread that submits it. A request that throws is logged and the next runs. Any thread.
 */
final class EarlyRequests {
    private static final System.Logger LOG = System.getLogger(EarlyRequests.class.getName());

    /** What became of a submitted request. */
    enum Outcome {
        /** Held: it runs once the queue opens, after the requests before it. */
        HELD,
        /** Run at once, on the submitting thread. */
        RAN,
        /** Turned away: the queue was declined ({@link #declineIfIdle}). */
        DECLINED
    }

    private final Object lock = new Object();
    /** Null once every held request has run, or once the queue was declined. */
    private ArrayDeque<Runnable> held = new ArrayDeque<>();
    private boolean opened;
    private boolean declined;

    /**
     * As {@link #submit(Runnable, Runnable)}, the same request either way.
     *
     * @param request the request
     * @return what became of it
     */
    Outcome submit(Runnable request) { return submit(request, request); }

    /**
     * Holds {@code later} while the queue is not open or still runs the requests before it; otherwise runs {@code now}
     * on this thread. A declined queue runs neither.
     *
     * @param later what runs when the request is held
     * @param now what runs when the queue is open and empty
     * @return what became of the request
     */
    Outcome submit(Runnable later, Runnable now) {
        Objects.requireNonNull(later, "later");
        Objects.requireNonNull(now, "now");
        synchronized (lock) {
            if (declined) return Outcome.DECLINED;
            if (held != null) {
                held.add(later);
                return Outcome.HELD;
            }
        }
        run(now);
        return Outcome.RAN;
    }

    /** The application can take requests: the held ones run in order on a virtual thread. Once; later calls do nothing. */
    void open() {
        synchronized (lock) {
            if (opened || declined) return;
            opened = true;
        }
        Thread.ofVirtual().name("jasper-early-requests").start(this::drain);
    }

    /**
     * Turns every later request away when the queue was never opened and holds none: a launch that turns out not to stay
     * can then exit without dropping a request it already answered. Once declined, it stays declined.
     *
     * @return whether the queue is declined; false when it was opened or holds a request
     */
    boolean declineIfIdle() {
        synchronized (lock) {
            if (declined) return true;
            if (opened || !held.isEmpty()) return false;
            declined = true;
            held = null;
            return true;
        }
    }

    /** Whether requests are still held: before {@link #open}, and while the held ones run. */
    boolean holding() {
        synchronized (lock) { return held != null; }
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (lock) {
                next = held.poll();
                if (next == null) {
                    held = null;
                    return;
                }
            }
            run(next);
        }
    }

    private static void run(Runnable request) {
        try {
            request.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A launch request failed", failure);
        }
    }
}
