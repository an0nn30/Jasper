package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);

    @Test void delaysDoubleUpToThirtySecondsPerAddress() {
        assertThat(throttle.delayFor("a")).isZero();
        for (long seconds : new long[]{1, 2, 4, 8, 16, 30, 30}) {
            throttle.failed("a");
            assertThat(throttle.delayFor("a")).isEqualTo(Duration.ofSeconds(seconds));
            clock.advance(Duration.ofSeconds(seconds));
            assertThat(throttle.delayFor("a")).isZero();
        }
        assertThat(throttle.delayFor("b")).as("each address on its own").isZero();
        assertThat(throttle.lockedOut("a")).isFalse();
    }

    @Test void tenFailuresLockTheAddressOutForFiveMinutes() {
        for (int i = 0; i < 10; i++) throttle.failed("a");
        assertThat(throttle.lockedOut("a")).isTrue();
        assertThat(throttle.lockedOut("b")).isFalse();
        clock.advance(Duration.ofMinutes(4));
        assertThat(throttle.lockedOut("a")).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(throttle.lockedOut("a")).isFalse();
        assertThat(throttle.delayFor("a")).isZero();
    }

    @Test void successAndTimeForgetFailures() {
        throttle.failed("a");
        throttle.failed("a");
        throttle.succeeded("a");
        assertThat(throttle.delayFor("a")).isZero();
        throttle.failed("a");
        throttle.failed("a");
        throttle.failed("a");
        clock.advance(Duration.ofMinutes(11));
        assertThat(throttle.delayFor("a")).isZero();
        throttle.failed("a");
        assertThat(throttle.delayFor("a")).as("old failures no longer count").isEqualTo(Duration.ofSeconds(1));
    }
}
