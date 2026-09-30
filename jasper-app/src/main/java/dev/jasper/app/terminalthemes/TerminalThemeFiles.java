package dev.jasper.app.terminalthemes;

import dev.jasper.app.config.TerminalColors;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Writes the terminal-themes folder: installs a theme file from disk after checking it, and removes the user's own
 * files. The files are small; synchronized; any thread.
 */
final class TerminalThemeFiles {
    /** A refused or failed operation; the message is for the user. */
    static class ThemeFileException extends Exception {
        ThemeFileException(String message) { super(message); }
        ThemeFileException(String message, Throwable cause) { super(message, cause); }
    }

    /** An install whose file name is taken; ask, then install again with {@code replace}. */
    static final class Conflict extends ThemeFileException {
        private final Path existing;
        Conflict(Path existing) { super(existing.getFileName() + " is already installed."); this.existing = existing; }
        Path existing() { return existing; }
    }

    private final Path folder;

    TerminalThemeFiles(Path folder) { this.folder = folder.toAbsolutePath().normalize(); }

    Path folder() { return folder; }

    /** Checks {@code source} is a usable theme with a name Jasper allows, then copies it in; returns its name. */
    synchronized String install(Path source, boolean replace) throws ThemeFileException {
        String fileName = source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".toml")) throw new ThemeFileException(fileName + " is not a .toml theme file.");
        if (fileName.startsWith(".")) fileName = "_" + fileName.substring(1);
        String name = AlacrittyTheme.displayName(fileName);
        if (name.isBlank()) throw new ThemeFileException(fileName + " has no usable name.");
        if (TerminalColors.reserved(name))
            throw new ThemeFileException("\"" + name + "\" is reserved for Jasper's own terminal colours; rename the file.");
        String text;
        try {
            if (Files.size(source) > TerminalThemeCatalog.MAX_FILE) throw new ThemeFileException(fileName + " is larger than 64 KiB.");
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new ThemeFileException("Cannot read " + fileName + ": " + failure.getMessage(), failure);
        }
        AlacrittyTheme.Result result;
        try { result = AlacrittyTheme.read(text); }
        catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
            throw new ThemeFileException(name + " cannot be used: it cannot be read as TOML.", failure);
        }
        if (result.palette().isEmpty()) throw new ThemeFileException(name + " cannot be used: " + result.problems().getFirst());
        Path target = folder.resolve(fileName);
        if (Files.exists(target) && !replace) throw new Conflict(target);
        Path temp = folder.resolve("." + fileName + ".part");
        try {
            Files.createDirectories(folder);
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return name;
        } catch (IOException failure) {
            throw new ThemeFileException("Cannot install " + fileName + ": " + failure.getMessage(), failure);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    /** Deletes one of the user's theme files. */
    synchronized void remove(Path file) throws ThemeFileException {
        Path absolute = file.toAbsolutePath().normalize();
        String name = absolute.getFileName().toString();
        if (!folder.equals(absolute.getParent()) || name.startsWith(".") || !name.toLowerCase(Locale.ROOT).endsWith(".toml")
                || Files.exists(absolute, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(absolute))
            throw new ThemeFileException(name + " is not one of your terminal themes.");
        try { Files.deleteIfExists(absolute); }
        catch (IOException failure) { throw new ThemeFileException("Cannot remove " + name + ": " + failure.getMessage(), failure); }
    }
}
