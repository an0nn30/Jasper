package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** The navigation pane's entries: the terminal's folder and standard folders, then pinned, then recent folders. */
public final class Places {
    /** The pane's groups, in order. */
    public enum Section { PLACES, PINNED, RECENT }

    /**
     * One navigation entry.
     *
     * @param section its group
     * @param label untrusted display text
     * @param icon an {@code AppIcons} catalog id
     * @param folder where it goes
     */
    public record Place(Section section, String label, String icon, Path folder) { }

    private Places() { }

    /**
     * @param isFolder whether a path is an existing folder; missing standard folders and terminal folders are left out
     * @return the entries in display order
     */
    public static List<Place> build(Optional<Path> terminalFolder, Path home, List<Path> pinned, List<Path> recent,
                                    Predicate<Path> isFolder) {
        var places = new ArrayList<Place>();
        terminalFolder.filter(isFolder).ifPresent(folder ->
            places.add(new Place(Section.PLACES, "Terminal folder", "terminal/toolwindow/terminal", folder)));
        places.add(new Place(Section.PLACES, label(home), "nodes/homeFolder", home));
        for (var standard : List.of(Map.entry("Desktop", "nodes/desktop"), Map.entry("Downloads", "general/download"))) {
            Path folder = home.resolve(standard.getKey());
            if (isFolder.test(folder)) places.add(new Place(Section.PLACES, standard.getKey(), standard.getValue(), folder));
        }
        pinned.forEach(folder -> places.add(new Place(Section.PINNED, label(folder), "general/pin", folder)));
        recent.forEach(folder -> places.add(new Place(Section.RECENT, label(folder), "general/history", folder)));
        return List.copyOf(places);
    }

    /** A folder's name, or the whole path for a root such as {@code /} or {@code C:\}. */
    public static String label(Path folder) {
        return folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
    }
}
