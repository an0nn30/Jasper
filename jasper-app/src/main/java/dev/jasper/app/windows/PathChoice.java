package dev.jasper.app.windows;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner and mode for a local path picker; exactly one owner is present. {@code terminalFolder} is the owner's focused
 * terminal folder: listed as "Terminal folder", and where the picker opens when {@code initial} is empty.
 */
public record PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title,
                         Optional<Path> initial, Mode mode, Optional<Path> terminalFolder) {
    /** Existing files, one existing directory, or one file to save to. */
    public enum Mode { FILES, DIRECTORY, SAVE }

    public PathChoice {
        if ((terminalOwner == null) == (auxiliaryOwner == null)) throw new IllegalArgumentException("Exactly one owner is required");
        Objects.requireNonNull(title); Objects.requireNonNull(initial); Objects.requireNonNull(mode); Objects.requireNonNull(terminalFolder);
        initial = initial.map(path -> path.toAbsolutePath().normalize());
        terminalFolder = terminalFolder.map(path -> path.toAbsolutePath().normalize());
    }

    public PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title, Optional<Path> initial, Mode mode) {
        this(terminalOwner, auxiliaryOwner, title, initial, mode, Optional.empty());
    }
}
