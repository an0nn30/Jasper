/**
 * Subcommands of the {@code jasper} command. A plugin declares each one under {@code [[subcommands]]} in
 * {@code plugin.toml} and answers it with {@link dev.jasper.sdk.cli.CommandLine#handle}: {@code jasper <name> ...}
 * reaches the running Jasper, which runs the handler with a {@link dev.jasper.sdk.cli.CommandCall} and relays its
 * output and exit code to the caller's terminal. Depends on {@code dev.jasper.sdk}.
 */
package dev.jasper.sdk.cli;
