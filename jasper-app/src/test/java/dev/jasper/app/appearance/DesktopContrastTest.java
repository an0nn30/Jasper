package dev.jasper.app.appearance;

import dev.jasper.ui.theme.ColorMath;
import java.awt.Color;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopContrastTest {
    @Test void repairsNormalHighContrastAndBoundaryTargets() {
        for (double minimum : new double[] {3, 4.5, 7}) {
            Color repaired = DesktopContrast.repair(new Color(128, 128, 128), Color.WHITE, minimum).orElseThrow();
            assertThat(ColorMath.contrast(repaired, Color.WHITE)).isGreaterThanOrEqualTo(minimum);
        }
    }
    @Test void justBelowThreeToOneStillRequiresRepair() {
        Color original = new Color(149, 149, 149);
        assertThat(ColorMath.contrast(original, Color.WHITE)).isLessThan(3);
        Color repaired = DesktopContrast.repair(original, Color.WHITE, 3).orElseThrow();
        assertThat(repaired).isNotEqualTo(original);
        assertThat(ColorMath.contrast(repaired, Color.WHITE)).isGreaterThanOrEqualTo(3);
    }

    @Test void blackDirectionWinsAnEqualDistanceRepairTie() {
        // Both #65285e and white are 30905 squared RGB units from this color and pass 3:1.
        assertThat(DesktopContrast.repair(new Color(225, 88, 209), new Color(145, 145, 145), 3))
            .contains(new Color(101, 40, 94));
    }

    @Test void nearestPassingStepIsChosenAcrossBothDirections() {
        Color original = new Color(140, 90, 170), background = Color.WHITE;
        Color repaired = DesktopContrast.repair(original, background, 7).orElseThrow();
        int distance = squared(repaired, original);
        for (Color end : java.util.List.of(Color.BLACK, Color.WHITE)) for (int step = 0; step <= 20; step++) {
            Color candidate = ColorMath.mix(end, original, step / 20.0);
            if (ColorMath.contrast(candidate, background) >= 7) assertThat(squared(candidate, original)).isGreaterThanOrEqualTo(distance);
        }
    }
    private static int squared(Color a, Color b) {
        int r = a.getRed()-b.getRed(), g = a.getGreen()-b.getGreen(), blue = a.getBlue()-b.getBlue();
        return r*r+g*g+blue*blue;
    }

    @Test void preservesAlreadyPassingInk() {
        assertThat(DesktopContrast.repair(Color.BLACK, Color.WHITE, 7)).contains(Color.BLACK);
    }
    @Test void cannotRepairAnUnreachableTarget() {
        assertThat(DesktopContrast.repair(Color.GRAY, Color.GRAY, 7)).isEmpty();
    }
    @Test void refusesUnknownAlphaParents() {
        assertThat(DesktopContrast.repair(new Color(0, 0, 0, 100), Color.WHITE, 4.5)).isEmpty();
        assertThat(DesktopContrast.repair(Color.BLACK, new Color(255, 255, 255, 100), 4.5)).isEmpty();
    }
}
