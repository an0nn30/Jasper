package dev.jasper.browser.page;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A page's transient user activation, as Chromium keeps one: a press or a key the page got activates it for 5 seconds,
 * and each popup that opens consumes it, so one gesture opens at most one popup. The next press or key activates it
 * again. Thread-safe: the page's view activates it on the UI thread, and whatever opens a popup consumes it on
 * Chromium's.
 */
public final class Activation {
    // When the page was last activated, or Long.MIN_VALUE before that and once a popup consumed it.
    private final AtomicLong at = new AtomicLong(Long.MIN_VALUE);

    /** An activation nothing has activated yet. */
    public Activation() { }

    /**
     * A press or key the page got activates it.
     *
     * @param whenMillis when it happened, in milliseconds since the epoch
     */
    public void activate(long whenMillis) {
        at.set(whenMillis);
    }

    /**
     * Whether the page is activated at {@code nowMillis}: within 5,000 ms of the press or key that last activated it,
     * and not consumed since.
     *
     * @param nowMillis the time now, in milliseconds since the epoch
     * @return whether a popup may open
     */
    public boolean active(long nowMillis) {
        return LastPress.withinActivation(at.get(), nowMillis);
    }

    /**
     * Consumes the activation for one popup: true, once, while the page is {@link #active}; false until the next
     * press or key after that.
     *
     * @param nowMillis the time now, in milliseconds since the epoch
     * @return whether the popup may open
     */
    public boolean consume(long nowMillis) {
        for (long when = at.get(); LastPress.withinActivation(when, nowMillis); when = at.get())
            if (at.compareAndSet(when, Long.MIN_VALUE)) return true;
        return false;
    }
}
