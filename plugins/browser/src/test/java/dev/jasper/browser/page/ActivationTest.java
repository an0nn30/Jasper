package dev.jasper.browser.page;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page's user activation, as Chromium keeps one: a press or key activates the page for 5 seconds, and each popup that
 * opens consumes it, so one gesture opens one popup; the next press or key activates it again.
 */
class ActivationTest {
    @Test void aPressOrKeyActivatesThePageForFiveSeconds() {
        var activation = new Activation();
        assertThat(activation.active(100_000)).as("nothing yet").isFalse();
        activation.activate(100_000);
        assertThat(activation.active(100_000)).isTrue();
        assertThat(activation.active(105_000)).as("5,000 ms after").isTrue();
        assertThat(activation.active(105_001)).as("5,001 ms after").isFalse();
        assertThat(activation.active(99_999)).as("before it").isFalse();
    }

    @Test void aPopupConsumesTheActivationAndTheNextPressOrKeyBringsItBack() {
        var activation = new Activation();
        activation.activate(100_000);
        assertThat(activation.consume(101_000)).isTrue();
        assertThat(activation.active(101_000)).isFalse();
        assertThat(activation.consume(101_000)).as("a second popup").isFalse();
        activation.activate(102_000);
        assertThat(activation.consume(102_500)).as("after a new press").isTrue();
        assertThat(activation.consume(102_500)).isFalse();
    }

    /** Plan 8's L38: the activation tells whether a press is still what last activated it, unconsumed. */
    @Test void theActivationTellsWhetherAPressIsStillWhatLastActivatedIt() {
        var activation = new Activation();
        assertThat(activation.lastActivatedAt(Long.MIN_VALUE)).as("never").isFalse();
        activation.activate(100_000);
        assertThat(activation.lastActivatedAt(100_000)).isTrue();
        assertThat(activation.lastActivatedAt(99_000)).as("an earlier press").isFalse();
        activation.activate(100_500);
        assertThat(activation.lastActivatedAt(100_000)).as("a key after it").isFalse();
        assertThat(activation.consume(101_000)).isTrue();
        assertThat(activation.lastActivatedAt(100_500)).as("consumed").isFalse();
        assertThat(activation.lastActivatedAt(Long.MIN_VALUE)).as("still never").isFalse();
    }

    /** Plan 8's L42: an activation counts since a moment only when its press or key came after that moment. */
    @Test void anActivationCountsSinceAMomentOnlyWhenItCameAfterIt() {
        var activation = new Activation();
        assertThat(activation.activeSince(Long.MIN_VALUE, 100_000)).as("nothing yet").isFalse();
        activation.activate(100_000);
        assertThat(activation.activeSince(Long.MIN_VALUE, 100_000)).as("since any moment").isTrue();
        assertThat(activation.activeSince(99_999, 105_000)).as("after the moment, within 5 s").isTrue();
        assertThat(activation.activeSince(100_000, 100_500)).as("at the moment itself").isFalse();
        assertThat(activation.activeSince(99_999, 105_001)).as("past 5 s").isFalse();
        assertThat(activation.active(100_500)).as("the activation itself, unchanged").isTrue();
    }

    @Test void anExpiredActivationConsumesNothing() {
        var activation = new Activation();
        assertThat(activation.consume(0)).isFalse();
        activation.activate(100_000);
        assertThat(activation.consume(105_001)).isFalse();
        assertThat(activation.consume(104_000)).as("still there for a popup within its time").isTrue();
    }

    /** Chromium asks on its own thread, and a page's script bridge may ask at once: still one popup per gesture. */
    @Test void onlyOneOfManyThreadsConsumesOneActivation() throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 200; round++) {
                var activation = new Activation();
                activation.activate(100_000);
                var start = new CountDownLatch(1);
                var opened = new AtomicInteger();
                var tasks = new java.util.ArrayList<Future<?>>();
                for (int i = 0; i < 8; i++) tasks.add(threads.submit(() -> {
                    start.await();
                    if (activation.consume(100_500)) opened.incrementAndGet();
                    return null;
                }));
                start.countDown();
                for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
                assertThat(opened.get()).as("round %d", round).isEqualTo(1);
            }
        } finally {
            threads.shutdownNow();
        }
    }
}
