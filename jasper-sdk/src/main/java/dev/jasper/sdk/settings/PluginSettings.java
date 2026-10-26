package dev.jasper.sdk.settings;

import dev.jasper.sdk.Subscription;
import java.nio.file.Path;

/**
 * A plugin's settings, kept in {@code plugins/<id>/<id>.toml} under Jasper's home. A plugin declares every setting
 * while it starts; the runtime then reads their values, reports values their kinds reject and keys nothing declares,
 * and shows them in Settings. Jasper reads the file again whenever it changes. A file that is not valid TOML, or that
 * gives a setting a value of the wrong TOML type, is reported and not applied: every setting keeps its last good
 * value.
 */
public interface PluginSettings {
    /**
     * Declares a setting. UI thread, while the plugin starts.
     *
     * @param spec the declaration, with a label
     * @param <T> the value type
     * @return the setting's handle, already holding the file's value
     * @throws IllegalStateException after the plugin's start has returned
     * @throws IllegalArgumentException for a declaration without a label, a key declared twice, or a key inside
     *         another declared key
     */
    <T> Setting<T> declare(SettingSpec<T> spec);

    /**
     * The settings file.
     *
     * @return {@code plugins/<id>/<id>.toml} under Jasper's home
     */
    Path file();

    /**
     * Runs the handler on the UI thread after any declared value changed.
     *
     * @param handler the handler
     * @return the registration; the runtime closes it when the plugin stops
     */
    Subscription onChanged(Runnable handler);

    /**
     * Shows a page of the plugin's own in Settings instead of the page generated from its declarations. UI thread,
     * while the plugin starts; at most once.
     *
     * @param panel the page
     * @throws IllegalStateException after the plugin's start has returned, or when a page was already given
     */
    void page(SettingsPanel panel);

    /**
     * Opens Settings at this plugin's page over the active window. UI thread.
     *
     * @throws IllegalStateException off the UI thread, or after the plugin's context closed
     * @since 0.22.0
     */
    void show();
}
