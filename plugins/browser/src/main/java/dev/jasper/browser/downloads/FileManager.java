package dev.jasper.browser.downloads;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The Downloads menu's Show and Open (spec section 10, ruling F31), as the File Server shows a folder: Show in Finder,
 * Explorer or Folder shows the file in the system's file manager, through Java's desktop where it can browse to a
 * file, else Explorer selecting it on Windows ({@code /select,} and the path in quotes, two arguments, ruling D21),
 * {@code xdg-open} opening its folder on Linux, and otherwise the desktop opening its folder; Open is the desktop's open
 * action, or {@code xdg-open} on Linux without Java's desktop. A process is started with the path as an argument of its
 * own, never through a shell. Both run on the background executor, as they may
 * block, and nothing runs once it has stopped; a failure is told with its title, "Could not show “NAME”" or "Could not
 * open “NAME”", NAME elided as the menu elides it, and the reason. Any thread.
 */
public final class FileManager {
    private final Executor background;
    private final BiConsumer<String, String> failed;
    private final boolean windows, linux;
    private final Predicate<Desktop.Action> supports;
    private final BiConsumer<Desktop.Action, Path> desktop;
    private final Consumer<List<String>> processes;

    /**
     * The system's file manager.
     *
     * @param background where it shows and opens files: the plugin's background executor
     * @param failed hears a failure's title and reason, on the background executor
     */
    public FileManager(Executor background, BiConsumer<String, String> failed) {
        this(background, failed, System.getProperty("os.name", ""), FileManager::supported, FileManager::desktop, FileManager::start);
    }

    /**
     * A file manager with a test's desktop and processes (ruling F15): no test reaches the desktop or starts a process.
     *
     * @param background where it shows and opens files
     * @param failed hears a failure's title and reason
     * @param osName the {@code os.name} system property
     * @param supports whether Java's desktop exists and does an action
     * @param desktop has Java's desktop do an action on a file: {@code BROWSE_FILE_DIR} or {@code OPEN}; a failure is
     *     thrown, an {@link IOException} as an {@link UncheckedIOException}
     * @param processes starts a command, its program first; a failure is thrown as {@code desktop}'s is
     */
    FileManager(Executor background, BiConsumer<String, String> failed, String osName, Predicate<Desktop.Action> supports,
                BiConsumer<Desktop.Action, Path> desktop, Consumer<List<String>> processes) {
        this.background = Objects.requireNonNull(background, "background");
        this.failed = Objects.requireNonNull(failed, "failed");
        windows = osName.startsWith("Windows");
        linux = osName.startsWith("Linux");
        this.supports = Objects.requireNonNull(supports, "supports");
        this.desktop = Objects.requireNonNull(desktop, "desktop");
        this.processes = Objects.requireNonNull(processes, "processes");
    }

    /**
     * Show in Finder, Explorer or Folder: the file in the system's file manager, selected where the system can select
     * it, on the background executor.
     *
     * @param file the file
     */
    public void reveal(Path file) {
        Objects.requireNonNull(file, "file");
        run(file, "Could not show", () -> {
            if (supports.test(Desktop.Action.BROWSE_FILE_DIR)) desktop.accept(Desktop.Action.BROWSE_FILE_DIR, file);
            // Ruling D21: two arguments, the path quoted, which Java passes as it is: explorer.exe /select, "C:\Users\...".
            else if (windows) processes.accept(List.of("explorer.exe", "/select,", "\"" + file + "\""));
            else if (linux) processes.accept(List.of("xdg-open", folder(file).toString()));
            else if (supports.test(Desktop.Action.OPEN)) desktop.accept(Desktop.Action.OPEN, folder(file));
            else throw new UnsupportedOperationException("This system cannot show files");
        });
    }

    /**
     * Open: the file with the application the system opens it with, on the background executor.
     *
     * @param file the file
     */
    public void open(Path file) {
        Objects.requireNonNull(file, "file");
        run(file, "Could not open", () -> {
            if (supports.test(Desktop.Action.OPEN)) desktop.accept(Desktop.Action.OPEN, file);
            else if (linux) processes.accept(List.of("xdg-open", file.toString()));
            else throw new UnsupportedOperationException("This system cannot open files");
        });
    }

    /** Runs {@code action} on the background executor, telling a failure as {@code verb “NAME”}; nothing once it has stopped. */
    private void run(Path file, String verb, Runnable action) {
        try {
            background.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    Path name = file.getFileName();
                    failed.accept(verb + " \u201C" + DownloadsMenu.elided(name == null ? file.toString() : name.toString()) + "\u201D",
                        reason(failure));
                }
            });
        } catch (RejectedExecutionException stopped) {
            // The plugin has stopped: nothing is shown or opened.
        }
    }

    private static Path folder(Path file) {
        Path parent = file.getParent();
        return parent == null ? file : parent;
    }

    private static String reason(RuntimeException failure) {
        Throwable cause = failure instanceof UncheckedIOException unchecked ? unchecked.getCause() : failure;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static boolean supported(Desktop.Action action) {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
    }

    private static void desktop(Desktop.Action action, Path file) {
        try {
            if (action == Desktop.Action.BROWSE_FILE_DIR) Desktop.getDesktop().browseFileDirectory(file.toFile());
            else Desktop.getDesktop().open(file.toFile());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void start(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
