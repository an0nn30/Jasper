package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import org.junit.jupiter.api.Test;

/**
 * Plan 6b Rulings 2 and 3: the chrome roles the Workstation and Dark Mode mocks paint are the Enterprise theme's values.
 * Since the user's 2026-10-18 direction the theme ports IntelliJ Light and New UI Dark, so the tab indicator is
 * IntelliJ's tab underline and the scrim the converter's (black at 25 % in Light, 55 % in Dark).
 */
class EnterpriseChromeTargetsTest {
    @Test void theChromeTargetsAreTheMocks() {
        var theme = EnterpriseLook.theme();
        for (boolean dark : new boolean[] {false, true}) {
            var palette = theme.palette(dark);
            assertThat(palette.color(SemanticColor.SURFACE_TABSTRIP)).isEqualTo(MockProfile.role(dark, "surface.tabstrip"));
            assertThat(palette.color(SemanticColor.TAB_INDICATOR).getRGB() & 0xFFFFFF).as("EditorTabs.underlineColor").isEqualTo(dark ? 0x3574F0 : 0x4083C9);
            var scrim = palette.color(SemanticColor.SURFACE_SCRIM);
            assertThat(scrim.getRGB() & 0xFFFFFF).as("black").isZero();
            assertThat(scrim.getAlpha()).as("the converter's scrim").isEqualTo(dark ? 0x8C : 0x40);
            assertThat(DerivedColor.BORDER_DIVIDER.color(theme.generated(dark))).isEqualTo(MockProfile.role(dark, "border.divider"));
        }
    }
}
