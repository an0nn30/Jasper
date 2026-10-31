package dev.jasper.browser.item;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** A browser item's zoom (spec section 10): steps of 5%, kept between 5% and 500%, and Chromium's level for it. */
class ZoomTest {
    @Test void aStepMovesFivePercentAndStopsAtFiveAndFiveHundred() {
        assertThat(Zoom.step(100, 1)).isEqualTo(105);
        assertThat(Zoom.step(100, -1)).isEqualTo(95);
        assertThat(Zoom.step(7, -1)).isEqualTo(5);
        assertThat(Zoom.step(5, -1)).isEqualTo(5);
        assertThat(Zoom.step(498, 1)).isEqualTo(500);
        assertThat(Zoom.step(500, 1)).isEqualTo(500);
        assertThat(Zoom.step(133, 1)).as("a zoom off the five-percent grid steps by five").isEqualTo(138);
        assertThat(Zoom.step(100, 3)).as("one step, whatever the size of the direction").isEqualTo(105);
        assertThat(Zoom.step(100, -7)).isEqualTo(95);
        assertThat(Zoom.step(100, 0)).isEqualTo(100);
        assertThat(Zoom.MIN).isEqualTo(5);
        assertThat(Zoom.MAX).isEqualTo(500);
    }

    /** Chromium's zoom level is logarithmic, base 1.2: 0 is 100%, and each level up is 1.2 times the size. */
    @Test void chromiumsLevelIsTheLogarithmBaseOnePointTwoOfTheFactor() {
        assertThat(Zoom.cefLevel(100)).isEqualTo(0.0);
        assertThat(Zoom.cefLevel(120)).isCloseTo(1.0, within(1e-12));
        assertThat(Zoom.cefLevel(144)).isCloseTo(2.0, within(1e-12));
        assertThat(Zoom.cefLevel(50)).isCloseTo(Math.log(0.5) / Math.log(1.2), within(1e-12)).isNegative();
        assertThat(Zoom.cefLevel(5)).isFinite();
        assertThat(Zoom.cefLevel(500)).isFinite();
        for (int outside : new int[] { 4, 0, -100, 501 })
            assertThatThrownBy(() -> Zoom.cefLevel(outside)).as(Integer.toString(outside)).isInstanceOf(IllegalArgumentException.class);
    }
}
