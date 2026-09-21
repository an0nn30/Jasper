package dev.jasper.sdk.plugin;

/**
 * A plugin's entry class: public, with a public no-argument constructor, named by {@code entry} in
 * {@code plugin.toml}. Both methods run on the UI thread and must return promptly; slow work belongs
 * on {@link PluginContext#background()}.
 */
public interface Plugin {
    /**
     * Registers the plugin's contributions. If this throws, everything the context handed out is
     * rolled back, the context is closed and the plugin is reported as failed.
     *
     * @param context this plugin's services
     * @throws Exception to fail the start
     */
    void start(PluginContext context) throws Exception;

    /**
     * Called once at shutdown, before the runtime closes what the plugin left open. Must not block:
     * a blocked UI thread cannot be abandoned and ends the process at the exit deadline.
     */
    default void stop() { }
}
