package dev.jasper.app.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Finds what a fixture leaves in {@code java.io.tmpdir}, for tests that prove a helper deletes its own folders. A test takes
 * {@link #names} before it runs the helper and asks {@link #leftBehind} afterwards; the marker, a name the test made up and
 * put somewhere in the helper's folder, tells the helper's folder from one a build running beside it made.
 */
public final class TempLeaks {
    private TempLeaks() { }

    private static Path tmpdir() { return Path.of(System.getProperty("java.io.tmpdir")); }

    /** The names of the folders and files in the temp folder that start with {@code prefix}. */
    public static Set<String> names(String prefix) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tmpdir(), prefix + "*")) {
            return StreamSupport.stream(entries.spliterator(), false).map(entry -> entry.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /**
     * The folders that start with {@code prefix}, were not in {@code before} and hold a file or folder whose name starts with
     * {@code marker} anywhere below them.
     */
    public static List<Path> leftBehind(Set<String> before, String prefix, String marker) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tmpdir(), prefix + "*")) {
            return StreamSupport.stream(entries.spliterator(), false)
                .filter(entry -> !before.contains(entry.getFileName().toString()) && Files.isDirectory(entry))
                .filter(entry -> holds(entry, marker))
                .toList();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static boolean holds(Path folder, String marker) {
        try (Stream<Path> below = Files.walk(folder)) {
            return below.anyMatch(path -> path.getFileName() != null && path.getFileName().toString().startsWith(marker));
        } catch (IOException | UncheckedIOException gone) {
            // A folder another build removed while it was being read is not a leak.
            return false;
        }
    }
}
