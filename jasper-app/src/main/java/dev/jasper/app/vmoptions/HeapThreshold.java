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
 * A collection-usage threshold on the heap pool that supports one (the old generation, spec section 5.4), with a
 * listener that calls {@code signal} on each threshold notification (on the JMX notification thread).
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
     * Sets the threshold to {@code fraction} of the pool's maximum (at least one byte) and listens; empty when no heap
     * pool supports a collection-usage threshold or names a maximum, or the JVM refuses.
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
                if (name.equals(MemoryNotificationInfo.from((CompositeData) notification.getUserData()).getPoolName())) signal.run();
            };
            emitter.addNotificationListener(listener, null, null);
            pool.setCollectionUsageThreshold(threshold);
            return Optional.of(new HeapThreshold(pool, threshold, emitter, listener));
        } catch (RuntimeException refused) {
            LOG.log(System.Logger.Level.WARNING, "Could not watch the heap pool " + name, refused);
            return Optional.empty();
        }
    }

    /** Among {@code pools}, the heap pool with a collection-usage threshold and the largest maximum. */
    static Optional<MemoryPoolMXBean> pool(List<MemoryPoolMXBean> pools) {
        return pools.stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported())
            .filter(pool -> pool.getUsage() != null && pool.getUsage().getMax() > 0)
            .max(Comparator.comparingLong(pool -> pool.getUsage().getMax()));
    }

    /** The watched pool's name. */
    public String poolName() { return pool.getName(); }

    /** The threshold in bytes. */
    public long threshold() { return threshold; }

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
