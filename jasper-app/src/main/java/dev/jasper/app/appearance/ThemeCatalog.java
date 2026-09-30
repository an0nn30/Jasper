package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
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
 * Every theme Jasper can install: the plugins in the {@code themes} folder beside {@code config.toml},
 * then the built-in themes. An installed theme takes precedence over a built-in theme of the same name.
 * {@link #scan()} does the file work and may run on any thread; {@link #latest()} may be read anywhere.
 */
public final class ThemeCatalog {
    /** A problem with one file in the themes folder, shown as a configuration warning. */
    public record Problem(Path file, String message) {
        public Problem { Objects.requireNonNull(file); Objects.requireNonNull(message); }
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

    private record Loaded(List<Theme> themes, List<Problem> problems) {}
    private record Stamp(long size, FileTime modified) {}
    private record Read(Stamp stamp, List<Theme> themes, List<Problem> problems) {}

    private static final Snapshot BUILT_IN = new Snapshot(ThemeManager.builtIns(), List.of());

    private final Path directory;
    private final Function<Path, ThemeArchive.Contents> reader;
    /** Each archive's last read, by path; guarded by this. */
    private Map<Path, Read> reads = Map.of();
    private volatile Snapshot latest = BUILT_IN;

    ThemeCatalog(Path directory, Function<Path, ThemeArchive.Contents> reader) {
        this.directory = directory;
        this.reader = Objects.requireNonNull(reader);
    }

    /** Only the built-in themes; nothing is ever scanned. */
    public static ThemeCatalog builtIn() { return new ThemeCatalog(null, ThemeArchive::read); }

    /** The {@code themes} folder in the directory holding {@code configFile}. */
    public static ThemeCatalog beside(Path configFile) {
        return new ThemeCatalog(configFile.toAbsolutePath().getParent().resolve("themes"), ThemeArchive::read);
    }

    /** The last scan's result; the built-in themes before the first scan. */
    public Snapshot latest() { return latest; }

    /** The folder this catalog scans; empty for the built-in catalog. */
    public Optional<Path> directory() { return Optional.ofNullable(directory); }

    /** Reads {@code archive} as a scan would, without installing it; may run on any thread. */
    public static Inspection inspect(Path archive) {
        Loaded loaded;
        try { loaded = load(archive, ThemeArchive::read); }
        catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
            return new Inspection(List.of(), List.of(archive.getFileName() + " cannot be read: " + failure));
        }
        return new Inspection(loaded.themes().stream().map(Theme::name).toList(),
            loaded.problems().stream().map(Problem::message).toList());
    }

    /** Reads the themes folder, creating it when missing, and re-reads only files whose size or modified time changed. */
    public synchronized Snapshot scan() {
        if (directory == null) return latest;
        var problems = new ArrayList<Problem>();
        var next = new HashMap<Path, Read>();
        var installed = new ArrayList<Theme>();
        var owners = new HashMap<String, Path>();
        for (Path file : files(problems)) {
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".theme.json")) {
                problems.add(new Problem(file, "Standalone .theme.json files aren't supported; install the theme plugin's .jar or .zip instead."));
                continue;
            }
            if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) continue;
            Read read;
            // Nothing one file does may stop the scan: it becomes that file's problem.
            try { read = read(file); }
            catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
                problems.add(new Problem(file, file.getFileName() + " cannot be read: " + failure));
                continue;
            }
            if (read == null) continue;
            next.put(file, read);
            problems.addAll(read.problems());
            for (Theme theme : read.themes()) {
                Path owner = owners.putIfAbsent(key(theme.name()), file);
                if (owner == null) installed.add(theme);
                else if (owner.equals(file)) problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is provided twice; the first is used."));
                else problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is also installed from " + owner.getFileName() + ", which is used."));
            }
        }
        reads = next;
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

    /** The archive's themes, from the last read when its size and modified time are unchanged; null when it vanished. */
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
        Loaded loaded = load(file, reader);
        return new Read(stamp, loaded.themes(), loaded.problems());
    }

    /** The archive's themes, loaded and resolved, and every problem met; never throws for a bad archive. */
    private static Loaded load(Path file, Function<Path, ThemeArchive.Contents> reader) {
        ThemeArchive.Contents contents;
        try { contents = reader.apply(file); }
        catch (RuntimeException failure) {
            return new Loaded(List.of(), List.of(new Problem(file, file.getFileName() + " cannot be read: " + failure)));
        }
        var problems = new ArrayList<Problem>();
        contents.problems().forEach(problem -> problems.add(new Problem(file, problem)));
        var themes = new ArrayList<Theme>();
        for (ThemeArchive.Found found : contents.themes()) {
            var source = new Theme.Archive(file, found.label(), found.json());
            ThemeLoader.Resolved resolved;
            try { resolved = ThemeManager.load(source); }
            catch (ThemeLoader.ThemeException failure) { problems.add(new Problem(file, failure.getMessage())); continue; }
            catch (RuntimeException failure) { problems.add(new Problem(file, found.label() + " cannot be loaded: " + failure)); continue; }
            resolved.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
            EditorScheme.Result scheme = scheme(found, resolved.dark());
            scheme.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
            themes.add(new Theme("installed:" + found.label(), resolved.name(), resolved.dark(), scheme.palette(), source));
        }
        return new Loaded(themes, problems);
    }

    private static EditorScheme.Result scheme(ThemeArchive.Found found, boolean dark) {
        if (found.schemeXml() != null) return EditorScheme.read(found.schemeXml(), found.schemeLabel(), dark);
        if (found.schemeName() != null) return EditorScheme.named(found.schemeName(), found.schemeLabel(), dark);
        return new EditorScheme.Result(dark ? Palette.jasperDark() : Palette.jasperLight(), List.of());
    }

    private static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }
}
