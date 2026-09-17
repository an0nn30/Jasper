package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BubbleMotionTest {
    @Test void theCurveStartsAtNothingAndEndsAtExactlyOne() {
        assertThat(BubbleMotion.easeOutBack(0f)).isEqualTo(0f);
        assertThat(BubbleMotion.easeOutBack(1f)).isEqualTo(1f);
    }

    @Test void theCardStartsSmallAndSettlesAtItsRealSize() {
        assertThat(BubbleMotion.inScale(0)).isEqualTo(BubbleMotion.IN_FROM);
        assertThat(BubbleMotion.inScale(BubbleMotion.IN_NANOS)).isEqualTo(1f);
        assertThat(BubbleMotion.inScale(BubbleMotion.IN_NANOS * 3)).isEqualTo(1f);
    }

    /** A bounce, not a fade-up: the scale must overshoot once and come back. */
    @Test void theCardOvershootsOnceOnItsWayIn() {
        float peak = 0f;
        int crossings = 0;
        boolean above = false;
        for (long t = 0; t <= BubbleMotion.IN_NANOS; t += BubbleMotion.IN_NANOS / 200) {
            float scale = BubbleMotion.inScale(t);
            peak = Math.max(peak, scale);
            if (scale > 1f != above) { above = scale > 1f; crossings++; }
        }

        assertThat(peak).isCloseTo(BubbleMotion.IN_PEAK, within(0.005f));
        assertThat(crossings).as("up over 1 and back down, exactly once each").isEqualTo(2);
    }

    @Test void opacityRidesTheSameCurveButNeverLeavesItsRange() {
        for (long t = 0; t <= BubbleMotion.IN_NANOS; t += BubbleMotion.IN_NANOS / 50) {
            assertThat(BubbleMotion.inOpacity(t)).isBetween(0f, 1f);
        }
        assertThat(BubbleMotion.inOpacity(0)).isEqualTo(0f);
        assertThat(BubbleMotion.inOpacity(BubbleMotion.IN_NANOS)).isEqualTo(1f);
    }

    @Test void hoveringGrowsTheCardAndLeavingPutsItBack() {
        assertThat(BubbleMotion.hoverScale(0, true)).isEqualTo(1f);
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS, true)).isEqualTo(BubbleMotion.HOVER_SCALE);
        assertThat(BubbleMotion.hoverScale(0, false)).isEqualTo(BubbleMotion.HOVER_SCALE);
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS, false)).isEqualTo(1f);
    }

    @Test void hoverIsClampedPastItsDurationSoALateTickCannotOvershoot() {
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS * 9, true)).isEqualTo(BubbleMotion.HOVER_SCALE);
        assertThat(BubbleMotion.hoverScale(BubbleMotion.HOVER_NANOS * 9, false)).isEqualTo(1f);
    }

    /** The grow must be readable as selection without visibly moving the text. */
    @Test void theHoverGrowthIsSmall() {
        assertThat(BubbleMotion.HOVER_SCALE).isBetween(1.01f, 1.08f);
    }
}
