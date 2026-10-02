package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

/** Converted tokens must agree with the author-specific effective FlatLaf colors, not just raw JSON. */
@ExtendWith(EdtTestExtension.class)
class BundledWorkspaceParityTest {
    @Test void completeBundledThemesPreserveEffectiveWorkspaceColors() throws Exception {
        Path root = Path.of(System.getProperty("jasper.repoRoot"), "jasper-app/src/main/resources/dev/jasper/app/themes");
        var previous = UIManager.getLookAndFeel();
        var mismatches = new ArrayList<String>();
        try {
            for (String file : Files.readAllLines(root.resolve("index.txt"))) {
                var document = ThemeToml.parse(Files.readString(root.resolve(file)), file);
                if (!document.uiComplete().orElse(false)) continue;
                var theme = Theme.of(file, new Theme.Bundled(file, () -> document));
                var values = ThemeManager.generated(theme);
                assertThat(ThemeManager.install(theme)).as(file).isTrue();
                WorkspaceTokenKeys.KEYS.forEach((token, key) -> {
                    var color = UIManager.getLookAndFeelDefaults().getColor(key);
                    if (color != null && !color.equals(values.tokens().get(token)))
                        mismatches.add(file + " / " + key + ": " + values.tokens().get(token) + " != " + color);
                });
            }
            assertThat(mismatches).isEmpty();
        } finally { UIManager.setLookAndFeel(previous); ThemeManager.retainGenerated(java.util.List.of()); }
    }
}
