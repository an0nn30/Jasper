package dev.jasper.app.windows;

import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner and request for the file chooser; exactly one owner is present. {@code terminalFolder} is where the owner's
 * focused terminal is (listed as "Terminal folder", and where the chooser opens when {@code initial} is empty);
 * {@code sources} are the plugin sources offered besides the local disk.
 */
public record PathChoice(UUID jasperOwner, AuxiliarySurface auxiliaryOwner, String title, Optional<Location> initial,
                         Mode mode, boolean multiple, Optional<Location> terminalFolder, Optional<String> suggestedName,
                         List<ChooserSource> sources) {
    /** Existing files, one folder, or one file to save to. */
    public enum Mode { OPEN, DIRECTORY, SAVE }

    public PathChoice {
        if ((jasperOwner == null) == (auxiliaryOwner == null)) throw new IllegalArgumentException("Exactly one owner is required");
        Objects.requireNonNull(title); Objects.requireNonNull(initial); Objects.requireNonNull(mode);
        Objects.requireNonNull(terminalFolder); Objects.requireNonNull(suggestedName);
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
        sources = List.copyOf(sources);
    }
}
