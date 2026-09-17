package dev.jasper.app;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandNoticeTest {
    private static final Duration TEN = Duration.ofSeconds(10);

    private static boolean notify(boolean anyActive, boolean ownActive, boolean ownSelected, Duration ran) {
        return CommandNotice.shouldNotify(new CommandNotice.Origin(anyActive, ownActive, ownSelected), ran, TEN);
    }

    @Test void nothingShorterThanTheThresholdNotifies() {
        assertThat(notify(false, false, false, Duration.ofSeconds(9))).isFalse();
        assertThat(notify(false, false, false, Duration.ZERO)).isFalse();
        assertThat(notify(false, false, false, TEN)).as("exactly the threshold counts").isTrue();
    }

    @Test void aZeroThresholdDisablesTheFeatureEntirely() {
        assertThat(CommandNotice.shouldNotify(new CommandNotice.Origin(false, false, false),
            Duration.ofHours(1), Duration.ZERO)).isFalse();
    }

    /** Jasper is not on screen at all, so even the tab you left selected is out of sight. */
    @Test void anythingNotifiesWhileJasperIsInTheBackground() {
        assertThat(notify(false, false, true, TEN)).isTrue();
        assertThat(notify(false, false, false, TEN)).isTrue();
    }

    @Test void theTabYouAreLookingAtNeverNotifies() {
        assertThat(notify(true, true, true, TEN)).isFalse();
    }

    @Test void anotherTabOfTheActiveWindowNotifies() {
        assertThat(notify(true, true, false, TEN)).isTrue();
    }

    /**
     * Deliberate: a command finishing in a different Jasper window, while a Jasper window has focus,
     * stays quiet. You are still looking at Jasper and that window's tab strip already shows it.
     */
    @Test void anotherWindowStaysQuietWhileOneIsFocused() {
        assertThat(notify(true, false, false, TEN)).isFalse();
        assertThat(notify(true, false, true, TEN)).isFalse();
    }

    @Test void everyCombinationIsDecidedWithoutAWindow() {
        for (boolean any : new boolean[] {true, false})
            for (boolean own : new boolean[] {true, false})
                for (boolean selected : new boolean[] {true, false})
                    assertThat(notify(any, own, selected, TEN))
                        .as("any=%s own=%s selected=%s", any, own, selected)
                        .isEqualTo(!any || (own && !selected));
    }
}
