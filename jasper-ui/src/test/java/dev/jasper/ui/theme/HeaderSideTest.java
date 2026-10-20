package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.DerivedColor.SURFACE_HEADER;
import static dev.jasper.ui.theme.DerivedColor.SURFACE_HEADER_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/** Enterprise plan 7 (plan 2's hand-off): derived surfaces lean toward the variant's text. */
class HeaderSideTest {
    @Test void theActiveHeaderLeansTowardTheTextInEveryShippedVariant() {
        for (var theme : KitFixtures.themes()) {
            if (theme.derivedOverrides().containsKey(SURFACE_HEADER_ACTIVE)) continue;
            var derived = DerivedPalette.of(theme);
            Color header = derived.color(SURFACE_HEADER), active = derived.color(SURFACE_HEADER_ACTIVE);
            boolean lighter = ColorMath.lightness(theme.semantic().color(SemanticColor.TEXT_PRIMARY)) > ColorMath.lightness(header);
            double delta = ColorMath.lightness(active) - ColorMath.lightness(header);
            assertThat(lighter ? delta : -delta).as(theme.name() + (theme.dark() ? " Dark" : " Light")).isGreaterThanOrEqualTo(2 - 1e-9);
        }
    }

    /**
     * Spec 10.7: the focused header strip, as painted, stands past the others toward the text in every shipped variant,
     * those that author the two alike (Catppuccin) included; a derived pair paints as derived.
     */
    @Test void theFocusedStripIsPaintedPastTheOthersInEveryShippedVariant() {
        var painter = new ChromePainter(Kit.CLASSIC);
        for (var theme : KitFixtures.themes()) {
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            var derived = DerivedPalette.of(theme);
            Color header = derived.color(SURFACE_HEADER), focused = painter.headerSurface(theme, true);
            assertThat(painter.headerSurface(theme, false)).as(where + ": the others are the role").isEqualTo(header);
            boolean lighter = ColorMath.lightness(theme.semantic().color(SemanticColor.TEXT_PRIMARY)) > ColorMath.lightness(header);
            double delta = ColorMath.lightness(focused) - ColorMath.lightness(header);
            assertThat(lighter ? delta : -delta).as(where).isGreaterThanOrEqualTo(1 - 1e-9);
            if (!theme.derivedOverrides().containsKey(SURFACE_HEADER_ACTIVE))
                assertThat(focused).as(where + ": a derived pair paints as derived").isEqualTo(derived.color(SURFACE_HEADER_ACTIVE));
        }
    }

    @Test void slateCobaltDarksFocusedHeaderIsLighterThanItsOthers() {
        var derived = DerivedPalette.of(KitFixtures.dark());
        assertThat(ColorMath.hex(derived.color(SURFACE_HEADER_ACTIVE))).isEqualToIgnoringCase("#2d3546");
        assertThat(ColorMath.lightness(derived.color(SURFACE_HEADER_ACTIVE))).isGreaterThan(ColorMath.lightness(derived.color(SURFACE_HEADER)));
        assertThat(ColorMath.hex(DerivedPalette.of(KitFixtures.light()).color(SURFACE_HEADER_ACTIVE))).as("Light keeps its value")
            .isEqualToIgnoringCase("#d3dcea");
    }

    @Test void beyondKeepsAColourOnTheTextSideOrMovesItThere() {
        Color dark = new Color(0x23, 0x26, 0x2C), light = new Color(0xF2, 0xF4, 0xF7);
        Color already = new Color(0x40, 0x44, 0x4C);
        assertThat(SurfaceContrast.beyond(already, dark, 3, light)).as("far enough on the light side").isEqualTo(already);
        Color under = new Color(0x1C, 0x1F, 0x24);
        Color moved = SurfaceContrast.beyond(under, dark, 3, light);
        assertThat(ColorMath.lightness(moved) - ColorMath.lightness(dark)).as("moved past to the light side").isGreaterThanOrEqualTo(3);
        Color near = new Color(250, 250, 250);
        Color fallback = SurfaceContrast.beyond(near, near, 3, Color.WHITE);
        assertThat(ColorMath.lightness(near) - ColorMath.lightness(fallback)).as("no room on the text side: the other side").isGreaterThanOrEqualTo(3);
    }

    @Test void aBeyondGuardHoldsOnTheTextsSideAndRepairsAsBeyondDoes() {
        var guard = SurfaceContrast.Guard.beyond(3);
        Color dark = new Color(0x23, 0x26, 0x2C), light = new Color(0xF2, 0xF4, 0xF7), under = new Color(0x1C, 0x1F, 0x24);
        assertThat(guard.kind()).isEqualTo(SurfaceContrast.Guard.Kind.BEYOND);
        assertThat(guard.holds(new Color(0x40, 0x44, 0x4C), dark, light)).as("past it on the text's side").isTrue();
        assertThat(guard.holds(under, dark, light)).as("on the other side").isFalse();
        assertThat(guard.holds(new Color(0x08, 0x09, 0x0A), dark, light)).as("far on the other side").isFalse();
        assertThat(guard.holds(new Color(0x08, 0x09, 0x0A), dark)).as("without the text: either side").isTrue();
        assertThat(guard.apply(under, dark, light)).isEqualTo(SurfaceContrast.beyond(under, dark, 3, light));
    }
}
