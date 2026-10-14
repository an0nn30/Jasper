package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HeapThresholdTest {
    private static MemoryPoolMXBean named(String name) {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getName().equals(name)).findFirst().orElseThrow();
    }

    /** A pool that answers only what {@code HeapThreshold.pool} asks; anything else throws. */
    private static MemoryPoolMXBean fake(String name, MemoryType type, long max, boolean usageThreshold, boolean collectionThreshold) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getName", "toString" -> name;
            case "getType" -> type;
            case "getUsage" -> new MemoryUsage(-1, 0, 0, max);
            case "isUsageThresholdSupported" -> usageThreshold;
            case "isCollectionUsageThresholdSupported" -> collectionThreshold;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (MemoryPoolMXBean) Proxy.newProxyInstance(
            MemoryPoolMXBean.class.getClassLoader(), new Class<?>[] {MemoryPoolMXBean.class}, handler);
    }

    @Test void theOldGenerationIsChosenNotTheLargestPool() {
        long mib = 1024 * 1024;
        var eden = fake("Eden", MemoryType.HEAP, 900 * mib, false, true);
        var old = fake("Old", MemoryType.HEAP, 100 * mib, true, true);
        var metaspace = fake("Metaspace", MemoryType.NON_HEAP, 1024 * mib, true, false);
        assertThat(HeapThreshold.pool(List.of(eden, old, metaspace)).map(MemoryPoolMXBean::getName))
            .as("young pools support only collection-usage thresholds, and non-heap pools are ignored").hasValue("Old");
        var unbounded = fake("Unbounded", MemoryType.HEAP, -1, true, true);
        assertThat(HeapThreshold.pool(List.of(unbounded))).as("a pool without a maximum is never chosen").isEmpty();
        assertThat(HeapThreshold.pool(List.of(unbounded, old)).map(MemoryPoolMXBean::getName)).hasValue("Old");
    }

    @Test void theChosenPoolOfTheRealJvmIsAnOldGeneration() {
        assertThat(HeapThreshold.pool(ManagementFactory.getMemoryPoolMXBeans())).hasValueSatisfying(pool -> {
            assertThat(pool.getType()).isEqualTo(MemoryType.HEAP);
            assertThat(pool.isUsageThresholdSupported()).isTrue();
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
