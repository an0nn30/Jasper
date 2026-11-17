package dev.jasper.editor.associations;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The rows of the File associations section: the built-in file types, then the extensions the user added. */
public final class FileTypeRows {
    /**
     * One row.
     *
     * @param id the row's id: the lower-case name, or {@code user:<ext>}
     * @param name the row's display name
     * @param extensions the row's extensions, normalised
     * @param user whether the user added it
     */
    public record Row(String id, String name, List<String> extensions, boolean user) {
        public Row {
            extensions = List.copyOf(extensions);
        }
    }

    /** The built-in rows, in order. */
    public static final List<Row> BUILT_IN = List.of(
        builtIn("Java", ".java"),
        builtIn("Kotlin", ".kt", ".kts"),
        builtIn("C", ".c", ".h"),
        builtIn("C++", ".cpp", ".cc", ".cxx", ".hpp", ".hh"),
        builtIn("Python", ".py"),
        builtIn("Go", ".go"),
        builtIn("Rust", ".rs"),
        builtIn("JavaScript", ".js", ".mjs"),
        builtIn("Shell", ".sh", ".zsh", ".bash"),
        builtIn("Markdown", ".md", ".markdown"),
        builtIn("JSON", ".json"),
        builtIn("YAML", ".yaml", ".yml"),
        builtIn("TOML", ".toml"),
        builtIn("XML", ".xml"),
        builtIn("Plain text", ".txt", ".log"));

    private static final Pattern VALID = Pattern.compile("\\.[a-z0-9_+-]{1,16}");
    private static final List<String> BROWSER_OWNED = List.of(".html", ".htm");

    private FileTypeRows() { }

    private static Row builtIn(String name, String... extensions) {
        return new Row(name.toLowerCase(Locale.ROOT), name, List.of(extensions), false);
    }

    /**
     * The built-in rows, then one row per distinct user extension, in the order given.
     *
     * @param userExtensions the saved user extensions, normalised
     * @return the rows
     */
    public static List<Row> withUser(List<String> userExtensions) {
        return Stream.concat(BUILT_IN.stream(),
            userExtensions.stream().distinct().map(extension -> new Row("user:" + extension, extension, List.of(extension), true))).toList();
    }

    /**
     * Why a typed extension cannot be added, or empty when it can.
     *
     * @param typed what the user typed
     * @param userExtensions the user's own extensions, normalised
     * @return the error text
     */
    public static Optional<String> addError(String typed, List<String> userExtensions) {
        String extension = normalised(typed);
        if (!VALID.matcher(extension).matches()) {
            return Optional.of("Use a dot and up to 16 letters, digits, _, + or -");
        }
        if (BROWSER_OWNED.contains(extension)) {
            return Optional.of(extension + " opens in Jasper's browser");
        }
        if (userExtensions.contains(extension) || BUILT_IN.stream().anyMatch(row -> row.extensions().contains(extension))) {
            return Optional.of(extension + " is already in the list");
        }
        return Optional.empty();
    }

    /**
     * Lower case, with a leading dot.
     *
     * @param typed what the user typed
     * @return the extension
     */
    public static String normalised(String typed) {
        String extension = typed.strip().toLowerCase(Locale.ROOT);
        return extension.startsWith(".") ? extension : "." + extension;
    }
}
