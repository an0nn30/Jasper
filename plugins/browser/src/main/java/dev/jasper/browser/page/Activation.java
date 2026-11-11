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
     * Whether the page is activated at {@code nowMillis}, as {@link #active} says, by a press or key after
     * {@code sinceMillis} (plan 8's L42): an activation from before the page's main frame last started loading a
     * document begins none of that document's own navigations.
     *
     * @param sinceMillis the moment the activation must come after, in milliseconds since the epoch, or
     *     {@link Long#MIN_VALUE} for any
     * @param nowMillis the time now, in milliseconds since the epoch
     * @return whether it is so activated
     */
    public boolean activeSince(long sinceMillis, long nowMillis) {
        long when = at.get();
        return when > sinceMillis && LastPress.withinActivation(when, nowMillis);
    }

    /**
     * Whether the press or key at {@code whenMillis} is what last activated the page: nothing activated it after, and
     * nothing consumed it since (plan 8's L38).
     *
     * @param whenMillis when a press or key happened, in milliseconds since the epoch
     * @return whether the activation is still that one's; false for {@link Long#MIN_VALUE}, which is never
     */
    public boolean lastActivatedAt(long whenMillis) {
        return whenMillis != Long.MIN_VALUE && at.get() == whenMillis;
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
