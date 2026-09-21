package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A local terminal to open: the user's configured shell.
 *
 * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
 */
public record LocalSpec(Optional<Path> workingDirectory) {
    /** Rejects null and a relative directory. */
    public LocalSpec {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (workingDirectory.isPresent() && !workingDirectory.get().isAbsolute())
            throw new IllegalArgumentException("A working directory must be absolute: " + workingDirectory.get());
    }
}
