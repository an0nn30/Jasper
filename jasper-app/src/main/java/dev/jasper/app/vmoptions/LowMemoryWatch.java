package dev.jasper.app.vmoptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * When to warn that Jasper is running low on memory (spec section 5.4): heap threshold signals are throttled to one per
 * {@link #THROTTLE}; more than {@link #SIGNALS} within a sliding {@link #WINDOW} warn; an {@code OutOfMemoryError}
 * warns at once; Jasper warns at most once until the user changes the heap. Thread-safe: signals arrive on the JMX
 * notification thread.
 */
public final class LowMemoryWatch {
    /** The share of the old generation's maximum at which its collection usage signals. */
    public static final double FRACTION = 0.95;
    static final Duration THROTTLE = Duration.ofSeconds(20);
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int SIGNALS = 5;

    private final Clock clock;
    private final ArrayDeque<Instant> counted = new ArrayDeque<>();
    private boolean warned;

    /** A watch reading the time from {@code clock}. */
    public LowMemoryWatch(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /** A heap threshold signal; true when it is the one that should raise the warning. */
    public synchronized boolean signal() {
        Instant now = clock.instant();
        if (!counted.isEmpty() && Duration.between(counted.getLast(), now).compareTo(THROTTLE) < 0) return false;
        counted.addLast(now);
        while (Duration.between(counted.getFirst(), now).compareTo(WINDOW) > 0) counted.removeFirst();
        if (warned || counted.size() <= SIGNALS) return false;
        warned = true;
        return true;
    }

    /** An {@code OutOfMemoryError}; true unless Jasper already warned. */
    public synchronized boolean outOfMemory() {
        if (warned) return false;
        warned = true;
        return true;
    }

    /** The user saved a new heap limit: the warning may show again, and the count starts over. */
    public synchronized void heapChanged() {
        warned = false;
        counted.clear();
    }
}
