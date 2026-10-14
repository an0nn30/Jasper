package dev.jasper.app.commandline;

import dev.jasper.app.commandline.CommandInstall.Kind;
import dev.jasper.app.commandline.CommandInstall.State;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** The image's {@code bin} folder as an entry of the user's own {@code Path}, which new terminals pick up. */
final class WindowsPathPlacement implements CommandInstall.Placement {
    private final Path folder;
    private final Callable<Optional<String>> read;
    private final Consumer<String> write;

    /**
     * @param folder the image's {@code bin} folder; null when there is none
     * @param read the user's {@code Path}, unexpanded; empty when they have none of their own
     * @param write replaces it, throwing {@link UncheckedIOException}
     */
    WindowsPathPlacement(Path folder, Callable<Optional<String>> read, Consumer<String> write) {
        this.folder = folder;
        this.read = read;
        this.write = write;
    }

    @Override public boolean available() { return folder != null; }

    @Override public State state() {
        if (folder == null) return State.unavailable();
        try {
            return contains(current(), folder) ? new State(Kind.INSTALLED, "Installed: " + folder + " is on your Path") : State.notInstalled();
        } catch (IOException unreadable) {
            return State.notInstalled();
        }
    }

    @Override public void install(boolean replace) throws IOException {
        if (folder == null) throw new IOException("Needs an installed Jasper");
        String path = current();
        if (!contains(path, folder)) store(added(path, folder));
    }

    @Override public void uninstall() throws IOException {
        if (folder == null) return;
        String path = current();
        if (contains(path, folder)) store(removed(path, folder));
    }

    @Override public Optional<String> hint(String pathVariable) { return Optional.of("Open a new terminal to use it"); }

    private String current() throws IOException {
        try {
            return read.call().orElse("");
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Could not read your Path", failure);
        }
    }

    private void store(String value) throws IOException {
        try {
            write.accept(value);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    /** Whether a {@code Path} value names the folder, ignoring case, quotes and a trailing separator. */
    static boolean contains(String path, Path folder) {
        String wanted = entry(folder.toString());
        return Arrays.stream(path.split(";", -1)).anyMatch(each -> entry(each).equals(wanted));
    }

    /** The value with the folder appended, unless it is there already. */
    static String added(String path, Path folder) {
        if (contains(path, folder)) return path;
        if (path.isBlank()) return folder.toString();
        return path.endsWith(";") ? path + folder : path + ";" + folder;
    }

    /** The value without every entry that names the folder. */
    static String removed(String path, Path folder) {
        String wanted = entry(folder.toString());
        return Arrays.stream(path.split(";", -1)).filter(each -> !entry(each).equals(wanted)).collect(Collectors.joining(";"));
    }

    private static String entry(String value) {
        String text = value.strip();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        while (text.endsWith("\\") || text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text.toLowerCase(Locale.ROOT);
    }
}
