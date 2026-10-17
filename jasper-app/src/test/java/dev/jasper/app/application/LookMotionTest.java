package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.Motion;
import org.junit.jupiter.api.Test;

/** Spec 10.9: the look's motion and the setting, folded once. */
class LookMotionTest {
    @Test void aStillLookNeverMoves() {
        assertThat(new LookMotion(Motion.NONE).allows(true)).isFalse();
        assertThat(new LookMotion(Motion.NONE).allows(false)).isFalse();
    }

    @Test void theDefaultLookLeavesItToTheSetting() {
        assertThat(LookMotion.STANDARD.allows(true)).isTrue();
        assertThat(LookMotion.STANDARD.allows(false)).isFalse();
    }
}
