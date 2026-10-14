package dev.jasper.ui.theme;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SurfaceContrastTest {
    @Test void lightnessIsCieLStar() {
        assertThat(ColorMath.lightness(Color.BLACK)).isZero();
        assertThat(ColorMath.lightness(Color.WHITE)).isCloseTo(100, within(.01));
        assertThat(ColorMath.lightness(new Color(0x777777))).isCloseTo(50, within(.5));
    }

    @Test void anInactiveTabTooCloseToTheActiveOneIsDarkenedJustUntilItIsClearlyApart() {
        for (var surfaces : java.util.List.of(new Color[] {new Color(0x303446), new Color(0x303446)},
                new Color[] {new Color(0xFBFBFC), new Color(0xE9ECEF)}, new Color[] {new Color(0x23262C), new Color(0x1F2228)})) {
            Color active = surfaces[0], strip = surfaces[1];
            Color tab = SurfaceContrast.apart(strip, active, SurfaceContrast.TAB_LIGHTNESS);
            double gap = ColorMath.lightness(active) - ColorMath.lightness(tab);
            assertThat(gap).as(ColorMath.hex(strip) + " under " + ColorMath.hex(active)).isGreaterThanOrEqualTo(9).isLessThan(10);
        }
        Color frappe = SurfaceContrast.apart(new Color(0x303446), new Color(0x303446), SurfaceContrast.TAB_LIGHTNESS);
        assertThat(frappe.getBlue()).as("the strip keeps its tint").isGreaterThan(frappe.getRed());
    }

    @Test void anInactiveTabAlreadyClearlyApartKeepsItsColourOnEitherSide() {
        assertThat(SurfaceContrast.apart(new Color(0x101218), new Color(0x303446), SurfaceContrast.TAB_LIGHTNESS)).isEqualTo(new Color(0x101218));
        assertThat(SurfaceContrast.apart(new Color(0x5A5F78), new Color(0x303446), SurfaceContrast.TAB_LIGHTNESS)).isEqualTo(new Color(0x5A5F78));
    }

    @Test void anActiveTabTooDarkToGoBelowGetsLighterInactiveTabs() {
        Color tab = SurfaceContrast.apart(Color.BLACK, Color.BLACK, SurfaceContrast.TAB_LIGHTNESS);
        assertThat(ColorMath.lightness(tab)).isGreaterThanOrEqualTo(9).isLessThan(10);
    }

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
