package dev.jasper.sdk.cli;

import java.util.concurrent.CompletionStage;

/**
 * Runs the calls of one subcommand.
 *
 * @since 0.17.0
 */
@FunctionalInterface
public interface CommandHandler {
    /**
     * Starts a call, on the UI thread. The stage completes, on any thread, with the exit code: 0 to 255; anything else
     * is reported as 1. A handler that throws, or a stage that fails, prints {@code jasper: <name> failed: ...} and exits 1.
     *
     * @param call the call
     * @return its exit code
     */
    CompletionStage<Integer> run(CommandCall call);
}
