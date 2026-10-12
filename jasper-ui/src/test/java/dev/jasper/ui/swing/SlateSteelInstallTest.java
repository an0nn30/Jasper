package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.Test;

class SlateSteelInstallTest {
    @Test void eachVariantInstallsIntoFlatLafWithItsRoles() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (boolean dark : new boolean[] {true, false}) {
                    var theme = SlateSteel.bundled().generated(dark);
                    FlatLafInstaller.install(theme);
                    assertThat(UIManager.get("Jasper.installedTheme")).isSameAs(theme);
                    assertThat(UIManager.getColor("Panel.background")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_PANEL));
                    assertThat(UIManager.getColor("Jasper.semantic.surface.tabstrip")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_TABSTRIP));
                    assertThat(UIManager.getColor("Jasper.semantic.surface.scrim").getAlpha()).isEqualTo(dark ? 0x8C : 0x40);
                }
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
}
