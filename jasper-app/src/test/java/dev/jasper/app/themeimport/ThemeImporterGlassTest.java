package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.ThemeToml;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeImporterGlassTest {
    @Test void renamingAnImportedThemeKeepsItsWorkspaceTable() {
        var document = ThemeToml.parse("name = \"Original\"\nextends = \"Jasper Dark\"\n[workspace]\nopacity = 0.9\nblur = 0.3\n", "original.toml");
        var renamed = ThemeImporter.rename(document, "Renamed");
        assertThat(renamed.name()).isEqualTo("Renamed");
        assertThat(renamed.workspace().opacity()).isEqualTo(Optional.of(0.9));
        assertThat(renamed.workspace().blur()).isEqualTo(Optional.of(0.3));
    }
}
