package dev.jasper.app.terminalthemes;

import dev.jasper.app.themeimport.AlacrittyTheme;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.UncheckedIOException;

import dev.jasper.app.config.TerminalColors;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every terminal theme Jasper offers: the user's files in the {@code terminal-themes} folder beside
 * {@code config.toml}, then the bundled alacritty-theme collection. A user file takes precedence over a bundled theme of
 * the same name. {@link #scan()} does the file work and may run on any thread; {@link #latest()} may be read anywhere.
 */
public final class TerminalThemeCatalog {
    /** A problem with one file in the folder, shown as a configuration warning. */
    public record Problem(Path file, String message) {
        public Problem { Objects.requireNonNull(file); Objects.requireNonNull(message); }
    }

    /** One scan: the themes in list order (the user's by name, then bundled by name) and the problems met. */
    public record Snapshot(List<TerminalTheme> themes, List<Problem> problems) {
        public Snapshot { themes = List.copyOf(themes); problems = List.copyOf(problems); }

        /** The theme with this name, ignoring case and surrounding spaces; none for a blank name. */
        public Optional<TerminalTheme> find(String name) {
            String key = key(name);
            if (key.isEmpty()) return Optional.empty();
            return themes.stream().filter(theme -> key(theme.name()).equals(key)).findFirst();
        }

        /** Every theme's name, in list order. */
        public List<String> names() { return themes.stream().map(TerminalTheme::name).toList(); }
    }

    static final String FOLDER = "terminal-themes";
    static final int MAX_FILE = 64 * 1024;

    private record Stamp(long size, FileTime modified) {}
    /** A file's read; a null stamp means it is not remembered and is read again next scan. */
    private record Read(Stamp stamp, TerminalTheme theme, List<String> problems) {}

    private final Path directory;
    /** Guarded by this. */
    private List<TerminalTheme> bundled;
    /** Each file's last read, by path; guarded by this. */
    private Map<Path, Read> reads = Map.of();
    private volatile Snapshot latest;
    /** Converted native values, guarded by this and pruned on every scan. */
    private final Map<TerminalTheme, dev.jasper.app.appearance.Theme> converted = new HashMap<>();

    /** Reuses the native conversion of an unchanged legacy palette. */
    public synchronized dev.jasper.app.appearance.Theme converted(TerminalTheme theme) {
        return converted.computeIfAbsent(theme, AlacrittyConversion::convert);
    }

    private TerminalThemeCatalog(Path directory) { this.directory = directory; }

    /** Only the bundled themes. */
    public static TerminalThemeCatalog bundledOnly() { return new TerminalThemeCatalog(null); }

    /** The {@code terminal-themes} folder in the directory holding {@code configFile}, and the bundled themes. */
    public static TerminalThemeCatalog beside(Path configFile) {
        return new TerminalThemeCatalog(configFile.toAbsolutePath().getParent().resolve(FOLDER));
    }

    /** The folder this catalog scans; empty for {@link #bundledOnly()}. */
    public Optional<Path> directory() { return Optional.ofNullable(directory); }

    /** The last scan's result, scanning first when there has been none. */
    public Snapshot latest() {
        Snapshot current = latest;
        return current != null ? current : scan();
    }

    /** Reads the bundled themes once and the folder (created when missing), re-reading only changed files. */
    public synchronized Snapshot scan() {
        if (bundled == null) bundled = readBundled();
        var problems = new ArrayList<Problem>();
        var mine = new ArrayList<TerminalTheme>();
        var owners = new HashMap<String, Path>();
        var next = new HashMap<Path, Read>();
        for (Path file : files(problems)) {
            Read read;
            try { read = read(file); }
            catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
                read = new Read(null, null, List.of(file.getFileName() + " cannot be read: " + failure));
            }
            if (read == null) continue;
            if (read.stamp() != null) next.put(file, read);
            read.problems().forEach(message -> problems.add(new Problem(file, message)));
            if (read.theme() == null) continue;
            String name = read.theme().name();
            if (TerminalColors.reserved(name)) {
                problems.add(new Problem(file, "\"" + name + "\" is reserved for Jasper's own terminal colours; rename the file."));
                continue;
            }
            Path owner = owners.putIfAbsent(key(name), file);
            if (owner == null) mine.add(read.theme());
            else problems.add(new Problem(file, "Terminal theme \"" + name + "\" is also installed from " + owner.getFileName() + ", which is used."));
        }
        reads = next;
        mine.sort(Comparator.comparing(TerminalTheme::name, String.CASE_INSENSITIVE_ORDER));
        var themes = new ArrayList<>(mine);
        for (TerminalTheme theme : bundled) if (!owners.containsKey(key(theme.name()))) themes.add(theme);
        converted.keySet().retainAll(themes);
        return latest = new Snapshot(themes, problems);
    }

    /** Non-hidden {@code .toml} regular files, sorted by name ignoring case, then by name. */
    private List<Path> files(List<Problem> problems) {
        if (directory == null) return List.of();
        try {
            Files.createDirectories(directory);
            try (Stream<Path> listing = Files.list(directory)) {
                return listing.filter(file -> {
                        String name = file.getFileName().toString();
                        return !name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".toml");
                    })
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(file -> file.getFileName().toString()))
                    .toList();
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            problems.add(new Problem(directory, "Cannot read the terminal-themes folder: " + failure));
            return List.of();
        }
    }

    /** The file's theme, from the last read when its size and modified time are unchanged; null when it vanished. */
    private Read read(Path file) {
        Stamp stamp;
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            stamp = new Stamp(attributes.size(), attributes.lastModifiedTime());
        } catch (IOException | SecurityException failure) {
            return null;
        }
        Read previous = reads.get(file);
        if (previous != null && previous.stamp().equals(stamp)) return previous;
        if (stamp.size() > MAX_FILE) return new Read(stamp, null, List.of(file.getFileName() + " is larger than 64 KiB; skipped."));
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file))).toString(); }
        catch (CharacterCodingException notUtf8) { return new Read(stamp, null, List.of(file.getFileName() + " is not UTF-8 text; skipped.")); }
        catch (IOException | SecurityException failure) {
            // Not remembered (no stamp), so the next scan tries again.
            return new Read(null, null, List.of(file.getFileName() + " cannot be read: " + failure.getMessage()));
        }
        var result = AlacrittyTheme.read(text);
        String name = AlacrittyTheme.displayName(file.getFileName().toString());
        var problems = result.problems().stream().map(problem -> file.getFileName() + ": " + problem).toList();
        if (name.isBlank()) return new Read(stamp, null, List.of(file.getFileName() + " has no usable name; skipped."));
        return new Read(stamp, result.palette().map(palette -> new TerminalTheme(name, palette, Optional.of(file))).orElse(null), problems);
    }

    private static List<TerminalTheme> readBundled() {
        var themes = new ArrayList<TerminalTheme>();
        var seen = new HashSet<String>();
        for (String file : bundledFiles()) {
            var result = AlacrittyTheme.read(bundledText(file));
            String name = AlacrittyTheme.displayName(file);
            if (result.palette().isEmpty() || TerminalColors.reserved(name) || !seen.add(key(name))) continue;
            themes.add(new TerminalTheme(name, result.palette().get(), Optional.empty()));
        }
        themes.sort(Comparator.comparing(TerminalTheme::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(themes);
    }

    private static String key(String name) { return name == null ? "" : name.strip().toLowerCase(Locale.ROOT); }
    private static final String RESOURCES = "/dev/jasper/app/terminalthemes/alacritty/";
    /** The bundled files, from {@code index.txt}. */
    static List<String> bundledFiles() {
        return bundledText("index.txt").lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    /** A bundled file's text. */
    static String bundledText(String file) {
        try (InputStream in = TerminalThemeCatalog.class.getResourceAsStream(RESOURCES + file)) {
            if (in == null) throw new IllegalStateException("Missing bundled terminal theme " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

}
