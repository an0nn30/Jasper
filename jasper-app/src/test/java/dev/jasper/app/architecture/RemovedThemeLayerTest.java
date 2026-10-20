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
        "dev.jasper.app.themesui.ThemeImport", "dev.jasper.ui.theme.Token", "dev.jasper.ui.theme.ThemeGenerator",
        "dev.jasper.ui.theme.SemanticDefaults", "dev.jasper.ui.theme.ThemeResolver", "dev.jasper.ui.theme.ThemeDocument",
        "dev.jasper.ui.themeio.ThemeToml", "dev.jasper.ui.themeimport.ThemeConverter");

    @Test void theLegacyClassesAndAliasesAreGone() {
        for (String name : REMOVED)
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        assertThat(RemovedThemeLayerTest.class.getResource("/dev/jasper/app/themes/legacy-aliases.json")).isNull();
        assertThat(RemovedThemeLayerTest.class.getResource("/dev/jasper/ui/themeimport/native/index.txt")).isNull();
    }
}
