package dev.jasper.app.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The navigation pane's entries: the terminal's folder and standard folders, pinned and recent folders, then each
 * source's roots. Folders of sources that are not offered are left out, not forgotten.
 */
public final class Places {
    /** The pane's groups, in order. */
    public enum Section { PLACES, PINNED, RECENT, SOURCE }

    /**
     * One navigation entry.
     *
     * @param heading   the section title
     * @param label     untrusted display text
     * @param icon      a named kit glyph
     * @param location  where it goes
     * @param tooltip   untrusted text
     * @param connected a source root that needs no connection first
     */
    public record Place(Section section, String heading, String label, String icon, Location location, String tooltip, boolean connected) { }

    static final String SERVER_ICON = "server";

    private Places() { }

    /** @param isFolder whether a local path is an existing folder; missing standard and terminal folders are left out */
    public static List<Place> build(Optional<Location> terminalFolder, Path home, List<Location> pinned, List<Location> recent,
                                    List<ChooserSource> sources, Predicate<Path> isFolder) {
        Map<String, ChooserSource> byId = new LinkedHashMap<>();
        for (ChooserSource source : sources) byId.putIfAbsent(source.id(), source);
        var places = new ArrayList<Place>();
        terminalFolder.filter(folder -> folder.isLocal() ? isFolder.test(folder.localPath()) : byId.containsKey(folder.source()))
            .ifPresent(folder -> places.add(new Place(Section.PLACES, "Places", "Terminal folder", "terminal",
                folder, tooltip(folder, byId), false)));
        Location homeFolder = Location.local(home);
        places.add(new Place(Section.PLACES, "Places", label(home), "home", homeFolder, home.toString(), false));
        for (var standard : List.of(Map.entry("Desktop", "desktop"), Map.entry("Downloads", "download"))) {
            Path folder = home.resolve(standard.getKey());
            if (isFolder.test(folder))
                places.add(new Place(Section.PLACES, "Places", standard.getKey(), standard.getValue(), Location.local(folder), folder.toString(), false));
        }
        for (Location folder : pinned)
            if (offered(folder, byId)) places.add(new Place(Section.PINNED, "Pinned", label(folder, byId), "bookmark", folder, tooltip(folder, byId), false));
        for (Location folder : recent)
            if (offered(folder, byId)) places.add(new Place(Section.RECENT, "Recent", label(folder, byId), "history", folder, tooltip(folder, byId), false));
        for (ChooserSource source : byId.values()) {
            if (source.id().equals(Location.LOCAL)) continue;
            for (ChooserSource.Root root : source.roots())
                places.add(new Place(Section.SOURCE, source.heading(), root.label(), SERVER_ICON, Location.home(source.id(), root.id()),
                    root.detail(), root.connected()));
        }
        return List.copyOf(places);
    }

    /** A folder's name, or the whole path for a root such as {@code /} or {@code C:\}. */
    public static String label(Path folder) {
        return folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
    }

    private static boolean offered(Location folder, Map<String, ChooserSource> byId) {
        return folder.isLocal() || byId.containsKey(folder.source());
    }

    private static String rootLabel(Location folder, Map<String, ChooserSource> byId) {
        ChooserSource source = byId.get(folder.source());
        if (source == null) return folder.root();
        return source.roots().stream().filter(root -> root.id().equals(folder.root())).map(ChooserSource.Root::label).findFirst().orElse(folder.root());
    }

    private static String label(Location folder, Map<String, ChooserSource> byId) {
        if (folder.isLocal()) return label(folder.localPath());
        String path = folder.path();
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String name = trimmed.equals("/") ? "/" : trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return name + " \u00B7 " + rootLabel(folder, byId);
    }

    private static String tooltip(Location folder, Map<String, ChooserSource> byId) {
        return folder.isLocal() ? folder.path() : rootLabel(folder, byId) + ":" + folder.path();
    }
}
