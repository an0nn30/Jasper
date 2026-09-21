package dev.jasper.sdk.plugin;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Read-only view of the plugin's {@code [plugins."<id>"]} table in the user's configuration file.
 * Getters return empty when the key is absent or has another type, and are safe from any thread.
 */
public interface PluginConfig {
    /**
     * A string value.
     *
     * @param key key within this table
     * @return the value, or empty
     */
    Optional<String> string(String key);

    /**
     * An integer value.
     *
     * @param key key within this table
     * @return the value, or empty
     */
    OptionalLong integer(String key);

    /**
     * A boolean value.
     *
     * @param key key within this table
     * @return the value, or empty
     */
    Optional<Boolean> bool(String key);

    /**
     * An array of strings.
     *
     * @param key key within this table
     * @return the values, or an empty list when absent or not all strings
     */
    List<String> stringList(String key);

    /**
     * A nested table.
     *
     * @param key key within this table
     * @return the nested view, or empty
     */
    Optional<PluginConfig> table(String key);

    /**
     * Runs the handler on the UI thread after a reload that changed this plugin's table.
     *
     * @param handler change callback
     * @return the registration
     */
    Subscription onChanged(Runnable handler);

    /**
     * Reports a problem with one of the plugin's settings through the application's configuration
     * diagnostics. Reports are cleared on the next reload.
     *
     * @param key the offending key within this table
     * @param message plain text
     */
    void report(String key, String message);
}
