package dev.jasper.app.plugins;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Every call from the application into plugin code goes through here: {@link Exception} and
 * {@link LinkageError} are caught, logged against the plugin and counted; other errors propagate.
 * While a callback runs on the UI thread it is remembered, so an exit deadline that fires can say
 * whose callback blocked. Safe from any thread.
 */
final class Containment {
    private static final long SLOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final BooleanSupplier onUi;
    private volatile String executing;

    Containment(BooleanSupplier onUi) { this.onUi = onUi; }

    /** Returns the contained failure, or null when the action completed. */
    Throwable attempt(String pluginId, String what, Callable<?> action) {
        boolean ui = onUi.getAsBoolean();
        String previous = executing;
        if (ui) executing = pluginId + " (" + what + ")";
        long began = System.nanoTime();
        try {
            action.call();
            return null;
        } catch (Exception | LinkageError failure) {
            record(pluginId, what, failure);
            return failure;
        } finally {
            if (ui) executing = previous;
            long took = System.nanoTime() - began;
            if (ui && took > SLOW_NANOS)
                logger(pluginId).log(System.Logger.Level.INFO, "Slow plugin callback on the UI thread: " + what
                    + " took " + TimeUnit.NANOSECONDS.toMillis(took) + " ms");
        }
    }

    boolean run(String pluginId, String what, Runnable action) {
        return attempt(pluginId, what, () -> { action.run(); return null; }) == null;
    }

    void record(String pluginId, String what, Throwable failure) {
        failures.computeIfAbsent(pluginId, id -> new AtomicInteger()).incrementAndGet();
        logger(pluginId).log(System.Logger.Level.WARNING, "Plugin " + pluginId + " failed in " + what, failure);
    }

    int failures(String pluginId) {
        AtomicInteger count = failures.get(pluginId);
        return count == null ? 0 : count.get();
    }

    String executing() { return executing; }

    static System.Logger logger(String pluginId) { return System.getLogger("dev.jasper.plugins." + pluginId); }
}
