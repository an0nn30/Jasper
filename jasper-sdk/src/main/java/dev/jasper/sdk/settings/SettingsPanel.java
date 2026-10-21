package dev.jasper.sdk.settings;

import java.util.List;
import dev.jasper.sdk.ui.UiView;

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
    UiView create(SettingsPanelHost host);

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
     * Called after a standard row from {@link SettingsPanelHost#row} handles the user's input: a typed edit, a
     * switch, a choice, a list action, a file from Browse… or Reset. {@link SettingsPanelHost#pending} already returns
     * the resulting value and the row shows its validation; an invalid edit leaves the pending value as it was.
     * Rebuild the descriptors that depend on pending values here. The plugin's own {@link SettingsPanelHost#stage}
     * calls do not trigger it. Runs on the UI thread while the page is shown.
     * @since 0.18.3
     */
    default void pendingChanged() { }

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
     * Drops unsaved state of the plugin's own. Settings calls it just before {@link #dispose} when it closes a page
     * it showed without a successful Apply since the page last changed, as on Cancel; a page Settings never showed
     * gets no {@code reset}. The panel outlives each opening of Settings, so this is where unapplied state of its
     * own goes away.
     */
    default void reset() { }

    /**
     * Settings closed the page; release listeners. Unapplied state of the plugin's own was already dropped through
     * {@link #reset}.
     */
    default void dispose() { }
}
