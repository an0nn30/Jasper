package dev.jasper.app.appearance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Every theme Jasper can install: the Jasper theme files and theme plugins in the {@code themes} folder
 * beside {@code config.toml}, then the built-in themes. An installed theme takes precedence over a built-in theme of the same name.
 * {@link #scan()} does the file work and may run on any thread; {@link #latest()} may be read anywhere.
 */
public final class ThemeCatalog {
    /** Theme files larger than this are refused. */
    public static final int MAX_THEME_FILE = 256 * 1024;

    /** A problem with one file in the themes folder, shown as a configuration warning; {@code line} is 0 when unknown. */
    public record Problem(Path file, int line, String message) {
        public Problem { Objects.requireNonNull(file); Objects.requireNonNull(message); }
        public Problem(Path file, String message) { this(file, 0, message); }
    }

    /** One scan: the themes in precedence order, and the problems met. */
    public record Snapshot(List<Theme> themes, List<Problem> problems) {
        public Snapshot { themes = List.copyOf(themes); problems = List.copyOf(problems); }

        /** The theme with this name, ignoring case and surrounding whitespace; none for a blank name. */
        public Optional<Theme> find(String name) {
            String key = key(name);
            if (key.isEmpty()) return Optional.empty();
            return themes.stream().filter(theme -> key(theme.name()).equals(key)).findFirst();
        }

        /** Every theme's name, sorted ignoring case. */
        public List<String> names() {
            return themes.stream().map(Theme::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }

    /** What an archive would install: its themes' names, and the problems reading it met. */
    public record Inspection(List<String> themes, List<String> problems) {
        public Inspection { themes = List.copyOf(themes); problems = List.copyOf(problems); }
    }

    private record Stamp(long size, FileTime modified) {}
    private record Read(Stamp stamp, List<Theme> themes, ThemeDocument document, List<Problem> problems) {}

    private static final Snapshot BUILT_IN = new Snapshot(ThemeManager.builtIns(), List.of());

    private final Path directory;
    private final Function<Path, Snapshot> reader;
    /** Each archive's last read, by path; guarded by this. */
    private Map<Path, Read> reads = Map.of();
    /**
     * Each Jasper theme file's theme from the last scan, by path; guarded by this. An unchanged theme keeps its
     * instance, so a configuration reload does not reinstall the look and feel for it.
     */
    private Map<Path, Theme> userThemes = Map.of();
    private volatile Snapshot latest = BUILT_IN;

    ThemeCatalog(Path directory, Function<Path, Snapshot> reader) {
        this.directory = directory;
        this.reader = reader;
    }

    /** Only the built-in themes; nothing is ever scanned. */
    public static ThemeCatalog builtIn() { return new ThemeCatalog(null, null); }

    /** The {@code themes} folder in the directory holding {@code configFile}. */
    public static ThemeCatalog beside(Path configFile) {
        return new ThemeCatalog(configFile.toAbsolutePath().getParent().resolve("themes"), null);
    }

    /** The last scan's result; the built-in themes before the first scan. */
    public Snapshot latest() { return latest; }

    /** The folder this catalog scans; empty for the built-in catalog. */
    public Optional<Path> directory() { return Optional.ofNullable(directory); }

    /** Transitional per-file legacy source, supplied by application composition. */
    public static ThemeCatalog beside(Path configFile, Function<Path, Snapshot> legacyReader) {
        return new ThemeCatalog(configFile.toAbsolutePath().getParent().resolve("themes"), Objects.requireNonNull(legacyReader));
    }

    /** Reads the themes folder, creating it when missing, and re-reads only files whose size or modified time changed. */
    public synchronized Snapshot scan() {
        if (directory == null) return latest;
        var problems = new ArrayList<Problem>();
        var next = new HashMap<Path, Read>();
        var found = new ArrayList<Map.Entry<Path, Read>>();
        for (Path file : files(problems)) {
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (reader != null && lower.endsWith(".theme.json")) {
                problems.add(new Problem(file, "Standalone .theme.json files aren't supported; install the theme plugin's .jar or .zip instead."));
                continue;
            }
            boolean archive = lower.endsWith(".jar") || lower.endsWith(".zip");
            if ((archive && reader == null) || (!archive && !lower.endsWith(".toml"))) continue;
            Read read;
            // Nothing one file does may stop the scan: it becomes that file's problem.
            try { read = read(file, archive); }
            catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
                problems.add(new Problem(file, file.getFileName() + " cannot be read: " + failure));
                continue;
            }
            if (read == null) continue;
            next.put(file, read);
            problems.addAll(read.problems());
            found.add(Map.entry(file, read));
        }
        reads = next;
        // "extends" may name any theme: the folder's, in file order, then the built-in themes.
        var documents = new ArrayList<ThemeDocument>();
        for (var entry : found) {
            if (entry.getValue().document() != null) documents.add(entry.getValue().document());
            entry.getValue().themes().forEach(theme -> documents.add(theme.source().document()));
        }
        ThemeManager.builtIns().forEach(theme -> documents.add(theme.source().document()));
        Function<String, List<ThemeDocument>> candidates =
            name -> documents.stream().filter(document -> key(document.name()).equals(key(name))).toList();
        var installed = new ArrayList<Theme>();
        var owners = new HashMap<String, Path>();
        var nextUserThemes = new HashMap<Path, Theme>();
        for (var entry : found) {
            Path file = entry.getKey();
            List<Theme> provided = entry.getValue().themes();
            ThemeDocument document = entry.getValue().document();
            if (document != null) {
                Theme theme;
                try { theme = Theme.of("user:" + file.getFileName(), new Theme.UserFile(file, ThemeResolver.resolve(document, candidates))); }
                catch (ThemeFileException failure) {
                    // A failure in a theme this file extends carries that file's line; the message names that file.
                    int line = failure.origin().equals(file.getFileName().toString()) ? failure.line() : 0;
                    problems.add(new Problem(file, line, failure.getMessage()));
                    continue;
                }
                // Nothing one file does may stop the scan. The resolver and generator are not known to throw anything
                // else for a parsed document; should they, it becomes this file's problem.
                catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
                    problems.add(new Problem(file, file.getFileName() + " cannot be loaded: " + failure));
                    continue;
                }
                Theme previous = userThemes.get(file);
                if (theme.equals(previous)) theme = previous;
                nextUserThemes.put(file, theme);
                provided = List.of(theme);
            }
            for (Theme theme : provided) {
                Path owner = owners.putIfAbsent(key(theme.name()), file);
                if (owner == null) installed.add(theme);
                else if (owner.equals(file)) problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is provided twice; the first is used."));
                else problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is also installed from " + owner.getFileName() + ", which is used."));
            }
        }
        userThemes = nextUserThemes;
        var themes = new ArrayList<>(installed);
        for (Theme builtIn : ThemeManager.builtIns()) if (!owners.containsKey(key(builtIn.name()))) themes.add(builtIn);
        return latest = new Snapshot(themes, problems);
    }

    /** Regular, non-hidden files in the folder, sorted by name ignoring case. */
    private List<Path> files(List<Problem> problems) {
        try {
            Files.createDirectories(directory);
            try (Stream<Path> listing = Files.list(directory)) {
                return listing.filter(file -> !file.getFileName().toString().startsWith("."))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(file -> file.getFileName().toString()))
                    .toList();
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            problems.add(new Problem(directory, "Cannot read the themes folder: " + failure));
            return List.of();
        }
    }

    /** The file's themes or document, from the last read when its size and modified time are unchanged; null when it vanished. */
    private Read read(Path file, boolean archive) {
        Stamp stamp;
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            stamp = new Stamp(attributes.size(), attributes.lastModifiedTime());
        } catch (IOException | SecurityException failure) {
            return null;
        }
        Read previous = reads.get(file);
        if (previous != null && previous.stamp().equals(stamp)) return previous;
        if (archive) {
            Snapshot loaded = reader.apply(file);
            return new Read(stamp, loaded.themes(), null, loaded.problems());
        }
        String name = file.getFileName().toString();
        if (stamp.size() > MAX_THEME_FILE)
            return new Read(stamp, List.of(), null, List.of(new Problem(file, name + " is larger than 256 KiB.")));
        try {
            return new Read(stamp, List.of(), ThemeToml.parse(Files.readString(file), name), List.of());
        } catch (ThemeFileException failure) {
            return new Read(stamp, List.of(), null, List.of(new Problem(file, failure.line(), failure.getMessage())));
        } catch (IOException failure) {
            return new Read(stamp, List.of(), null, List.of(new Problem(file, name + " cannot be read: " + failure)));
        }
    }

    /** The archive's themes, loaded and resolved, and every problem met; never throws for a bad archive. */
    private static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }
}
