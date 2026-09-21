package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What a new tab or split runs. */
public sealed interface OpenSpec {
    /** The user's configured shell, in a directory or where New Tab would start. */
    record Local(Optional<Path> directory) implements OpenSpec {
        public Local { Objects.requireNonNull(directory, "directory"); }
    }

    /** A session somebody else provides. */
    record Session(SessionRequest request) implements OpenSpec {
        public Session { Objects.requireNonNull(request, "request"); }
    }
}
