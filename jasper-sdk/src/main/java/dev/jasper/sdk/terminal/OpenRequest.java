package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What to run in a new tab or split. */
public sealed interface OpenRequest permits OpenRequest.Local, OpenRequest.Session {
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

    /**
     * A session the plugin provides. Needs {@code session.provide}.
     *
     * @param spec how to connect it
     */
    record Session(SessionSpec spec) implements OpenRequest {
        /** Rejects null. */
        public Session { Objects.requireNonNull(spec, "spec"); }
    }

    /**
     * A session the plugin provides.
     *
     * @param spec how to connect it
     * @return the request
     */
    static OpenRequest session(SessionSpec spec) { return new Session(spec); }
}
