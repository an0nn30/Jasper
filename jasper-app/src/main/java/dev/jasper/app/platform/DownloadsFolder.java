package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The system's Downloads folder ({@code Platform.downloadsFolder}): {@code ~/Downloads} on macOS, the Downloads known
 * folder on Windows, {@code XDG_DOWNLOAD_DIR} on Linux and other Unix systems. Finding it creates nothing.
 */
public final class DownloadsFolder {
    private static final System.Logger LOG = System.getLogger(DownloadsFolder.class.getName());
    private static final String KEY = "XDG_DOWNLOAD_DIR";
    private static final String HOME = "$HOME";
    /** {@code user-dirs.dirs} is a few lines; a longer file is read only this far. */
    private static final int MOST_READ = 64 * 1024;

    private DownloadsFolder() { }

    /**
     * This process's Downloads folder, found at the first call, on the calling thread, and the same from then on: only
     * that call reads {@code user-dirs.dirs} or asks Windows for the known folder. Any thread.
     *
     * @return the folder, an absolute path that may not exist
     */
    public static Path current() { return Current.FOLDER; }

    /** The holder idiom: the class initializes, once, at the first {@link #current} (browser plan 7's F3). */
    private static final class Current {
        static final Path FOLDER = of(System.getProperty("os.name", ""), Path.of(System.getProperty("user.home", "")),
            System.getenv(), DownloadsFolder::read, DownloadsFolder::knownFolder);
    }

    /**
     * The Downloads folder for a system, without reaching it: what {@link #current} computes.
     *
     * @param osName the {@code os.name} property
     * @param home the user's home folder
     * @param env the environment ({@code XDG_CONFIG_HOME} on Linux, {@code USERPROFILE} on Windows)
     * @param read a file's text, or empty when it cannot be read; asked only for {@code user-dirs.dirs}, on Linux and other
     *     Unix systems
     * @param knownFolder Windows' Downloads known folder, or empty when it is unavailable; asked only on Windows
     * @return the folder, an absolute path: on macOS {@code home/Downloads}; on Windows the known folder, else
     *     {@code %USERPROFILE%\Downloads}, else {@code home\Downloads}; elsewhere {@code XDG_DOWNLOAD_DIR} from
     *     {@code user-dirs.dirs} in {@code XDG_CONFIG_HOME} when that is absolute, else in {@code home/.config}, else
     *     {@code home/Downloads}
     */
    public static Path of(String osName, Path home, Map<String, String> env, Function<Path, Optional<String>> read,
                          Supplier<Optional<Path>> knownFolder) {
        Path user = home.toAbsolutePath();
        Path fallback = user.resolve("Downloads");
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) return fallback;
        if (os.startsWith("windows")) {
            return asked(knownFolder).filter(Path::isAbsolute)
                .or(() -> absolute(env.get("USERPROFILE")).map(profile -> profile.resolve("Downloads")))
                .orElse(fallback);
        }
        Path config = absolute(env.get("XDG_CONFIG_HOME")).orElse(user.resolve(".config"));
        Optional<String> text;
        try {
            text = read.apply(config.resolve("user-dirs.dirs"));
        } catch (RuntimeException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not read user-dirs.dirs", unreadable);
            text = Optional.empty();
        }
        return text.flatMap(userDirs -> downloadDir(userDirs, user)).orElse(fallback);
    }

    /**
     * {@code XDG_DOWNLOAD_DIR} in a {@code user-dirs.dirs} file, read as xdg-user-dirs' own lookup reads it: the last line
     * {@code XDG_DOWNLOAD_DIR="$HOME/…"} or {@code XDG_DOWNLOAD_DIR="/…"} that parses, blanks around {@code =}, a backslash
     * escaping the next character, trailing slashes dropped. A comment line, and a value that is not quoted, not closed,
     * relative or empty, is ignored.
     */
    static Optional<Path> downloadDir(String userDirs, Path home) {
        Optional<Path> found = Optional.empty();
        for (String raw : userDirs.split("\n")) {
            String line = raw.strip();
            if (!line.startsWith(KEY)) continue;
            String rest = line.substring(KEY.length()).stripLeading();
            if (!rest.startsWith("=")) continue;
            rest = rest.substring(1).stripLeading();
            if (!rest.startsWith("\"")) continue;
            Optional<String> value = quoted(rest);
            if (value.isEmpty()) continue;
            Optional<Path> folder = folder(value.get(), home);
            if (folder.isPresent()) found = folder;
        }
        return found;
    }

    /** The text between {@code text}'s opening quote and the next unescaped quote, unescaped; empty when it is not closed. */
    private static Optional<String> quoted(String text) {
        StringBuilder value = new StringBuilder();
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') return Optional.of(value.toString());
            if (c == '\\' && i + 1 < text.length()) c = text.charAt(++i);
            value.append(c);
        }
        return Optional.empty();
    }

    /** A value's folder: {@code $HOME} alone or followed by {@code /}, or an absolute path; trailing slashes dropped. */
    private static Optional<Path> folder(String value, Path home) {
        String trimmed = value;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        try {
            if (trimmed.equals(HOME)) return Optional.of(home);
            if (trimmed.startsWith(HOME + "/")) {
                // "$HOME//x" is home's x, as the lookup's concatenation gives it, never the root's.
                String below = trimmed.substring(HOME.length());
                while (below.startsWith("/")) below = below.substring(1);
                return Optional.of(below.isEmpty() ? home : home.resolve(below));
            }
            if (trimmed.startsWith("/")) return Optional.of(Path.of(trimmed)).filter(Path::isAbsolute);
        } catch (InvalidPathException invalid) {
            LOG.log(System.Logger.Level.DEBUG, "Ignored an XDG_DOWNLOAD_DIR that is no path", invalid);
        }
        return Optional.empty();
    }

    /** An environment variable's absolute path; empty when it is unset, empty, relative or no path. */
    private static Optional<Path> absolute(String value) {
        if (value == null || value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Path.of(value)).filter(Path::isAbsolute);
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<Path> asked(Supplier<Optional<Path>> knownFolder) {
        try {
            return knownFolder.get();
        } catch (RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Could not find the Downloads known folder", unavailable);
            return Optional.empty();
        }
    }

    /** At most {@link #MOST_READ} bytes of a file as UTF-8; empty when it does not exist or cannot be read. */
    private static Optional<String> read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return Optional.of(new String(in.readNBytes(MOST_READ), StandardCharsets.UTF_8));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "Could not read " + file, unreadable);
            return Optional.empty();
        }
    }

    /** Windows' {@code FOLDERID_Downloads}, through {@code SHGetKnownFolderPath}; Windows only. */
    private static Optional<Path> knownFolder() {
        try {
            return Optional.of(Path.of(com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(
                com.sun.jna.platform.win32.KnownFolders.FOLDERID_Downloads)));
        } catch (RuntimeException | LinkageError unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Could not find the Downloads known folder", unavailable);
            return Optional.empty();
        }
    }
}
