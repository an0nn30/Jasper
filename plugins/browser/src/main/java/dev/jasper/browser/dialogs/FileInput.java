package dev.jasper.browser.dialogs;

import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.ui.Windows;
import java.awt.EventQueue;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A page's file input (spec section 5): {@code <input type=file>}, and a save Chromium asks a file for, open Jasper's
 * chooser over the page's item, for local files only, and for a folder when the page asks for one. The page is told
 * once: the chosen paths, or cancelled, as a cancelled chooser, a closed item or a page with nowhere to show it are.
 * The page's accepted types do not narrow the chooser, as Jasper's has no filters.
 */
final class FileInput {
    /** What the page asks for. */
    enum Mode {
        /** One existing file. */
        OPEN,
        /** Existing files. */
        OPEN_MULTIPLE,
        /** One folder. */
        OPEN_FOLDER,
        /** A file to save to. */
        SAVE
    }

    // The longest chooser title Chromium's is shown up to.
    private static final int LONGEST_TITLE = 200;
    private static final System.Logger LOG = System.getLogger(FileInput.class.getName());

    private FileInput() { }

    /**
     * Shows Jasper's chooser over {@code page}'s item, on the UI thread, and tells the page what was chosen. Any thread:
     * Chromium asks on its own.
     *
     * @param page the page that asks, or null for none, which is cancelled at once
     * @param mode what it asks for
     * @param title Chromium's title for the chooser, or blank for the mode's own
     * @param defaultPath where Chromium suggests the chooser starts, or the name it suggests for a save; blank for none
     * @param chosen told the chosen absolute paths, never empty
     * @param cancelled told when nothing was chosen; between the two, exactly one is told, once
     */
    static void choose(Page page, Mode mode, String title, String defaultPath, Consumer<List<String>> chosen, Runnable cancelled) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(chosen, "chosen");
        Objects.requireNonNull(cancelled, "cancelled");
        var told = new AtomicBoolean();
        Runnable cancel = () -> { if (told.compareAndSet(false, true)) cancelled.run(); };
        EventQueue.invokeLater(() -> {
            Optional<DialogPlace> place = page == null ? Optional.empty() : page.dialogPlace();
            if (place.isEmpty()) {
                cancel.run();
                return;
            }
            // The chooser goes with the item's tab, which ends it; the page's close tells the page at once regardless.
            Subscription closing = page.onClose(cancel);
            try {
                List<Path> paths = show(place.get().windows(), place.get().owner(), mode, title(mode, title), initial(mode, defaultPath));
                if (!paths.isEmpty() && told.compareAndSet(false, true)) chosen.accept(paths.stream().map(Path::toString).toList());
            } catch (RuntimeException refused) { // the item's tab closed, or the plugin stops
                LOG.log(System.Logger.Level.DEBUG, "A page's file chooser could not open", refused);
            } finally {
                closing.close();
                cancel.run();
            }
        });
    }

    /** Shows the chooser the mode asks for; blocks, pumping the UI thread's events, until it closes. */
    private static List<Path> show(Windows windows, WindowOwner owner, Mode mode, String title, Optional<Path> initial) {
        return switch (mode) {
            case OPEN, OPEN_MULTIPLE -> windows.choose(new FileChoice(owner, title, FileChoice.Mode.OPEN, mode == Mode.OPEN_MULTIPLE,
                    initial.map(FileLocation.Local::new), Optional.empty(), Set.of())).stream()
                .flatMap(location -> location instanceof FileLocation.Local local ? Stream.of(local.path()) : Stream.empty())
                .toList();
            case OPEN_FOLDER -> windows.chooseDirectory(owner, title, initial).map(List::of).orElse(List.of());
            case SAVE -> windows.chooseSaveFile(owner, title, initial).map(List::of).orElse(List.of());
        };
    }

    /**
     * The chooser's title: Chromium's when it gives one, else the mode's own.
     *
     * @param mode what the page asks for
     * @param title Chromium's title, or blank
     * @return the title, never blank
     */
    static String title(Mode mode, String title) {
        if (title != null && !title.isBlank()) {
            String trimmed = title.strip();
            return trimmed.length() <= LONGEST_TITLE ? trimmed : trimmed.substring(0, LONGEST_TITLE);
        }
        return switch (mode) {
            case OPEN, OPEN_MULTIPLE -> "Open";
            case OPEN_FOLDER -> "Choose Folder";
            case SAVE -> "Save";
        };
    }

    /**
     * Where the chooser starts: Chromium's suggestion when it is an absolute path, and for a save the suggested name in
     * the home folder. Only a suggested name's last part is kept, so a name cannot lead elsewhere.
     *
     * @param mode what the page asks for
     * @param suggested Chromium's default path, or blank
     * @return the start, or empty for the chooser's own
     */
    static Optional<Path> initial(Mode mode, String suggested) {
        if (suggested == null || suggested.isBlank()) return Optional.empty();
        try {
            Path path = Path.of(suggested);
            if (path.isAbsolute()) return Optional.of(path.normalize());
            Path name = path.normalize().getFileName();
            if (mode != Mode.SAVE || name == null || name.toString().isEmpty() || name.toString().equals("..")) return Optional.empty();
            return Optional.of(Path.of(System.getProperty("user.home")).resolve(name.toString()));
        } catch (InvalidPathException unusable) {
            return Optional.empty();
        }
    }
}
