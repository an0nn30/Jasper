package dev.jasper.sdk.ui;

/** Status bar contributions. */
public interface StatusBar {
    /**
     * Adds an item on the UI thread. It is visible, with no text, icon, tooltip or action, until set.
     *
     * @param spec where the item goes
     * @return the item
     * @throws IllegalArgumentException when the id does not start with the plugin's id and a dot, or is already in use
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    StatusItem add(StatusItemSpec spec);

    /**
     * Adds a progress control on the UI thread, initially indeterminate with empty text.
     * It shares the status-item namespace and ordering; one handle drives every window.
     * @param spec identity and placement
     * @return the progress handle
     * @throws IllegalArgumentException for a foreign or duplicate id
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host predates progress controls
     * @since 0.7.5
     */
    default StatusProgress addProgress(StatusItemSpec spec) {
        throw new UnsupportedOperationException("Progress is unavailable");
    }
}
