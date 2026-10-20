package dev.jasper.sdk.files;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What {@link dev.jasper.sdk.ui.Windows#choose(FileChoice)} asks for.
 *
 * @param owner         a live terminal window, a shown window of this plugin, or one of its content tabs
 * @param title         the dialog title
 * @param mode          open files, save to one file, or choose a folder
 * @param multiple      whether Open may select several files; only with {@link Mode#OPEN}
 * @param initial       where to open; empty opens at the owner window's focused terminal pane, or, for a plugin window
 *                      or a window with no focused terminal pane, the focused pane of the terminal window used last: a
 *                      source's location when one of {@code sources} recognises that pane, else the pane's local
 *                      folder; else the home folder
 * @param suggestedName Save: the name filled in
 * @param sources       ids of the sources whose locations the caller handles; their roots are offered, others are not;
 *                      empty offers local places only
 * @since 0.10.0
 */
public record FileChoice(WindowOwner owner, String title, Mode mode, boolean multiple, Optional<FileLocation> initial,
                         Optional<String> suggestedName, Set<String> sources) {
    /** What the chooser picks. */
    public enum Mode {
        /** Existing files. */
        OPEN,
        /** One file to save to; replacing an existing file asks first. */
        SAVE,
        /** One folder. */
        DIRECTORY
    }

    /** Rejects nulls, a blank title and {@code multiple} outside Open. */
    public FileChoice {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(suggestedName, "suggestedName");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A chooser needs a title");
        if (multiple && mode != Mode.OPEN) throw new IllegalArgumentException("Only Open selects several files");
        sources = Set.copyOf(sources);
    }
}
