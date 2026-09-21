package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What to run in a new tab or split. Plugin-provided sessions join this family in a later SDK version. */
public sealed interface OpenRequest permits OpenRequest.Local {
    /**
     * A local shell. Needs {@code terminal.open}.
     *
     * @param spec what to start
     */
    record Local(LocalSpec spec) implements OpenRequest {
        /** Rejects null. */
        public Local { Objects.requireNonNull(spec, "spec"); }
    }

    /**
     * The user's shell, where New Tab would start it.
     *
     * @return the request
     */
    static OpenRequest local() { return new Local(new LocalSpec(Optional.empty())); }

    /**
     * The user's shell in a directory.
     *
     * @param workingDirectory an absolute directory
     * @return the request
     */
    static OpenRequest localIn(Path workingDirectory) { return new Local(new LocalSpec(Optional.of(workingDirectory))); }

    /**
     * A local shell from a spec.
     *
     * @param spec what to start
     * @return the request
     */
    static OpenRequest local(LocalSpec spec) { return new Local(spec); }
}
