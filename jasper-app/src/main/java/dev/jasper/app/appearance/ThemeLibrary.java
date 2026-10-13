package dev.jasper.app.appearance;

import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SchemeId;
import dev.jasper.ui.theme.TerminalScheme;
import dev.jasper.ui.theme.ThemeFileException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The installed themes: the built-in Slate Steel and every valid {@code <home>/themes/<id>.json}. Imported themes are
 * ordered by display name (ignoring case), then id. A file that cannot be used is skipped and kept as a problem.
 * Immutable and thread-safe; {@link #read} builds a new library, reusing the parsed themes of unchanged files.
 */
public final class ThemeLibrary {
    /** A file the library skipped: where it is, the 1-based line when known (else 0), and what is wrong. */
    public record Problem(Path file, int line, String message) {
        public Problem { Objects.requireNonNull(file, "file"); Objects.requireNonNull(message, "message"); }
    }

    /** Theme files are at most 1 MiB. */
    public static final int MAX_BYTES = 1 << 20;
    private static final ThemeLibrary BUNDLED = new ThemeLibrary(List.of(), Map.of(), List.of());

    private final List<JasperTheme> imported, themes;
    private final Map<String, String> texts;
    private final List<Problem> problems;
    private final List<TerminalScheme> schemes;
    private final Map<String, String> themeLabels = new HashMap<>(), schemeLabels = new HashMap<>();

    private ThemeLibrary(List<JasperTheme> imported, Map<String, String> texts, List<Problem> problems) {
        this.imported = List.copyOf(imported);
        this.texts = Map.copyOf(texts);
        this.problems = List.copyOf(problems);
        var all = new ArrayList<JasperTheme>();
        all.add(JasperTheme.bundled());
        all.addAll(this.imported);
        themes = List.copyOf(all);
        var allSchemes = new ArrayList<TerminalScheme>();
        for (var theme : themes) allSchemes.addAll(theme.schemes());
        schemes = List.copyOf(allSchemes);
        var names = new HashMap<String, Integer>();
        for (var theme : themes) names.merge(theme.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        for (var theme : themes)
            themeLabels.put(theme.id(), names.get(theme.name().toLowerCase(Locale.ROOT)) > 1 ? theme.name() + " (" + theme.id() + ")" : theme.name());
        var schemeNames = new HashMap<String, Integer>();
        for (var scheme : schemes) schemeNames.merge(scheme.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
        for (var scheme : schemes)
            schemeLabels.put(scheme.id().toString(),
                schemeNames.get(scheme.name().toLowerCase(Locale.ROOT)) > 1 ? scheme.name() + " (" + scheme.id() + ")" : scheme.name());
    }

    /** Only the built-in theme. */
    public static ThemeLibrary bundledOnly() { return BUNDLED; }

    /**
     * The themes in {@code folder}: every regular {@code *.json} file directly in it (a missing folder has none).
     * {@code previous}'s parsed theme is reused for a file whose text is unchanged, and {@code previous} itself is
     * returned when nothing changed, so an unchanged folder never reinstalls a theme. Never throws for a bad file.
     */
    public static ThemeLibrary read(Path folder, ThemeLibrary previous) {
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(previous, "previous");
        var files = new ArrayList<Path>();
        var problems = new ArrayList<Problem>();
        if (Files.isDirectory(folder)) {
            try (var listing = Files.list(folder)) {
                listing.filter(path -> path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).forEach(files::add);
            } catch (IOException failure) {
                problems.add(new Problem(folder, 0, "Could not list the themes folder: " + reason(failure)));
            } catch (UncheckedIOException failure) {
                problems.add(new Problem(folder, 0, "Could not list the themes folder: " + reason(failure.getCause())));
            }
        }
        var imported = new ArrayList<JasperTheme>();
        var texts = new LinkedHashMap<String, String>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - ".json".length());
            if (id.equals(JasperTheme.BUNDLED_ID)) {
                problems.add(new Problem(file, 0, "Slate Steel is built in; this file is ignored."));
                continue;
            }
            if (!JasperTheme.validId(id)) {
                problems.add(new Problem(file, 0, "A theme file is named after its id: lowercase letters, digits and dashes, at most 64 characters."));
                continue;
            }
            String text;
            try { text = readText(file); }
            catch (IOException failure) { problems.add(new Problem(file, 0, reason(failure))); continue; }
            var reused = previous.theme(id).filter(theme -> text.equals(previous.texts.get(id)));
            try {
                imported.add(reused.orElseGet(() -> JasperTheme.parse(text, id)));
                texts.put(id, text);
            } catch (ThemeFileException failure) {
                problems.add(new Problem(file, failure.line(), failure.detail()));
            }
        }
        imported.sort(Comparator.comparing((JasperTheme theme) -> theme.name().toLowerCase(Locale.ROOT)).thenComparing(JasperTheme::id));
        if (sameThemes(imported, previous.imported) && problems.equals(previous.problems)) return previous;
        return new ThemeLibrary(imported, texts, problems);
    }

    private static boolean sameThemes(List<JasperTheme> next, List<JasperTheme> previous) {
        if (next.size() != previous.size()) return false;
        for (int i = 0; i < next.size(); i++) if (next.get(i) != previous.get(i)) return false;
        return true;
    }

    /** The file's text, read as UTF-8 without a leading byte-order mark; larger than {@link #MAX_BYTES} or not UTF-8 is an error. */
    static String readText(Path file) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) { bytes = in.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Theme files are at most 1 MiB.");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException failure) {
            throw new IOException("A theme file is UTF-8 text.");
        }
    }

    /** Why a file could not be read: the reader's own reasons as they are, otherwise what failed, never the bare path. */
    static String reason(IOException failure) {
        if (failure instanceof AccessDeniedException) return "Could not read this file: permission denied.";
        if (failure instanceof NoSuchFileException) return "Could not read this file: it disappeared while being read.";
        if (failure instanceof FileSystemException system)
            return "Could not read this file" + (system.getReason() == null ? " (" + system.getClass().getSimpleName() + ")" : ": " + system.getReason()) + ".";
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "Could not read this file (" + failure.getClass().getSimpleName() + ")." : message;
    }

    /** Slate Steel, then the imported themes in order. */
    public List<JasperTheme> themes() { return themes; }
    /** The imported themes, in order. */
    public List<JasperTheme> imported() { return imported; }
    /** The themes that have the Dark or the Light variant, Slate Steel first. */
    public List<JasperTheme> themes(boolean dark) { return themes.stream().filter(theme -> theme.has(dark)).toList(); }
    /** The theme with this id. */
    public Optional<JasperTheme> theme(String id) { return themes.stream().filter(theme -> theme.id().equals(id)).findFirst(); }
    /** Every theme's terminal schemes: Slate Steel's, then each imported theme's, in the files' order. */
    public List<TerminalScheme> schemes() { return schemes; }
    /** The scheme with this id. */
    public Optional<TerminalScheme> scheme(SchemeId id) { return schemes.stream().filter(scheme -> scheme.id().equals(id)).findFirst(); }
    /** The files that were skipped, in file-name order. */
    public List<Problem> problems() { return problems; }
    /** A theme's name, followed by its id in parentheses when another theme has the same name. */
    public String label(JasperTheme theme) { return themeLabels.getOrDefault(theme.id(), theme.name()); }
    /** A scheme's name, followed by its id in parentheses when another scheme has the same name. */
    public String label(TerminalScheme scheme) { return schemeLabels.getOrDefault(scheme.id().toString(), scheme.name()); }
}
