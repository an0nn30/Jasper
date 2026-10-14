package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HeapThresholdTest {
    private static MemoryPoolMXBean named(String name) {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test void theLargestHeapPoolWithAThresholdIsChosen() {
        assertThat(HeapThreshold.pool(ManagementFactory.getMemoryPoolMXBeans())).hasValueSatisfying(pool -> {
            assertThat(pool.getType()).isEqualTo(MemoryType.HEAP);
            assertThat(pool.isCollectionUsageThresholdSupported()).isTrue();
            assertThat(pool.getUsage().getMax()).isPositive();
        });
        assertThat(HeapThreshold.pool(List.of())).isEmpty();
    }

    @Test void theThresholdIsTheFractionOfTheMaximumAndCloseReleasesIt() {
        var threshold = HeapThreshold.install(LowMemoryWatch.FRACTION, () -> { }).orElseThrow();
        MemoryPoolMXBean pool = named(threshold.poolName());
        try {
            long expected = (long) (pool.getUsage().getMax() * LowMemoryWatch.FRACTION);
            assertThat(threshold.threshold()).isEqualTo(expected);
            assertThat(pool.getCollectionUsageThreshold()).isEqualTo(expected);
        } finally {
            threshold.close();
        }
        assertThat(pool.getCollectionUsageThreshold()).isZero();
        threshold.close();
    }

    @Test void aCollectionAboveTheThresholdSignals() throws InterruptedException {
        var signalled = new CountDownLatch(1);
        // A fraction of 0 sets the smallest threshold, one byte, which any collection of the old generation exceeds.
        var threshold = HeapThreshold.install(0, signalled::countDown).orElseThrow();
        try {
            System.gc();
            assertThat(signalled.await(10, TimeUnit.SECONDS)).as("a threshold notification after a full collection").isTrue();
        } finally {
            threshold.close();
        }
    }
}
