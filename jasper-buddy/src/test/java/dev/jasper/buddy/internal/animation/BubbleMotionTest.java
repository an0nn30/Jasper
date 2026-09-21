package dev.jasper.buddy.internal.animation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BubbleMotionTest {
    /** Measured physical bottom edges in the dark recording, converted to logical displacement. */
    @Test void arrivalTracksTheReferenceFramesWithoutChangingSize() {
        long[] milliseconds = {0, 50, 100, 150, 200, 250, 300, 450, 600};
        float[] offsets = {32, 26, 16, 7.5f, 2, -0.5f, -1.5f, -0.5f, 0};
        for (int i = 0; i < milliseconds.length; i++) {
            assertThat(BubbleMotion.arrivalOffset(milliseconds[i] * 1_000_000))
                .as("reference at %d ms", milliseconds[i]).isCloseTo(offsets[i], within(0.7f));
        }
    }

    @Test void theSpringClampsBeforeStartingAndAfterSettling() {
        assertThat(BubbleMotion.arrivalOffset(-1)).isEqualTo(32f);
        assertThat(BubbleMotion.arrivalOffset(BubbleMotion.IN_NANOS)).isZero();
        assertThat(BubbleMotion.arrivalOffset(Long.MAX_VALUE)).isZero();
    }

    @Test void hoverFadesAnAffordanceInAndOut() {
        assertThat(BubbleMotion.hover(0, true)).isZero();
        assertThat(BubbleMotion.hover(BubbleMotion.HOVER_NANOS, true)).isEqualTo(1);
        assertThat(BubbleMotion.hover(0, false)).isEqualTo(1);
        assertThat(BubbleMotion.hover(BubbleMotion.HOVER_NANOS * 4, false)).isZero();
    }
}
