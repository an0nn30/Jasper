package dev.jasper.app.appearance;

import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.ThemeFileException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * The themes folder's files: a theme file is checked, then copied in under its id, and an imported theme's file can
 * be deleted. Blocking file work: callers run it off the EDT. The folder is created by the first import.
 */
public final class ThemeFolder {
    /** A theme file read and checked: its theme's id and name, whether it replaces an imported theme, and its bytes. */
    public record Candidate(String id, String name, boolean replaces, byte[] bytes) {
        public Candidate { Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    /** Why a file cannot be imported, in words Settings shows after {@code "Could not import <file>: "}. */
    public static final class ImportFailure extends Exception {
        ImportFailure(String message) { super(message); }
    }

    private final Path folder;

    public ThemeFolder(Path folder) { this.folder = Objects.requireNonNull(folder, "folder"); }

    /** The folder's path. */
    public Path folder() { return folder; }

    /**
     * The theme id Jasper gives a theme named {@code name}: Unicode-decomposed, accents dropped, lower-cased, every run
     * of characters other than ASCII letters and digits replaced by {@code -}, leading and trailing {@code -} removed,
     * then cut to 64 characters (and a trailing {@code -} removed). Empty when nothing usable remains.
     */
    public static String id(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "").toLowerCase(Locale.ROOT);
        String words = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return (words.length() > 64 ? words.substring(0, 64) : words).replaceAll("-+$", "");
    }

    /** {@code source} read and checked as a Jasper theme file, ready for {@link #install}. */
    public Candidate prepare(Path source) throws ImportFailure {
        String text;
        byte[] bytes;
        try {
            text = ThemeLibrary.readText(source);
            bytes = Files.readAllBytes(source);
        } catch (IOException failure) {
            throw new ImportFailure(ThemeLibrary.reason(failure));
        }
        JasperTheme theme;
        try {
            theme = JasperTheme.parse(text, "import");
        } catch (ThemeFileException failure) {
            throw new ImportFailure((failure.line() > 0 ? "line " + failure.line() + ": " : "") + failure.detail());
        }
        String id = id(theme.name());
        if (id.isEmpty()) throw new ImportFailure("The theme's name has no letters or digits Jasper can use for a file name");
        var builtIn = JasperTheme.builtIn(id);
        if (builtIn.isPresent()) throw new ImportFailure(builtIn.get().name() + " is built in and can't be replaced");
        return new Candidate(id, theme.name(), Files.exists(file(id)), bytes);
    }

    /** Writes the candidate's bytes to {@code <id>.json} through a temporary file and a move, so a failed write keeps the old file. */
    public void install(Candidate candidate) throws IOException {
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, candidate.id() + "-", ".tmp");
        try {
            Files.write(temporary, candidate.bytes());
            try {
                Files.move(temporary, file(candidate.id()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file(candidate.id()), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Deletes the imported theme {@code id}'s file. */
    public void remove(String id) throws IOException {
        if (!JasperTheme.validId(id) || builtIn(id)) throw new IllegalArgumentException("Not an imported theme: " + id);
        Files.delete(file(id));
    }

    /** Whether {@code id} is a built-in theme's, which no file in the folder replaces or removes. */
    public static boolean builtIn(String id) { return JasperTheme.builtIn(id).isPresent(); }

    private Path file(String id) { return folder.resolve(id + ".json"); }
}
