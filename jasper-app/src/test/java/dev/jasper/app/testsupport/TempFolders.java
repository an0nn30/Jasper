package dev.jasper.app.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Removes the folders a fixture made for itself in {@code java.io.tmpdir}, which no JUnit {@code @TempDir} owns. */
public final class TempFolders {
    private TempFolders() { }

    /** Deletes {@code root} and everything below it, children first; a folder that is already gone is not an error. */
    public static void delete(Path root) {
        if (root == null || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
