package dev.jasper.app.appearance;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.theme.ThemeResolver;
import dev.jasper.ui.theme.ThemeFileException;
import dev.jasper.ui.theme.ThemeDocument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
 * Every theme Jasper can install: the native Jasper theme files in the {@code themes} folder
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

    private record Read(String fingerprint, List<Theme> themes, ThemeDocument document, List<Problem> problems) {}

    private static final Snapshot BUILT_IN = new Snapshot(ThemeManager.builtIns(), List.of());

    private final Path directory;
    /** Each native file's last read, by path; guarded by this. */
    private Map<Path, Read> reads = Map.of();
    /**
     * Each Jasper theme file's theme from the last scan, by path; guarded by this. An unchanged theme keeps its
     * instance, so a configuration reload does not reinstall the look and feel for it.
     */
    private Map<Path, Theme> userThemes = Map.of();
    private volatile Snapshot latest = BUILT_IN;

    ThemeCatalog(Path directory) {
        this.directory = directory;
    }

    /** Only the built-in themes; nothing is ever scanned. */
    public static ThemeCatalog builtIn() { return new ThemeCatalog(null); }

    /** The {@code themes} folder in the directory holding {@code configFile}. */
    public static ThemeCatalog beside(Path configFile) {
        return new ThemeCatalog(configFile.toAbsolutePath().getParent().resolve("themes"));
    }

    /** The last scan's result; the built-in themes before the first scan. */
    public Snapshot latest() { return latest; }

    /** The folder this catalog scans; empty for the built-in catalog. */
    public Optional<Path> directory() { return Optional.ofNullable(directory); }

    /** Reads bounded native content, creating the folder when missing; unchanged fingerprints reuse parsed documents. */
    public synchronized Snapshot scan() {
        if (directory == null) return latest;
        var problems = new ArrayList<Problem>();
        var next = new HashMap<Path, Read>();
        var found = new ArrayList<Map.Entry<Path, Read>>();
        for (Path file : files(problems)) {
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".toml")) continue;
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
            found.add(Map.entry(file, read));
        }
        reads = next;
        // "extends" may name any theme: the folder's, in file order, then the built-in themes.
        var documents = new ArrayList<ThemeDocument>();
        for (var entry : found) {
            if (entry.getValue().document() != null) documents.add(entry.getValue().document());
            entry.getValue().themes().forEach(theme -> documents.add(theme.source().document()));
        }
        // A built-in theme is matched by name before its document is read, which parses its whole file.
        Function<String, List<ThemeDocument>> candidates = name -> Stream.concat(
            documents.stream().filter(document -> key(document.name()).equals(key(name))),
            ThemeManager.builtIns().stream().filter(theme -> key(theme.name()).equals(key(name))).map(theme -> theme.source().document())).toList();
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

    /** Reads bounded bytes once, reusing parsed content even across timestamp-only edits. */
    private Read read(Path file) {
        String name = file.getFileName().toString();
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) return null;
            if (attributes.size() > MAX_THEME_FILE) return oversized(file, name);
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_THEME_FILE + 1); }
            if (bytes.length > MAX_THEME_FILE) return oversized(file, name);
            String fingerprint;
            try { fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            Read previous = reads.get(file);
            if (previous != null && fingerprint.equals(previous.fingerprint())) return previous;
            try {
                String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                return new Read(fingerprint, List.of(), ThemeToml.parse(text, name), List.of());
            } catch (ThemeFileException failure) {
                return new Read(fingerprint, List.of(), null, List.of(new Problem(file, failure.line(), failure.getMessage())));
            }
        } catch (java.nio.file.NoSuchFileException vanished) { return null; }
        catch (IOException | SecurityException failure) {
            return new Read(null, List.of(), null, List.of(new Problem(file, name + " cannot be read: " + failure)));
        }
    }

    private static Read oversized(Path file, String name) {
        return new Read(null, List.of(), null, List.of(new Problem(file, name + " is larger than 256 KiB.")));
    }

    private static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }
}
