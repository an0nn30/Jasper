package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.3's known repairs, measured on the theme set: each pair's contrast as the theme gives it, and its guard's repair
 * where a classic face will paint it (plan 4 declares these pairs on its faces).
 */
class KnownRepairsTest {
    private static GeneratedTheme theme(String name) {
        return KitFixtures.themes().stream().filter(theme -> (theme.name() + (theme.dark() ? " Dark" : " Light")).equals(name))
            .findFirst().orElseThrow(() -> new AssertionError("No theme " + name));
    }

    private static void repairs(String name, PaintPair pair, double measured) {
        var theme = theme(name);
        var against = pair.against().color(theme);
        assertThat(ColorMath.contrast(pair.slot().color(theme), against)).as("%s: %s on %s", name, pair.slot().key(), pair.against().key())
            .isCloseTo(measured, within(.005));
        assertThat(pair.guard().holds(SurfaceContrast.painted(theme, pair), against)).as("%s: %s repaired", name, pair.slot().key()).isTrue();
    }

    @Test void theKnownRepairsMeasureAsSpecSevenThreeSaysAndTheirGuardsRepairThem() {
        for (String flavour : List.of("Catppuccin Latte Light", "Catppuccin Frappé Dark", "Catppuccin Macchiato Dark", "Catppuccin Mocha Dark"))
            repairs(flavour, new PaintPair(BORDER_CONTROL, SURFACE_INPUT, SurfaceContrast.Guard.CONTROL), 1.00);
        repairs("Catppuccin Latte Light", new PaintPair(BORDER_WINDOW, SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL), 1.17);
        repairs("Ocean Dawn Light", new PaintPair(BORDER_WINDOW, SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL), 1.11);
        repairs("Catppuccin Latte Light", new PaintPair(TEXT_ONACCENT, ACCENT, SurfaceContrast.Guard.TEXT), 4.09);
        repairs("Ocean Dark", new PaintPair(TEXT_ONACCENT, ACCENT, SurfaceContrast.Guard.TEXT), 3.09);
        var warning = new PaintPair(STATUS_WARNING, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.TEXT);
        // Latte's warning ground is now the one its plugin authors; spec 7.3 measured the recipe's (2.06:1).
        repairs("Catppuccin Latte Light", warning, 2.09);
        repairs("Ocean Dawn Light", warning, 1.60);
        repairs("Ocean Dark", warning, 3.52);
        repairs("Slate Cobalt Light", warning, 4.15);
        var dark = theme("Slate Cobalt Dark");
        assertThat(ColorMath.contrast(ACCENT.color(dark), SURFACE_CARD.color(dark))).as("the checked box's fill").isCloseTo(2.70, within(.005));
        assertThat(ColorMath.contrast(DerivedColor.ACCENT_EDGE.color(dark), SURFACE_CARD.color(dark))).as("its accent.edge ring")
            .isGreaterThanOrEqualTo(SurfaceContrast.INDICATOR);
    }
}
