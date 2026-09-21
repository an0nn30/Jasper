package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** What a pane reports about itself at one moment. The grid is zero by zero until the session starts; {@code providerId} names who provides a session that is not a local shell; at most one of the two directories is present. */
public record PaneSnapshot(String title, Optional<Path> workingDirectory, int columns, int rows, boolean shellIntegration,
                           State state, OptionalInt exitStatus, Optional<String> providerId,
                           Optional<RemoteLocation> remoteDirectory) {
    /** A session's life. */
    public enum State { STARTING, RUNNING, EXITED }

    public PaneSnapshot {
        Objects.requireNonNull(title); Objects.requireNonNull(workingDirectory); Objects.requireNonNull(state); Objects.requireNonNull(exitStatus);
        Objects.requireNonNull(providerId);
        Objects.requireNonNull(remoteDirectory);
    }
}
