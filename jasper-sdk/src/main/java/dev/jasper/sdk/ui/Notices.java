package dev.jasper.sdk.ui;

/**
 * Tells the user something went wrong, the way the application reports its own failures: in the
 * window the user is using, or in the log when there is none. Safe from any thread. Implemented by
 * the application and by the testkit.
 */
public interface Notices {
    /**
     * Shows an error.
     *
     * @param message one or two sentences, already in the user's terms
     */
    void error(String message);
}
