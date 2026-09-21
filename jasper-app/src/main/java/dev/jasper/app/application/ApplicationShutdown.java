package dev.jasper.app.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** EDT-started, once-only shutdown wait; termination always runs on a separate platform thread. */
final class ApplicationShutdown {
    private static final System.Logger LOG = System.getLogger(ApplicationShutdown.class.getName());
    private final Runnable terminate;
    private final Duration grace;
    private boolean started;

    ApplicationShutdown(Runnable terminate) { this(terminate, Duration.ofSeconds(2)); }
    ApplicationShutdown(Runnable terminate, Duration grace) {
        this.terminate = Objects.requireNonNull(terminate);
        this.grace = Objects.requireNonNull(grace);
        if (grace.isNegative() || grace.isZero()) throw new IllegalArgumentException("Shutdown grace must be positive");
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
                terminate.run();
            }));
    }
}
