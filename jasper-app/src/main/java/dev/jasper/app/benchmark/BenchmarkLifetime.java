package dev.jasper.app.benchmark;

import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Off-EDT waits and unconditional cleanup, also usable with headless ownership fakes. */
final class BenchmarkLifetime implements AutoCloseable {
    private final long deadline;
    private final Runnable cleanup;
    BenchmarkLifetime(long millis, Runnable cleanup) {
        deadline = System.nanoTime() + millis * 1_000_000L; this.cleanup = cleanup;
    }
    void await(BooleanSupplier ready, String description) throws InterruptedException, TimeoutException {
        while (!ready.getAsBoolean()) { check(description); Thread.sleep(10); }
        check(description);
    }
    void check(String description) throws TimeoutException {
        if (System.nanoTime() >= deadline) throw new TimeoutException(description);
    }
    @Override public void close() { cleanup.run(); }
}
