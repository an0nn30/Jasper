package dev.jasper.indexer.detect;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Which markers a local folder holds. Blocking file-system reads: call it off the UI thread. */
public final class LocalProbe {
    private LocalProbe() { }

    /**
     * @param folder a folder
     * @param markers the configured markers
     * @return the folder's entries that match a marker; empty when it cannot be read
     */
    public static Set<String> present(Path folder, Markers markers) {
        var found = new TreeSet<String>();
        for (String literal : markers.literals())
            if (Files.exists(folder.resolve(literal))) found.add(literal);
        if (markers.hasGlobs()) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (markers.matchesAny(name)) found.add(name);
                }
            } catch (IOException | DirectoryIteratorException unreadable) {
                // a folder that cannot be listed holds no glob markers
            }
        }
        return found;
    }

    /**
     * @param folder an absolute folder a shell reported
     * @param home the user's home folder
     * @param markers the configured markers
     * @return the folder's project root, or empty
     */
    public static Optional<Detection<Path>> detect(Path folder, Path home, Markers markers) {
        return ProjectDetector.root(ProjectDetector.localAncestors(folder, home), dir -> present(dir, markers), markers);
    }
}
