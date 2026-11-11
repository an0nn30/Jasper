package dev.jasper.sdk.settings;

import dev.jasper.sdk.ui.Nodes;

/**
 * What a {@link SettingsPanel} may use: standard rows for declared settings and the page's unsaved values. UI thread.
 */
public interface SettingsPanelHost {
    /**
     * The standard label, control, Reset button and validation message for a declared setting, bound to the page's
     * unsaved value. The row marks itself when a search matches it.
     *
     * @param setting a setting this plugin declared
     * @return the row
     */
    Nodes.Node row(Setting<?> setting);


    /**
     * The page's unsaved value of a declared setting, or its saved value when nothing is pending.
     *
     * @param setting a setting this plugin declared
     * @param <T> the value type
     * @return the value Apply would save
     */
    <T> T pending(Setting<T> setting);

    /**
     * Sets the page's unsaved value of a declared setting from a control of the plugin's own; Apply saves it.
     *
     * @param setting a setting this plugin declared
     * @param value the new value
     * @param <T> the value type
     * @throws IllegalArgumentException when the setting's kind rejects the value
     */
    <T> void stage(Setting<T> setting, T value);

    /**
     * Tells Settings that the page's modified state changed.
     */
    void changed();

    /**
     * Closes Settings, asking about unsaved changes as Cancel does, then runs {@code action} on the UI thread; nothing
     * runs when the user keeps Settings open. Settings is modal, so a control that opens a window of the plugin's own,
     * such as a manager, closes Settings first. The page is disposed by the time {@code action} runs.
     *
     * @param action what to do once Settings has closed
     * @throws IllegalStateException when the page is closed
     * @throws UnsupportedOperationException if the host predates this call
     * @since 0.32.0
     */
    default void closeThen(Runnable action) {
        throw new UnsupportedOperationException("Closing Settings is unavailable");
    }
}
