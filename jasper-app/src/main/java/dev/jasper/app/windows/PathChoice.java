package dev.jasper.app.windows;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owner and mode for a local path picker; exactly one owner is present. */
public record PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title,
                         Optional<Path> initial, Mode mode) {
    /** Existing files, one existing directory, or one file to save to. */
    public enum Mode { FILES, DIRECTORY, SAVE }

    public PathChoice {
        if ((terminalOwner == null) == (auxiliaryOwner == null)) throw new IllegalArgumentException("Exactly one owner is required");
        Objects.requireNonNull(title); Objects.requireNonNull(initial); Objects.requireNonNull(mode);
        initial = initial.map(path -> path.toAbsolutePath().normalize());
    }
}
