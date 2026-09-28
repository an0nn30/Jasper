package dev.jasper.sdk.settings;

import javax.swing.JComponent;

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
    JComponent row(Setting<?> setting);

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
     * Tells Settings that the page's modified state or validity changed.
     */
    void changed();
}
