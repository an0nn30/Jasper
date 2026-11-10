package dev.jasper.browser.page;

/**
 * Whether a page's next download asks where it goes (spec section 10, ruling F32): Save Link As… and Save Image As…
 * arm it with the address they download, on the UI thread, and the download handler takes it on Chromium's thread as
 * each of the page's downloads starts. It is taken once: by the page's first start of the armed address, but for its
 * fragment, within 10 s of arming; else by its first start within 1 s of arming, whatever its address, as a redirect
 * changes the address Chromium gives. A start of another address later than that, as one the page times itself, takes
 * nothing and goes without asking, and so does a redirected download whose start comes after the first second. A newer
 * arming replaces the one before; an ask not taken within 10 s lapses. Thread-safe. It names no Chromium type.
 */
final class DownloadAsk {
    /** How long after arming the armed address's start asks. */
    static final long ADDRESS_MILLIS = 10_000;
    /** How long after arming any start asks. */
    static final long ANY_MILLIS = 1_000;
    // Guarded by this: the address armed without its fragment, null when nothing is armed, and when it was armed.
    private String armed;
    private long armedAt;

    /**
     * Has the page's next download of {@code url} ask where it goes.
     *
     * @param url the address about to be downloaded
     * @param nowMillis the time now, in milliseconds since the epoch
     */
    synchronized void arm(String url, long nowMillis) {
        armed = withoutFragment(url);
        armedAt = nowMillis;
    }

    /**
     * Whether a download of {@code url} the page starts at {@code nowMillis} asks where it goes: once, for the armed
     * address within 10 s of arming, or for any address within 1 s of it.
     *
     * @param url the address Chromium gives the download, or null for none
     * @param nowMillis the time now, in milliseconds since the epoch
     * @return whether it asks; the ask is then taken
     */
    synchronized boolean take(String url, long nowMillis) {
        if (armed == null || nowMillis < armedAt) return false;
        long elapsed = nowMillis - armedAt; // negative only when the difference overflows: far outside
        if (elapsed < 0 || elapsed > ADDRESS_MILLIS) {
            armed = null;
            return false;
        }
        if (elapsed > ANY_MILLIS && !armed.equals(withoutFragment(url))) return false;
        armed = null;
        return true;
    }

    /** {@code url} without its fragment, which no request carries; empty for null. */
    private static String withoutFragment(String url) {
        if (url == null) return "";
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }
}
