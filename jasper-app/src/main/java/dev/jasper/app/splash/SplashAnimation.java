package dev.jasper.app.splash;

import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/** Owns the splash's bounded-rate daemon. Its callback must not post work to the busy startup EDT. */
final class SplashAnimation implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(SplashAnimation.class.getName());
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1,
        Thread.ofPlatform().daemon().name("jasper-splash-animation").factory());
    private final IntConsumer paint;
    private final boolean enabled;
    private boolean started;
    private int tick;

    SplashAnimation(IntConsumer paint) { this(paint, true); }

    SplashAnimation(IntConsumer paint, boolean enabled) {
        this.enabled = enabled;
        this.paint = Objects.requireNonNull(paint, "paint");
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        worker.setRemoveOnCancelPolicy(true);
    }

    synchronized void start() {
        if (started || worker.isShutdown()) return;
        if (!enabled) { worker.shutdown(); return; }
        started = true;
        worker.scheduleWithFixedDelay(() -> {
            try { paint.accept(++tick); }
            catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Splash animation stopped after a paint failure", failure);
                worker.shutdown();
            }
        }, 220, 220, TimeUnit.MILLISECONDS);
    }

    /** Cancels future frames without waiting on startup or on the AWT event thread. */
    @Override public synchronized void close() { worker.shutdownNow(); }

    boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }
}
