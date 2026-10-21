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
    /**
     * The words the caller typed after {@code jasper <name>}, as its shell passed them.
     *
     * @return the arguments after the subcommand's name
     */
    List<String> arguments();

    /**
     * The directory the {@code jasper} command ran in; resolve relative paths in {@link #arguments()} against it.
     *
     * @return the caller's working directory, absolute
     */
    Path workingDirectory();

    /**
     * The Jasper window holding the pane the command ran in, found through the pane's {@code JASPER_PANE} variable when
     * the call starts; an owner for a dialog or chooser the call opens.
     *
     * @return the Jasper window of the pane the command ran in, as found when the call started; empty when it ran
     *         elsewhere or that window was not open then
     */
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
     * Runs {@code listener} on the UI thread when the caller goes away before the call completes, for example on Ctrl+C.
     *
     * @param listener called once
     * @return removes the listener
     */
    Subscription onCancelled(Runnable listener);
}
