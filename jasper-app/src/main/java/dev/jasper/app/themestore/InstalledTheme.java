package dev.jasper.app.themestore;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** One archive in the themes folder as the Installed tab shows it: a title, its themes, its source and its problems. */
record InstalledTheme(Path file, String title, List<String> themes, Optional<Provenance.Entry> source, List<String> problems) {
    InstalledTheme { themes = List.copyOf(themes); problems = List.copyOf(problems); }

    /** The installed version; empty for a disk install. */
    String version() { return source.map(Provenance.Entry::version).orElse(""); }

    /** "Marketplace" or "From disk". */
    String origin() { return source.isPresent() ? "Marketplace" : "From disk"; }

    /** Every archive in {@code folder} the snapshot provides themes from or has problems with, by file name ignoring case. */
    static List<InstalledTheme> of(ThemeCatalog.Snapshot snapshot, Path folder, Map<String, Provenance.Entry> sources) {
        var themes = new LinkedHashMap<Path, List<String>>();
        var problems = new LinkedHashMap<Path, List<String>>();
        for (Theme theme : snapshot.themes())
            if (theme.source() instanceof Theme.Archive archive && inFolder(archive.archive(), folder))
                themes.computeIfAbsent(archive.archive(), key -> new ArrayList<>()).add(theme.name());
        for (ThemeCatalog.Problem problem : snapshot.problems())
            if (inFolder(problem.file(), folder)) problems.computeIfAbsent(problem.file(), key -> new ArrayList<>()).add(problem.message());
        var files = new TreeSet<Path>(Comparator.comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(file -> file.getFileName().toString()));
        files.addAll(themes.keySet());
        files.addAll(problems.keySet());
        var result = new ArrayList<InstalledTheme>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            var source = Optional.ofNullable(sources.get(name));
            String title = source.map(Provenance.Entry::name).orElse(name.substring(0, Math.max(1, name.lastIndexOf('.'))));
            result.add(new InstalledTheme(file, title, themes.getOrDefault(file, List.of()), source, problems.getOrDefault(file, List.of())));
        }
        return List.copyOf(result);
    }

    private static boolean inFolder(Path file, Path folder) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return folder.equals(file.toAbsolutePath().normalize().getParent()) && (name.endsWith(".jar") || name.endsWith(".zip"));
    }
}
