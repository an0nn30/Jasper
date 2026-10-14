package dev.jasper.app.vmoptions;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * A collection-usage threshold on the old generation (spec section 5.4: the heap pool that supports both usage
 * thresholds), with a listener that calls {@code signal} on each threshold notification, on the JMX notification
 * thread. A resource: its creator closes it.
 */
public final class HeapThreshold implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HeapThreshold.class.getName());

    private final MemoryPoolMXBean pool;
    private final long threshold;
    private final NotificationEmitter emitter;
    private final NotificationListener listener;
    private boolean closed;

    private HeapThreshold(MemoryPoolMXBean pool, long threshold, NotificationEmitter emitter, NotificationListener listener) {
        this.pool = pool;
        this.threshold = threshold;
        this.emitter = emitter;
        this.listener = listener;
    }

    /**
     * Sets the old generation's collection-usage threshold to {@code fraction} of its maximum (at least one byte) and
     * listens; empty when no heap pool supports both usage thresholds and names a maximum, or the JVM refuses. A
     * {@code RuntimeException} from {@code signal} is logged and goes no further.
     */
    public static Optional<HeapThreshold> install(double fraction, Runnable signal) {
        Objects.requireNonNull(signal, "signal");
        Optional<MemoryPoolMXBean> chosen = pool(ManagementFactory.getMemoryPoolMXBeans());
        if (chosen.isEmpty()) return Optional.empty();
        MemoryPoolMXBean pool = chosen.get();
        String name = pool.getName();
        try {
            long threshold = Math.max(1, (long) (pool.getUsage().getMax() * fraction));
            var emitter = (NotificationEmitter) ManagementFactory.getMemoryMXBean();
            NotificationListener listener = (notification, handback) -> {
                if (!MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType())) return;
                if (!name.equals(MemoryNotificationInfo.from((CompositeData) notification.getUserData()).getPoolName())) return;
                // The JDK turns an exception out of a listener into an AssertionError that skips the later listeners.
                try {
                    signal.run();
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.WARNING, "The heap threshold signal failed", failure);
                }
            };
            // The threshold first: a refused one leaves no listener behind.
            pool.setCollectionUsageThreshold(threshold);
            emitter.addNotificationListener(listener, null, null);
            return Optional.of(new HeapThreshold(pool, threshold, emitter, listener));
        } catch (RuntimeException refused) {
            LOG.log(System.Logger.Level.WARNING, "Could not watch the heap pool " + name, refused);
            return Optional.empty();
        }
    }

    /**
     * Among {@code pools}, the old generation: the heap pool that supports both usage thresholds (young pools support only
     * collection-usage thresholds), with the largest maximum.
     */
    static Optional<MemoryPoolMXBean> pool(List<MemoryPoolMXBean> pools) {
        return pools.stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP
                && pool.isUsageThresholdSupported() && pool.isCollectionUsageThresholdSupported())
            .filter(pool -> pool.getUsage() != null && pool.getUsage().getMax() > 0)
            .max(Comparator.comparingLong(pool -> pool.getUsage().getMax()));
    }

    /** The watched pool's name. */
    String poolName() { return pool.getName(); }

    /** The threshold in bytes. */
    long threshold() { return threshold; }

    /** Stops listening and clears the threshold; idempotent. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            emitter.removeNotificationListener(listener);
        } catch (ListenerNotFoundException gone) {
            // Already removed.
        }
        pool.setCollectionUsageThreshold(0);
    }
}
