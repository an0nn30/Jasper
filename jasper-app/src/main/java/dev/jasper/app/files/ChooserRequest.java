package dev.jasper.app.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a chooser is for.
 *
 * @param title the dialog title
 * @param mode open files, save to one file, or choose a folder
 * @param multiple whether Open may select several files
 * @param start where it opens
 * @param terminalFolder where the owner's focused terminal is, listed as "Terminal folder"
 * @param suggestedName Save: the file name to fill in; Open: the file to select
 * @param suffix only files ending with it are listed, ignoring case, for example {@code .zip}
 * @param ownerWindow the Jasper window the chooser belongs to, for sources' sign-in prompts
 */
public record ChooserRequest(String title, Mode mode, boolean multiple, Location start, Optional<Location> terminalFolder,
                             Optional<String> suggestedName, Optional<String> suffix, Optional<UUID> ownerWindow) {
    /** Open existing files, save to one file, or choose one folder. */
    public enum Mode { OPEN, SAVE, DIRECTORY }

    public ChooserRequest {
        Objects.requireNonNull(title); Objects.requireNonNull(mode); Objects.requireNonNull(start);
        Objects.requireNonNull(terminalFolder); Objects.requireNonNull(suggestedName); Objects.requireNonNull(suffix);
        Objects.requireNonNull(ownerWindow);
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
    }

    /**
     * Works out where to open: {@code initial} when given, else the terminal's folder, else {@code home}. A local initial
     * path that is not a folder opens its parent and suggests its name; a source's location is used as given. Stats the
     * local disk, as the native choosers did.
     */
    public static ChooserRequest of(String title, Mode mode, boolean multiple, Optional<Location> initial,
                                    Optional<Location> terminalFolder, Optional<String> suggestedName, Optional<String> suffix,
                                    Optional<UUID> ownerWindow, Path home) {
        Location start = Location.local(home);
        Optional<String> name = suggestedName;
        Optional<Location> wanted = initial.or(() -> terminalFolder);
        if (wanted.isPresent()) {
            Location place = wanted.get();
            if (!place.isLocal()) start = place;
            else {
                Path path = place.localPath();
                if (Files.isDirectory(path)) start = place;
                else {
                    Path parent = path.getParent();
                    if (parent != null && Files.isDirectory(parent)) start = Location.local(parent);
                    if (initial.isPresent() && name.isEmpty() && path.getFileName() != null) name = Optional.of(path.getFileName().toString());
                }
            }
        }
        return new ChooserRequest(title, mode, multiple, start, terminalFolder, name, suffix, ownerWindow);
    }
}
