package dev.jasper.browser.page;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** A press activates its page for 5 seconds, as Chromium's transient user activation lasts. */
class LastPressTest {
    private static final int LEFT = 1;

    @Test void aPressActivatesItsPageUpToFiveSecondsAfterIt() {
        var press = new LastPress(LEFT, 0, 100_000);
        assertThat(press.withinActivation(100_000)).as("at the press").isTrue();
        assertThat(press.withinActivation(102_500)).isTrue();
        assertThat(press.withinActivation(105_000)).as("5,000 ms after it").isTrue();
    }

    @Test void aPressActivatesNothingOutsideThoseFiveSeconds() {
        var press = new LastPress(LEFT, 0, 100_000);
        assertThat(press.withinActivation(105_001)).as("past the activation").isFalse();
        assertThat(press.withinActivation(99_999)).as("before the press").isFalse();
    }

    @Test void anyInputsTimeActivatesForTheSameFiveSeconds() {
        assertThat(LastPress.withinActivation(100_000, 105_000)).isTrue();
        assertThat(LastPress.withinActivation(100_000, 105_001)).isFalse();
        assertThat(LastPress.withinActivation(100_000, 99_999)).isFalse();
        assertThat(LastPress.withinActivation(Long.MIN_VALUE, Long.MAX_VALUE)).as("never").isFalse();
    }

    @Test void noPressActivatesNothing() {
        for (long now : new long[] {0, 1, 5_000, System.currentTimeMillis(), Long.MAX_VALUE, Long.MIN_VALUE})
            assertThat(LastPress.NONE.withinActivation(now)).as("at %d", now).isFalse();
    }
}
