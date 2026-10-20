package dev.jasper.sdk.cli;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One call of a subcommand. Implemented by the application and by the testkit.
 *
 * @since 0.17.0
 */
public interface CommandCall {
    /** @return the arguments after the subcommand's name */
    List<String> arguments();

    /** @return the caller's working directory, absolute */
    Path workingDirectory();

    /** @return the window of the Jasper pane the command ran in, while that window is open; empty when it ran elsewhere */
    Optional<WindowOwner> window();

    /**
     * Writes to the caller's standard output; any thread. Dropped once the call completed or was cancelled.
     *
     * @param text the text, newlines included
     */
    void out(String text);

    /**
     * Writes to the caller's standard error; any thread. Dropped once the call completed or was cancelled.
     *
     * @param text the text, newlines included
     */
    void err(String text);

    /**
     * Runs {@code listener} on the UI thread when the caller goes away before the call completes, for example on Ctrl-C.
     *
     * @param listener called once
     * @return removes the listener
     */
    Subscription onCancelled(Runnable listener);
}
