package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import org.junit.jupiter.api.Test;

/**
 * Plan 6b Rulings 2 and 3: the chrome roles the Workstation and Dark Mode mocks paint are the Enterprise theme's values
 * (the fixture theme until plan 5b, whose shipped theme takes the same eight values).
 */
class EnterpriseChromeTargetsTest {
    @Test void theChromeTargetsAreTheMocks() {
        var theme = EnterpriseLook.theme();
        assertThat(ColorMath.hex(theme.palette(false).color(SemanticColor.SURFACE_TABSTRIP))).isEqualToIgnoringCase("#E8E9EB");
        assertThat(ColorMath.hex(theme.palette(true).color(SemanticColor.SURFACE_TABSTRIP))).isEqualToIgnoringCase("#3A3D42");
        assertThat(ColorMath.hex(theme.palette(false).color(SemanticColor.TAB_INDICATOR))).isEqualToIgnoringCase("#2F65CA");
        assertThat(ColorMath.hex(theme.palette(true).color(SemanticColor.TAB_INDICATOR))).isEqualToIgnoringCase("#3D74D6");
        for (boolean dark : new boolean[] {false, true}) {
            var scrim = theme.palette(dark).color(SemanticColor.SURFACE_SCRIM);
            assertThat(scrim.getRGB() & 0xFFFFFF).as("black").isZero();
            assertThat(scrim.getAlpha()).as("spec 10.8's 21 %, so white reads #C9CACC").isEqualTo(0x36);
        }
        assertThat(ColorMath.hex(DerivedColor.BORDER_DIVIDER.color(theme.generated(false)))).as("the override, not the recipe")
            .isEqualToIgnoringCase("#D3D6DA");
        assertThat(ColorMath.hex(DerivedColor.BORDER_DIVIDER.color(theme.generated(true)))).as("lighter than the recipe's #2B2D31")
            .isEqualToIgnoringCase("#4A4D53");
    }
}
