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
}
