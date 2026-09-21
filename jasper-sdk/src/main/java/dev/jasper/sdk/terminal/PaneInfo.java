package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A snapshot of one pane. Needs {@code terminal.observe}.
 *
 * @param title the program's title, or the running command when it set none
 * @param workingDirectory the local working directory the shell last reported; a hint that may not exist
 * @param remoteDirectory a directory reported under another host
 * @param columns the grid width in cells; zero before the session starts
 * @param rows the grid height in cells; zero before the session starts
 * @param shellIntegration whether the shell reports prompts and commands
 * @param kind who provides the session
 * @param providerPluginId the providing plugin, for a plugin session
 * @param state where the session is in its life
 * @param exitStatus the exit status; present only when the session exited with a known status
 */
public record PaneInfo(String title, Optional<Path> workingDirectory, Optional<RemoteDirectory> remoteDirectory, int columns, int rows,
                       boolean shellIntegration, SessionKind kind, Optional<String> providerPluginId, SessionState state,
                       OptionalInt exitStatus) {
    /** Rejects nulls, a negative grid, and an exit status on a session that has not exited. */
    public PaneInfo {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(remoteDirectory, "remoteDirectory");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(providerPluginId, "providerPluginId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(exitStatus, "exitStatus");
        if (columns < 0 || rows < 0) throw new IllegalArgumentException("A grid cannot be negative");
        if (exitStatus.isPresent() && state != SessionState.EXITED) throw new IllegalArgumentException("Only an exited session has an exit status");
    }

    /**
     * What a handle reports for a pane it never saw open.
     *
     * @return an exited local pane with no title, directory or grid
     */
    public static PaneInfo unknown() {
        return new PaneInfo("", Optional.empty(), Optional.empty(), 0, 0, false, SessionKind.LOCAL, Optional.empty(),
            SessionState.EXITED, OptionalInt.empty());
    }
}
