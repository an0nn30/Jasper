package dev.jasper.sdk.settings;

import java.util.List;
import javax.swing.JComponent;

/**
 * A plugin's own page in Settings, used instead of the page Settings generates from its declarations, in the manner
 * of IntelliJ's {@code Configurable}. Every method runs on the UI thread. Settings saves the declared settings the page
 * shows through {@link SettingsPanelHost} on Apply; {@link #modified}, {@link #apply} and {@link #reset} cover only
 * state of the plugin's own.
 */
public interface SettingsPanel {
    /**
     * Builds the page, once per opening of Settings, when the page is first shown.
     *
     * @param host standard rows and unsaved values for the plugin's declared settings
     * @return the page's component
     */
    JComponent create(SettingsPanelHost host);

    /**
     * Extra words Settings' search matches for this page.
     *
     * @return the words
     */
    default List<String> keywords() { return List.of(); }

    /**
     * Marks what matches a search in the plugin's own controls; standard rows mark themselves.
     *
     * @param query the search text; blank clears the marks
     */
    default void highlight(String query) { }

    /**
     * Whether state of the plugin's own, beyond its declared settings, has unsaved changes.
     *
     * @return true when Apply would save something of the plugin's own
     */
    default boolean modified() { return false; }

    /**
     * Saves state of the plugin's own; Settings saves the declared settings.
     */
    default void apply() { }

    /**
     * Drops unsaved state of the plugin's own.
     */
    default void reset() { }

    /**
     * Settings closed the page; release listeners.
     */
    default void dispose() { }
}
