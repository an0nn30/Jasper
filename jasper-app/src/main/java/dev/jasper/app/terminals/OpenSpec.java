package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What a new tab or split runs. */
public sealed interface OpenSpec {
    /**
     * The user's shell, in a directory or where New Tab would start, or a program instead of it.
     *
     * @param directory where it starts; empty means where New Tab would
     * @param profile the shell profile id; empty means the user's default
     * @param program the program and its arguments, run instead of the shell with its environment; empty runs the shell
     */
    record Local(Optional<Path> directory, Optional<String> profile, Optional<List<String>> program) implements OpenSpec {
        public Local {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(profile, "profile");
            program = Objects.requireNonNull(program, "program").map(List::copyOf);
        }

        /** A shell profile. */
        public Local(Optional<Path> directory, Optional<String> profile) { this(directory, profile, Optional.empty()); }

        /** The user's default shell. */
        public Local(Optional<Path> directory) { this(directory, Optional.empty(), Optional.empty()); }
    }

    /** A session somebody else provides. */
    record Session(SessionRequest request) implements OpenSpec {
        public Session { Objects.requireNonNull(request, "request"); }
    }
}
