package dev.jasper.browser.dialogs;

/**
 * One page's guard against a flood of dialogs, such as a script's {@code for (;;) alert(1)}, which would otherwise
 * reopen Jasper's modal as soon as it closes and hold the window. A dialog that opens within {@link #QUICK_MILLIS} of the
 * page's previous dialog closing continues a run; the third dialog of a run, and every dialog after it, is refused
 * until the page's next main-frame load start {@link #rearm}s the guard. Times are a monotonic clock's milliseconds,
 * which the caller passes in. UI thread.
 */
final class DialogFlood {
    /** How soon after the previous dialog closed the next one continues a run. */
    static final long QUICK_MILLIS = 1_000;
    // The dialog a run is refused at.
    private static final int REFUSED_AT = 3;
    private boolean anyClosed, stopped;
    private long lastClosed;
    private int run;

    /**
     * A dialog asks to open.
     *
     * @param now the time it asks
     * @return whether it may; once one is refused, none may until {@link #rearm}
     */
    boolean admit(long now) {
        if (stopped) return false;
        run = anyClosed && now - lastClosed <= QUICK_MILLIS ? run + 1 : 1;
        if (run >= REFUSED_AT) stopped = true;
        return !stopped;
    }

    /**
     * An admitted dialog closed.
     *
     * @param now the time it closed
     */
    void closed(long now) {
        anyClosed = true;
        lastClosed = now;
    }

    /** The page loads a new document: its dialogs may open again, and a run starts afresh. */
    void rearm() {
        stopped = false;
        anyClosed = false;
        run = 0;
    }
}
