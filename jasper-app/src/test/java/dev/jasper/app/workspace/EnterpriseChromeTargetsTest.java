package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import org.junit.jupiter.api.Test;

/**
 * Plan 6b Rulings 2 and 3: the chrome roles the Workstation and Dark Mode mocks paint are the Enterprise theme's values;
 * the tab indicator follows the accent (spec 7.6, amended with plan 5b's accent ruling).
 */
class EnterpriseChromeTargetsTest {
    @Test void theChromeTargetsAreTheMocks() {
        var theme = EnterpriseLook.theme();
        for (boolean dark : new boolean[] {false, true}) {
            var palette = theme.palette(dark);
            assertThat(palette.color(SemanticColor.SURFACE_TABSTRIP)).isEqualTo(MockProfile.role(dark, "surface.tabstrip"));
            assertThat(palette.color(SemanticColor.TAB_INDICATOR)).as("the accent").isEqualTo(palette.color(SemanticColor.ACCENT));
            var scrim = palette.color(SemanticColor.SURFACE_SCRIM);
            assertThat(scrim.getRGB() & 0xFFFFFF).as("black").isZero();
            assertThat(scrim.getAlpha()).as("spec 10.8's 21 %").isEqualTo(0x36);
            assertThat(DerivedColor.BORDER_DIVIDER.color(theme.generated(dark))).isEqualTo(MockProfile.role(dark, "border.divider"));
        }
    }
}
