package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.config.ThemeSelectionMigration;
import dev.jasper.app.themeimport.ThemeMigration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pre-service coordinator joining independent file migration and config ownership. Never runs on the EDT. */
public final class ThemeUpgrade {
    private ThemeUpgrade() {}
    public static List<ThemeCatalog.Problem> prepare(Path config, boolean macOs) {
        Path absolute = config.toAbsolutePath().normalize();
        var migration = new ThemeMigration(absolute.getParent()); var report = migration.run();
        var problems = new ArrayList<>(report.problems());
        if (report.completed() || report.generation().isEmpty()) return List.copyOf(problems);
        try {
            var pending = migration.configIntent();
            if (pending.isPresent()) {
                var intent = pending.get();
                if (!intent.configFileName().equals(absolute.getFileName().toString())) throw new IOException("Pending theme migration belongs to another config override: " + intent.configFileName());
                var values = ThemeSelectionMigration.values(absolute, macOs);
                if (values.equals(intent.after())) { migration.complete(report); return List.copyOf(problems); }
                if (!values.equals(intent.before())) throw new IOException("Theme selections changed during migration; configuration retained");
                // Original theme values still match: preserve unrelated edits and reproduce only the recorded edit.
                var canonical = ThemeSelectionMigration.plan(absolute, macOs, Map.of(), Map.of()).map(ThemeSelectionMigration.Edit::after).orElse(values);
                var chrome = rename(canonical, intent.after(), "ui.theme.name");
                var workspace = rename(canonical, intent.after(), "ui.theme.workspace");
                var edit = ThemeSelectionMigration.plan(absolute, macOs, chrome, workspace).orElseThrow(() -> new IOException("Pending configuration edit cannot be recovered"));
                if (!edit.after().equals(intent.after())) throw new IOException("Pending configuration edit changed");
                migration.recordConfigIntent(report.generation(), intent(absolute, edit));
                ThemeSelectionMigration.apply(absolute, macOs, edit);
            } else {
                var edit = ThemeSelectionMigration.plan(absolute, macOs, report.chromeRenames(), report.workspaceRenames());
                if (edit.isPresent()) {
                    migration.recordConfigIntent(report.generation(), intent(absolute, edit.get()));
                    ThemeSelectionMigration.apply(absolute, macOs, edit.get());
                }
            }
            migration.complete(report);
        } catch (IOException | java.io.UncheckedIOException failure) {
            problems.add(new ThemeCatalog.Problem(absolute, "Theme upgrade pending: " + failure.getMessage()));
        }
        return List.copyOf(problems);
    }
    public static List<ThemeCatalog.Problem> diagnostics(Path config) {
        return new ThemeMigration(config.toAbsolutePath().normalize().getParent()).problems();
    }
    private static ThemeMigration.ConfigIntent intent(Path file, ThemeSelectionMigration.Edit edit) {
        return new ThemeMigration.ConfigIntent(file.getFileName().toString(), edit.expectedSha256(), edit.desiredSha256(), edit.before(), edit.after());
    }
    private static Map<String, String> rename(Map<String, String> before, Map<String, String> after, String canonical) {
        String old = before.get(canonical), desired = after.get(canonical);
        return old == null || desired == null ? Map.of() : Map.of(old.strip().toLowerCase(java.util.Locale.ROOT), desired);
    }
}
