package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LowMemoryWatchTest {
    private final MutableClock clock = new MutableClock();
    private final LowMemoryWatch watch = new LowMemoryWatch(clock);

    /** Signals {@code count} times, {@code apart} apart; returns which ones raised the warning. */
    private List<Integer> signals(int count, Duration apart) {
        var raised = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            if (i > 0) clock.advance(apart);
            if (watch.signal()) raised.add(i);
        }
        return raised;
    }

    @Test void theSixthSignalWithinFifteenMinutesWarnsOnce() {
        assertThat(signals(10, Duration.ofSeconds(20))).containsExactly(5);
    }

    @Test void signalsCloserThanTwentySecondsAreDropped() {
        // At 0, 10, 20 … 100 s only every other signal counts, so the sixth counted one is the eleventh.
        assertThat(signals(11, Duration.ofSeconds(10))).containsExactly(10);
        var storm = new LowMemoryWatch(clock);
        int raised = 0;
        for (int i = 0; i < 600; i++) {
            clock.advance(Duration.ofSeconds(1));
            if (storm.signal()) raised++;
        }
        assertThat(raised).as("a garbage-collection storm warns exactly once").isEqualTo(1);
    }

    @Test void signalsSpreadOverMoreThanFifteenMinutesNeverWarn() {
        assertThat(signals(30, Duration.ofMinutes(4))).isEmpty();
    }

    @Test void anOutOfMemoryErrorWarnsAtOnceButOnlyOnce() {
        assertThat(watch.outOfMemory()).isTrue();
        assertThat(watch.outOfMemory()).isFalse();
        assertThat(signals(10, Duration.ofSeconds(20))).as("already warned this session").isEmpty();
    }

    @Test void changingTheHeapRearmsTheWarning() {
        assertThat(signals(6, Duration.ofSeconds(20))).containsExactly(5);
        assertThat(watch.outOfMemory()).isFalse();
        watch.heapChanged();
        clock.advance(Duration.ofSeconds(20));
        assertThat(signals(6, Duration.ofSeconds(20))).as("the count starts again").containsExactly(5);
        watch.heapChanged();
        assertThat(watch.outOfMemory()).isTrue();
    }
}
