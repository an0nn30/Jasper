package dev.jasper.app.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Opens the settings file on the configuration worker, with progressively broader OS fallbacks. */
public final class ConfigEditor {
    private final List<Consumer<Path>> attempts;

    public ConfigEditor() {
        this(path -> desktop(Desktop.Action.EDIT, path), path -> desktop(Desktop.Action.OPEN, path),
            path -> desktop(Desktop.Action.BROWSE_FILE_DIR, path));
    }

    public ConfigEditor(Consumer<Path> edit, Consumer<Path> open, Consumer<Path> reveal) {
        attempts = List.of(edit, open, reveal);
    }

    public void open(Path path) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
        var failure = new IllegalStateException("Could not open or reveal configuration: " + path);
        for (Consumer<Path> attempt : attempts) {
            try { attempt.accept(path); return; }
            catch (RuntimeException exception) { failure.addSuppressed(exception); }
        }
        throw failure;
    }

    private static void desktop(Desktop.Action action, Path path) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(action))
            throw new UnsupportedOperationException("Desktop action is unavailable: " + action);
        try {
            switch (action) {
                case EDIT -> Desktop.getDesktop().edit(path.toFile());
                case OPEN -> Desktop.getDesktop().open(path.toFile());
                case BROWSE_FILE_DIR -> Desktop.getDesktop().browseFileDirectory(path.toFile());
                default -> throw new IllegalArgumentException("Unsupported editor action");
            }
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

        /** Reveals a directory in the file manager, else opens it. Never on the EDT. */
        public void reveal(Path path) {
            if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
            var failure = new IllegalStateException("Could not reveal: " + path);
            for (Consumer<Path> attempt : List.of(attempts.get(2), attempts.get(1))) {
                try { attempt.accept(path); return; }
                catch (RuntimeException exception) { failure.addSuppressed(exception); }
            }
            throw failure;
        }
}
