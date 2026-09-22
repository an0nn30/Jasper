package dev.jasper.vault.lock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InactivityTimerTest {
    long now = 1_000_000;

    @Test void expiresAfterTheTimeoutWithoutActivity() {
        var timer = new InactivityTimer(() -> now);
        timer.setTimeout(Duration.ofMinutes(2));
        assertThat(timer.expired()).isFalse();
        now += Duration.ofMinutes(1).toMillis();
        assertThat(timer.remaining()).isEqualTo(Duration.ofMinutes(1));
        timer.touch();
        now += Duration.ofMinutes(1).toMillis() + 59_000;
        assertThat(timer.expired()).isFalse();
        now += 1_000;
        assertThat(timer.expired()).isTrue();
        assertThat(timer.remaining()).isEqualTo(Duration.ZERO);
    }

    @Test void zeroMeansNever() {
        var timer = new InactivityTimer(() -> now);
        timer.setTimeout(Duration.ZERO);
        now += Duration.ofDays(3).toMillis();
        assertThat(timer.expired()).isFalse();
        assertThat(timer.remaining()).isEqualTo(Duration.ZERO);
        assertThat(timer.timeout()).isEqualTo(Duration.ZERO);
    }
}
