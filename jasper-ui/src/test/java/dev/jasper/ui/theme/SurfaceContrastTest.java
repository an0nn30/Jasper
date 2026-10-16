package dev.jasper.ui.theme;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test void theGuardsAreSpecSevenThreesFloors() {
        assertThat(SurfaceContrast.Guard.TEXT).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 4.5));
        assertThat(SurfaceContrast.Guard.INDICATOR).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 3));
        assertThat(SurfaceContrast.Guard.STRUCTURAL).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 1.3));
        assertThat(SurfaceContrast.Guard.CONTROL).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 1.5));
        assertThat(SurfaceContrast.Guard.apart(9)).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.APART, 9));
    }

    @Test void aGuardTakesOnlyAFloorEveryOpaqueColourReaches() {
        for (double floor : new double[] {.99, 4.59, Double.NaN})
            assertThatThrownBy(() -> new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, floor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("CONTRAST floor out of range");
        for (double distance : new double[] {-.01, 50.01, Double.NaN})
            assertThatThrownBy(() -> SurfaceContrast.Guard.apart(distance))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("APART floor out of range");
        assertThatThrownBy(() -> new SurfaceContrast.Guard(null, 3)).isInstanceOf(NullPointerException.class);
    }

    /** Spec 7.3: every guard terminates and holds, for every pair of roles of every degenerate palette, at the extreme floors too. */
    @Test void everyGuardHoldsForEveryPairOfEveryDegeneratePalette() {
        var guards = List.of(SurfaceContrast.Guard.TEXT, SurfaceContrast.Guard.INDICATOR, SurfaceContrast.Guard.STRUCTURAL,
            SurfaceContrast.Guard.CONTROL, new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, SurfaceContrast.REACHABLE),
            SurfaceContrast.Guard.apart(SurfaceContrast.TAB_LIGHTNESS), SurfaceContrast.Guard.apart(SurfaceContrast.REACHABLE_LIGHTNESS));
        for (var theme : KitFixtures.degenerate()) {
            var palette = theme.semantic();
            Color ink = palette.color(SemanticColor.TEXT_PRIMARY);
            for (var slot : SemanticColor.values()) for (var against : SemanticColor.values()) for (var guard : guards) {
                Color painted = guard.apply(palette.color(slot), palette.color(against), ink);
                assertThat(guard.holds(painted, palette.color(against))).as("%s: %s on %s, %s", theme.name(), slot, against, guard).isTrue();
            }
        }
    }

    @Test void aGuardThatHoldsLeavesTheColourAsItIs() {
        var translucent = new Color(0x10, 0x20, 0x30, 0x80);
        assertThat(SurfaceContrast.Guard.TEXT.apply(translucent, Color.WHITE, Color.BLACK)).isSameAs(translucent);
        assertThat(SurfaceContrast.Guard.apart(3).apply(Color.BLACK, Color.WHITE, Color.BLACK)).isSameAs(Color.BLACK);
    }
}
