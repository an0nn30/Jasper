package dev.jasper.editor.external;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A known editor found on this machine.
 *
 * @param editor the editor
 * @param executable its program, absolute
 */
public record Detected(KnownEditor editor, Path executable) {
    /** Rejects nulls and a relative program. */
    public Detected {
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) throw new IllegalArgumentException("An editor's program must be absolute: " + executable);
    }
}
