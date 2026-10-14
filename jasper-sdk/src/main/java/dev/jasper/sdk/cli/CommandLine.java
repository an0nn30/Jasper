package dev.jasper.sdk.cli;

import dev.jasper.sdk.Subscription;

/**
 * Answers this plugin's {@code jasper} subcommands. Implemented by the application and by the testkit.
 *
 * @since 0.17.0
 */
public interface CommandLine {
    /**
     * Handles a subcommand this plugin's {@code plugin.toml} declares. UI thread only. The registration ends with the
     * plugin, and a call still running then ends with exit code 1; while no handler is registered, {@code jasper <name>}
     * reports the subcommand unavailable.
     *
     * @param name a name the manifest declares under {@code [[subcommands]]}
     * @param handler runs each call
     * @return removes the handler
     * @throws IllegalArgumentException when the manifest does not declare {@code name}, another plugin provides it, or
     *     it already has a handler
     * @throws IllegalStateException off the UI thread, or after the plugin stopped
     */
    Subscription handle(String name, CommandHandler handler);
}
