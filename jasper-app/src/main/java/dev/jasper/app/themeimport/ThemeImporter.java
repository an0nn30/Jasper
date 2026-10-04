package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeDocument;
import dev.jasper.app.appearance.ThemeManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;

/** Frozen import plans and explicit conflict decisions; no input source is moved or reread during commit. */
public final class ThemeImporter {
    public enum ConflictChoice { REPLACE, KEEP_BOTH, SKIP }
    public record Item(String id, ConversionResult conversion, Optional<Path> target,
                       Optional<String> expectedSha256, boolean shadowsBundled) {}
    public record ImportPlan(List<Item> items, List<ThemeConverter.Failure> failures) {
        public ImportPlan { items = List.copyOf(items); failures = List.copyOf(failures); }
    }
    public record Imported(String id, String name, Path path) {}
    public record ImportReport(List<Imported> imported, List<String> warnings, List<ThemeConverter.Failure> failures) {
        public ImportReport { imported = List.copyOf(imported); warnings = List.copyOf(warnings); failures = List.copyOf(failures); }
    }
    private record Prepared(Item item, ThemeDocument document, boolean replacement) {}
    private final Path home;
    private final ThemeFiles files;

    public ThemeImporter(Path home) { this(home, new ThemeFiles(home)); }
    ThemeImporter(Path home, ThemeFiles files) { this.home = home.toAbsolutePath().normalize(); this.files = files; }

    public ImportPlan plan(Path source) {
        worker();
        var conversion = ThemeConverter.convertAll(source);
        var failures = new ArrayList<>(conversion.failures());
        var items = new ArrayList<Item>();
        try {
            files.locked(() -> {
                var snapshot = snapshot();
                var owners = owners(snapshot);
                snapshot.problems().forEach(problem -> failures.add(new ThemeConverter.Failure(problem.file().toString(), problem.message())));
                for (var result : conversion.results()) {
                    try {
                        String id = ThemeSources.hash((source.toAbsolutePath().normalize() + "\0" + result.sourceEntry()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        Path target = owners.get(key(result.document().name()));
                        items.add(new Item(id, result, Optional.ofNullable(target), target == null ? Optional.empty() : Optional.of(files.fingerprint(target)),
                            ThemeManager.builtIns().stream().anyMatch(theme -> key(theme.name()).equals(key(result.document().name())))));
                    } catch (IOException | RuntimeException failure) {
                        failures.add(new ThemeConverter.Failure(result.sourceEntry(), message(failure)));
                    }
                }
                return null;
            });
        } catch (IOException | RuntimeException failure) { failures.add(new ThemeConverter.Failure(source.toString(), message(failure))); }
        return new ImportPlan(items, failures);
    }

    /** Validates the whole selection first. Publication may partly succeed; only committed files appear in imported. */
    public ImportReport commit(ImportPlan plan, Map<String, ConflictChoice> choices) {
        worker();
        var imported = new ArrayList<Imported>(); var warnings = new ArrayList<String>();
        var failures = new ArrayList<>(plan.failures());
        Map<String, ConflictChoice> selected = Map.copyOf(choices);
        try {
            files.locked(() -> {
                var owners = owners(snapshot());
                var reserved = new HashSet<>(owners.keySet());
                var ids = new HashSet<String>(); var replacements = new HashSet<Path>();
                var prepared = new ArrayList<Prepared>();
                var invalid = new ArrayList<ThemeConverter.Failure>();
                for (Item item : plan.items()) {
                    try {
                        if (!ids.add(item.id())) throw new IOException("Duplicate import item identity");
                        ConflictChoice choice = selected.getOrDefault(item.id(), item.target().isPresent() ? ConflictChoice.SKIP : ConflictChoice.KEEP_BOTH);
                        if (choice == ConflictChoice.SKIP) continue;
                        ThemeDocument original = item.conversion().document();
                        ThemeDocument document = dev.jasper.app.appearance.ThemeToml.parse(
                            dev.jasper.app.appearance.ThemeTomlWriter.write(original), original.origin());
                        String name = document.name(); String normalized = key(name);
                        boolean replace = item.target().isPresent() && choice == ConflictChoice.REPLACE;
                        if (replace) {
                            Path target = item.target().orElseThrow();
                            if (!target.equals(owners.get(normalized))) throw new IOException("Theme ownership changed since planning; replan the import");
                            if (!replacements.add(target)) throw new IOException("Two selected providers cannot replace the same file");
                            if (!files.fingerprint(target).equals(item.expectedSha256().orElse(""))) throw new IOException("Theme changed since planning; replan the import");
                        } else {
                            if (item.target().isEmpty() && owners.containsKey(normalized)) throw new IOException("New theme name conflict; replan the import");
                            for (int suffix = 2; reserved.contains(key(name)); suffix++) name = document.name() + " " + suffix;
                            document = rename(document, name);
                        }
                        ThemeFiles.serialized(document);
                        reserved.add(key(name));
                        prepared.add(new Prepared(item, document, replace));
                    } catch (IOException | RuntimeException failure) {
                        invalid.add(new ThemeConverter.Failure(item.conversion().sourceEntry(), message(failure)));
                    }
                }
                if (!invalid.isEmpty()) { failures.addAll(invalid); return null; }
                for (Prepared next : prepared) {
                    Item item = next.item();
                    try {
                        var current = owners(snapshot());
                        Path owner = current.get(key(next.document().name()));
                        if (next.replacement()) {
                            Path target = item.target().orElseThrow();
                            if (!target.equals(owner)) throw new IOException("Theme ownership changed before publication");
                            files.replace(target, item.expectedSha256().orElseThrow(), next.document());
                            imported.add(new Imported(item.id(), next.document().name(), target));
                        } else {
                            if (owner != null) throw new IOException("New theme name conflict before publication");
                            Path target = files.create(next.document());
                            imported.add(new Imported(item.id(), next.document().name(), target));
                        }
                        warnings.addAll(item.conversion().warnings());
                        if (ThemeManager.builtIns().stream().anyMatch(theme -> key(theme.name()).equals(key(next.document().name())))) warnings.add(next.document().name() + " overrides a bundled theme in the user themes folder");
                    } catch (IOException | RuntimeException failure) {
                        failures.add(new ThemeConverter.Failure(item.conversion().sourceEntry(), message(failure)));
                    }
                }
                return null;
            });
        } catch (IOException | RuntimeException failure) { failures.add(new ThemeConverter.Failure("import", message(failure))); }
        return new ImportReport(imported, warnings, failures);
    }

    private ThemeCatalog.Snapshot snapshot() throws IOException {
        files.directory(); // Reject symlink/non-directory targets before the catalog can create or follow them.
        return ThemeCatalog.beside(home.resolve("config.toml")).scan();
    }
    private static Map<String, Path> owners(ThemeCatalog.Snapshot snapshot) {
        var result = new LinkedHashMap<String, Path>();
        for (Theme theme : snapshot.themes()) if (theme.source() instanceof Theme.UserFile file)
            result.put(key(theme.name()), file.file().toAbsolutePath().normalize());
        return result;
    }
    static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }
    static ThemeDocument rename(ThemeDocument base, String name) {
        return new ThemeDocument(base.origin(), name, base.dark(), base.parent(), base.parentLine(), base.uiComplete(), base.metadata(),
            base.seeds(), base.ansi(), base.tokens(), base.syntax(), base.ui(), base.icons(), base.workspace());
    }
    private static void worker() { if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme import requires a worker thread"); }
    private static String message(Throwable failure) { return failure.getMessage() == null ? failure.toString() : failure.getMessage(); }
}
