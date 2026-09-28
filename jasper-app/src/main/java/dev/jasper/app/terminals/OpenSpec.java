package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What a new tab or split runs. */
public sealed interface OpenSpec {
    /**
     * The user's shell, in a directory or where New Tab would start.
     *
     * @param directory where it starts; empty means where New Tab would
     * @param profile the shell profile id; empty means the user's default
     */
    record Local(Optional<Path> directory, Optional<String> profile) implements OpenSpec {
        public Local {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(profile, "profile");
        }

        /** The user's default shell. */
        public Local(Optional<Path> directory) { this(directory, Optional.empty()); }
    }

    /** A session somebody else provides. */
    record Session(SessionRequest request) implements OpenSpec {
        public Session { Objects.requireNonNull(request, "request"); }
    }
}
