package dev.jasper.browser.page;

/**
 * When Java's close of a page's browser can go to Chromium (plan 6's V13). JCEF's own way for a browser whose native
 * part is not made yet, a life span handler that closes it after its creation, does nothing: {@code CefBrowser_N.close}
 * marks the browser closing first, and the handler's close then returns at once, so the native browser would live on,
 * loading its page in its group's context. So the browser decides here instead:
 * <ul>
 *   <li>one whose creation was never asked for is never made: it refuses its creation from then on;</li>
 *   <li>one whose creation is under way is closed as Chromium makes it;</li>
 *   <li>one made is closed at once.</li>
 * </ul>
 * Plain Java. Any thread: Swing's asks for the creation and the close, Chromium's tells of the creation.
 */
final class BrowserClosing {
    /** What a close asked of the browser does now. */
    enum Close {
        /** The native browser exists: it closes now. */
        NOW,
        /** Its creation is under way: it closes as Chromium makes it ({@link #created}). */
        ON_CREATION,
        /** Its creation was never asked for: it is never made. */
        NEVER_MADE
    }

    // Guarded by this.
    private boolean asked, made, closing;

    /** Whether the browser's creation may be asked for now: not once Java has asked it to close. Asks for it. */
    synchronized boolean mayCreate() {
        if (closing) return false;
        asked = true;
        return true;
    }

    /** Java asks the browser to close: what that does now. */
    synchronized Close close() {
        closing = true;
        if (made) return Close.NOW;
        return asked ? Close.ON_CREATION : Close.NEVER_MADE;
    }

    /** Chromium has made the native browser: whether it is to close now, as Java asked before it existed. Once. */
    synchronized boolean created() {
        made = true;
        return closing;
    }
}
