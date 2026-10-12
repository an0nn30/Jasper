package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/** spec 3.3: a token's own alpha is painted as given; component treatments apply one kit metric to a base token. */
class AlphaOnceTest {
    @Test void focusHalosAndTilesUseTheirVariantsShare() {
        Edt.run(() -> {
            var dark = KitFixtures.context();
            var light = KitFixtures.context(KitFixtures.light());
            try {
                assertThat(ThemePainting.focusHaloShare(dark, false)).isEqualTo(.32);
                assertThat(ThemePainting.focusHaloShare(dark, true)).isEqualTo(.22);
                assertThat(ThemePainting.tileShare(dark)).isEqualTo(.17);
                assertThat(ThemePainting.focusHaloShare(light, false)).isEqualTo(.25);
                assertThat(ThemePainting.tileShare(light)).isEqualTo(.13);
                assertThat(Choices.hueSurface(light, dev.jasper.ui.model.Nodes.Hue.BLUE)).isEqualTo(light.theme().semantic().color(SemanticColor.HUE_BLUE_SURFACE));
            } finally { KitViewTest.release(light); KitViewTest.release(dark); }
        });
    }
}
