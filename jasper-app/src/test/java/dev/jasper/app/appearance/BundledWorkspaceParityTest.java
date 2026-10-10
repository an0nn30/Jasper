package dev.jasper.app.appearance;
import dev.jasper.ui.themeio.ThemeToml;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

/** Every bundled semantic snapshot remains installed intact, including after a native LAF reinstall. */
@ExtendWith(EdtTestExtension.class)
class BundledWorkspaceParityTest {
    @Test void completeBundledThemesPreserveEffectiveWorkspaceColors() throws Exception {
        Path root = Path.of(System.getProperty("jasper.repoRoot"), "jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native");
        var previous = UIManager.getLookAndFeel();
        try {
            for (String file : Files.readAllLines(root.resolve("index.txt"))) {
                var document = ThemeToml.parse(Files.readString(root.resolve(file)), file);
                if (!document.uiComplete().orElse(false)) continue;
                var theme = Theme.of(file, new Theme.Bundled(file, () -> document));
                var values = ThemeManager.generated(theme);
                assertThat(ThemeManager.install(theme)).as(file).isTrue();
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(values);
                for (var role : dev.jasper.ui.theme.SemanticColor.values())
                    assertThat(UIManager.getColor("Jasper.semantic." + role.key())).as(role.key()).isEqualTo(values.semantic().color(role));
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(values);
                for (var role : dev.jasper.ui.theme.SemanticColor.values())
                    assertThat(UIManager.getColor("Jasper.semantic." + role.key())).as("reinstalled " + role.key()).isEqualTo(values.semantic().color(role));
            }
        } finally { UIManager.setLookAndFeel(previous); ThemeManager.retainGenerated(java.util.List.of()); }
    }
}
