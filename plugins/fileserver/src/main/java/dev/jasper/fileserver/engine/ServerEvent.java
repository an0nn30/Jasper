package dev.jasper.fileserver.engine;

import java.time.Duration;
import java.time.Instant;

/** Something a client did; {@code remote} is the client's address, or empty for the server itself. */
public sealed interface ServerEvent {
    Instant at();
    String remote();

    enum Direction { DOWNLOAD, UPLOAD }

    record Connected(Instant at, String remote) implements ServerEvent { }
    record LoginSucceeded(Instant at, String remote, String user) implements ServerEvent { }
    record LoginFailed(Instant at, String remote, String user) implements ServerEvent { }
    record Changed(Instant at, String remote, String description) implements ServerEvent { }
    record Failed(Instant at, String remote, String message) implements ServerEvent { }
    /** A transfer began; {@code total} is -1 when unknown, and {@code cancel} drops the client's connection. */
    record TransferStarted(Instant at, String remote, long id, Direction direction, String path, long total, Runnable cancel) implements ServerEvent { }
    record TransferProgress(Instant at, String remote, long id, long bytes, long total) implements ServerEvent { }
    record Transferred(Instant at, String remote, long id, Direction direction, String path, long bytes, Duration took, boolean complete) implements ServerEvent { }
}
