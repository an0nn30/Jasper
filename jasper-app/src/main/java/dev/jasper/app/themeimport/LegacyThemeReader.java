package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import java.nio.file.Path;
import java.util.ArrayList;

/** Transitional composition bridge; removed once migration makes the runtime catalog native-only. */
public final class LegacyThemeReader {
    private LegacyThemeReader() {}
    /** Builds the existing catalog with explicit legacy input ownership. */
    public static ThemeCatalog catalog(Path configFile) { return ThemeCatalog.beside(configFile, LegacyThemeReader::read); }
    /** Reads one archive's native values and preserves all its diagnostics. */
    public static ThemeCatalog.Snapshot read(Path source) {
        var batch = ThemeConverter.archive(source);
        var themes = new ArrayList<Theme>(); var problems = new ArrayList<ThemeCatalog.Problem>();
        for (var result : batch.results()) {
            themes.add(Theme.of("installed:" + source.getFileName() + "!/" + result.sourceEntry(),
                new Theme.Archive(source, source.getFileName() + "!/" + result.sourceEntry(), result.document())));
            result.warnings().forEach(warning -> problems.add(new ThemeCatalog.Problem(source, warning)));
        }
        batch.failures().forEach(failure -> problems.add(new ThemeCatalog.Problem(source, failure.sourceEntry() + ": " + failure.message())));
        return new ThemeCatalog.Snapshot(themes, problems);
    }
    /** Inspects an archive without installing it or loading plugin classes. */
    public static ThemeCatalog.Inspection inspect(Path source) {
        var snapshot = read(source);
        return new ThemeCatalog.Inspection(snapshot.themes().stream().map(Theme::name).toList(),
            snapshot.problems().stream().map(ThemeCatalog.Problem::message).toList());
    }
}
