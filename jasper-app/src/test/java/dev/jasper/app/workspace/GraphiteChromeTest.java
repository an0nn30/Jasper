package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.ArrayDeque;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class GraphiteChromeTest {
    @Test void graphiteContentSurfacesContrastWithChromeAndSettingsHasOneHome() {
        try (var owner = content(launcher(new ArrayDeque<>()))) {
            owner.selectTheme(Theme.DARK);
            assertThat(owner.theme().palette().background()).isEqualTo(new Color(0x191c20));
            assertThat(UIManager.getColor("ToolWindow.background")).isEqualTo(new Color(0x191c20));
            assertThat(UIManager.getColor("Jasper.titleBackground")).isEqualTo(new Color(0x25292e));
            assertThat(UIManager.getColor("Jasper.tabUnderline")).isEqualTo(new Color(0x8eb8d2));
            assertThat(java.util.Arrays.stream(owner.rail().getComponents())
                .filter(javax.swing.JButton.class::isInstance)
                .map(child -> ((javax.swing.JButton) child).getToolTipText())).doesNotContain("Settings");
        }
    }
}
