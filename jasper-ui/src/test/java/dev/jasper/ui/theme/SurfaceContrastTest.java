package dev.jasper.ui.theme;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SurfaceContrastTest {
    @Test void aRoleReadableOnItsSurfaceIsPaintedAsResolved() {
        var palette = KitFixtures.light().semantic();
        assertThat(SurfaceContrast.on(palette, SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_PANEL, SurfaceContrast.TEXT))
            .isEqualTo(palette.color(SemanticColor.TEXT_PRIMARY));
    }

    @Test void aRoleBelowTheFloorOnItsSurfaceMovesTowardTextThenBlackOrWhiteWithoutChangingTheRole() {
        var base = KitFixtures.light().semantic();
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, base.color(role));
        values.put(SemanticColor.SURFACE_STATUS, new Color(0x1c1c1c));
        values.put(SemanticColor.STATUS_SUCCESS, new Color(0x2e5d32));
        values.put(SemanticColor.SURFACE_APP, new Color(0x1c1c1c));
        values.put(SemanticColor.ACCENT, new Color(0x1a2a6a));
        var palette = new SemanticPalette(values);
        var status = SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_STATUS, SurfaceContrast.TEXT);
        assertThat(ColorMath.contrast(status, new Color(0x1c1c1c))).isGreaterThanOrEqualTo(4.5);
        var badge = SurfaceContrast.on(palette, SemanticColor.ACCENT, SemanticColor.SURFACE_APP, SurfaceContrast.INDICATOR);
        assertThat(ColorMath.contrast(badge, new Color(0x1c1c1c))).isGreaterThanOrEqualTo(3);
        assertThat(palette.color(SemanticColor.STATUS_SUCCESS)).as("the shared role is not floored").isEqualTo(new Color(0x2e5d32));
    }
}
