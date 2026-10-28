package dev.jasper.browser.page;

/**
 * The last mouse press a page heard, which decides where a link it opens goes and whether its script may open a
 * window. Chromium's transient user activation lasts 5 seconds after a press.
 *
 * @param button the button, as {@link java.awt.event.MouseEvent#getButton()} gives it
 * @param modifiersEx the keys and buttons held, as {@link java.awt.event.InputEvent#getModifiersEx()} gives them
 * @param whenMillis when it happened, in milliseconds since the epoch
 */
public record LastPress(int button, int modifiersEx, long whenMillis) {
    /** No press yet: it activates nothing. */
    public static final LastPress NONE = new LastPress(0, 0, Long.MIN_VALUE);
    private static final long ACTIVATION_MILLIS = 5_000;

    /**
     * Whether the press still activates its page: from the press to 5,000 ms after it.
     *
     * @param nowMillis the time now, in milliseconds since the epoch
     * @return whether {@code nowMillis} is within Chromium's transient activation of this press
     */
    public boolean withinActivation(long nowMillis) {
        if (whenMillis == Long.MIN_VALUE || nowMillis < whenMillis) return false;
        long elapsed = nowMillis - whenMillis; // negative only when the difference overflows: far outside
        return elapsed >= 0 && elapsed <= ACTIVATION_MILLIS;
    }
}
