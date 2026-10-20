package dev.jasper.editor.external;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What the Default editor settings choose, once detection has said what is installed. */
public sealed interface Choice permits Choice.Jasper, Choice.System, Choice.Installed, Choice.Custom {
    /**
     * The Jasper Editor.
     *
     * @param client the {@code jasper} command, for {@code EDITOR}; empty when there is none
     */
    record Jasper(Optional<Path> client) implements Choice {
        /** Rejects null. */
        public Jasper { Objects.requireNonNull(client, "client"); }
    }

    /** The system's default app opens files; {@code EDITOR} and {@code VISUAL} are left alone. */
    record System() implements Choice { }

    /**
     * An editor detection found.
     *
     * @param editor the editor and its program
     */
    record Installed(Detected editor) implements Choice {
        /** Rejects null. */
        public Installed { Objects.requireNonNull(editor, "editor"); }
    }

    /**
     * The user's own command.
     *
     * @param command the command line as written; the file is added at the end
     * @param inTerminal whether it runs in a terminal tab
     */
    record Custom(String command, boolean inTerminal) implements Choice {
        /** Rejects null. */
        public Custom { Objects.requireNonNull(command, "command"); }
    }
}
