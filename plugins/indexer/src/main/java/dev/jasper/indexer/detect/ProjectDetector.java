package dev.jasper.indexer.detect;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The root rule: the nearest ancestor holding a strong marker, else the nearest holding a weak one. Callers build the
 * ancestor list so that the home folder and the file-system root are never candidates.
 */
public final class ProjectDetector {
    private ProjectDetector() { }

    /**
     * Finds a folder's project root.
     *
     * @param ancestors the folder and its ancestors, nearest first, without home and the file-system root
     * @param present the marker entries one folder holds; called once per folder
     * @param markers the configured markers
     * @param <F> how folders are named
     * @return the root, or empty when no ancestor holds a marker
     */
    public static <F> Optional<Detection<F>> root(List<F> ancestors, Function<F, Set<String>> present, Markers markers) {
        var seen = new ArrayList<Set<String>>(ancestors.size());
        for (F folder : ancestors) {
            Set<String> names = present.apply(folder);
            seen.add(names);
            Optional<Markers.Match> strong = markers.strongMatch(names);
            if (strong.isPresent()) return Optional.of(new Detection<>(folder, strong.get()));
        }
        for (int i = 0; i < ancestors.size(); i++) {
            Optional<Markers.Match> weak = markers.weakMatch(seen.get(i));
            if (weak.isPresent()) return Optional.of(new Detection<>(ancestors.get(i), weak.get()));
        }
        return Optional.empty();
    }

    /**
     * A local folder and its ancestors, nearest first, stopping before the home folder and the file-system root.
     *
     * @param folder an absolute folder
     * @param home the user's home folder
     * @return the candidates; empty for home itself and for the root
     */
    public static List<Path> localAncestors(Path folder, Path home) {
        var result = new ArrayList<Path>();
        Path stop = home.toAbsolutePath().normalize();
        for (Path at = folder.toAbsolutePath().normalize(); at != null && at.getParent() != null && !at.equals(stop); at = at.getParent())
            result.add(at);
        return result;
    }
}
