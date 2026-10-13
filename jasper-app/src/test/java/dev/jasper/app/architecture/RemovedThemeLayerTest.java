package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 9: the app's legacy theme layer is gone, so nothing can reach it again. */
class RemovedThemeLayerTest {
    static final List<String> REMOVED = List.of("dev.jasper.app.appearance.Theme", "dev.jasper.app.appearance.ThemeCatalog",
        "dev.jasper.app.appearance.BuiltInThemes", "dev.jasper.app.appearance.ThemeState",
        "dev.jasper.app.config.WorkspaceThemeChoice", "dev.jasper.app.config.TerminalColors",
        "dev.jasper.app.themeimport.ThemeMigration", "dev.jasper.app.themeimport.ConvertThemes",
        "dev.jasper.app.themesui.ThemeImport");

    @Test void theLegacyClassesAndAliasesAreGone() {
        for (String name : REMOVED)
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        assertThat(RemovedThemeLayerTest.class.getResource("/dev/jasper/app/themes/legacy-aliases.json")).isNull();
    }
}
