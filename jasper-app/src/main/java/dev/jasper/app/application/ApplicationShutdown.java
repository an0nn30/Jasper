package dev.jasper.app.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * EDT-started, once-only shutdown. {@link #arm} starts an exit deadline that needs no EDT to fire,
 * because a blocked EDT callback cannot be abandoned: cleanup after it never runs, and only the
 * deadline ends the process. {@link #await} is the normal path, a bounded wait for asynchronous
 * cleanup. Termination always runs on a separate platform thread, at most once.
 */
final class ApplicationShutdown {
    private static final System.Logger LOG = System.getLogger(ApplicationShutdown.class.getName());
    private final Runnable terminate;
    private final Duration grace;
    private final Duration deadline;
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);
    private boolean armed;
    private boolean started;

    ApplicationShutdown(Runnable terminate) { this(terminate, Duration.ofSeconds(2)); }
    ApplicationShutdown(Runnable terminate, Duration grace) { this(terminate, grace, grace.multipliedBy(2)); }
    ApplicationShutdown(Runnable terminate, Duration grace, Duration deadline) {
        this.terminate = Objects.requireNonNull(terminate);
        this.grace = Objects.requireNonNull(grace);
        this.deadline = Objects.requireNonNull(deadline);
        if (grace.isNegative() || grace.isZero()) throw new IllegalArgumentException("Shutdown grace must be positive");
        if (deadline.isNegative() || deadline.isZero()) throw new IllegalArgumentException("Shutdown deadline must be positive");
    }

    /**
     * Call before any shutdown work that runs code the application does not own. {@code culprit}
     * is read off the EDT when the deadline fires and names what was running, or returns null.
     */
    void arm(Supplier<String> culprit) {
        Objects.requireNonNull(culprit);
        if (armed) return;
        armed = true;
        Thread.ofPlatform().daemon().name("jasper-exit-deadline").start(() -> {
            try {
                if (finished.await(deadline.toMillis(), TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException interrupted) { return; }
            String running = culprit.get();
            LOG.log(System.Logger.Level.WARNING, "Shutdown deadline elapsed"
                + (running == null ? "" : " while running " + running) + "; terminating");
            terminateOnce();
        });
    }

    void await(List<CompletableFuture<?>> pending) {
        if (started) return;
        started = true;
        long began = System.nanoTime();
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
            .orTimeout(grace.toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((ignored, failure) -> Thread.ofPlatform().name("jasper-exit").start(() -> {
                LOG.log(System.Logger.Level.INFO, "Shutdown finished in " + (System.nanoTime() - began) / 1_000_000
                    + " ms" + (failure == null ? "" : " (cleanup timed out)"));
                terminateOnce();
            }));
    }

    private void terminateOnce() {
        if (!terminated.compareAndSet(false, true)) return;
        try { terminate.run(); }
        finally { finished.countDown(); }
    }
}
