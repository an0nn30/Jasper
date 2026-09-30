package dev.jasper.app.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What a chooser is for.
 *
 * @param title the dialog title
 * @param mode open files, save to one file, or choose a folder
 * @param multiple whether Open may select several files
 * @param start the folder it opens in
 * @param terminalFolder the focused terminal's folder, listed as "Terminal folder"
 * @param suggestedName Save: the file name to fill in; Open: the file to select
 * @param suffix only files ending with it are listed, ignoring case, for example {@code .zip}
 */
public record ChooserRequest(String title, Mode mode, boolean multiple, Path start, Optional<Path> terminalFolder,
                             Optional<String> suggestedName, Optional<String> suffix) {
    /** Open existing files, save to one file, or choose one folder. */
    public enum Mode { OPEN, SAVE, DIRECTORY }

    public ChooserRequest {
        Objects.requireNonNull(title); Objects.requireNonNull(mode); Objects.requireNonNull(start);
        Objects.requireNonNull(terminalFolder); Objects.requireNonNull(suggestedName); Objects.requireNonNull(suffix);
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
        start = start.toAbsolutePath().normalize();
        terminalFolder = terminalFolder.map(path -> path.toAbsolutePath().normalize());
    }

    /**
     * Works out where to open: {@code initial} when given, else the terminal's folder, else {@code home}. An initial
     * path that is not a folder opens its parent and suggests its name. Stats the disk, as the native choosers did.
     */
    public static ChooserRequest of(String title, Mode mode, boolean multiple, Optional<Path> initial,
                                    Optional<Path> terminalFolder, Optional<String> suffix, Path home) {
        Path start = home;
        Optional<String> name = Optional.empty();
        Optional<Path> wanted = initial.or(() -> terminalFolder).map(path -> path.toAbsolutePath().normalize());
        if (wanted.isPresent()) {
            Path path = wanted.get();
            if (Files.isDirectory(path)) start = path;
            else {
                Path parent = path.getParent();
                if (parent != null && Files.isDirectory(parent)) start = parent;
                if (initial.isPresent() && path.getFileName() != null) name = Optional.of(path.getFileName().toString());
            }
        }
        return new ChooserRequest(title, mode, multiple, start, terminalFolder, name, suffix);
    }
}
