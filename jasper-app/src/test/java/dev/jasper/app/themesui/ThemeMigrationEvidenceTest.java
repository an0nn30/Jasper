package dev.jasper.app.themesui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.themeimport.ConversionResult;
import dev.jasper.app.themeimport.ThemeConverter;
import dev.jasper.app.themeimport.ThemeImporter;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesThemeImportConflictAndFailureWithoutFilePublication() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            new dev.jasper.app.appearance.ThemeController().configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT);
            var conversion = new ConversionResult(Theme.DARK.source().document(), List.of("Fixture import warning"), "fixture-theme");
            var old = new ThemeImporter.Item("fixture", conversion, Optional.of(Path.of("fixture.toml")), Optional.of("hash"), false);
            var view = new ThemeImportView(new ThemeImporter.ImportPlan(List.of(old), List.of()), choices -> { }, () -> { });
            save("theme-import", theme, "conflict", view, 680, 440);
            view.commit.doClick(); save("theme-import", theme, "pending-commit", view, 680, 440);
            var failed = new ThemeImportView(new ThemeImporter.ImportPlan(List.of(), List.of(new ThemeConverter.Failure("fixture-theme", "Fixture parse failure"))), choices -> { }, () -> { });
            save("theme-import", theme, "failure", failed, 680, 440);
        });
    }
}
