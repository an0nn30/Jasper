package dev.jasper.terminal.testsupport;

import java.time.Duration;
import java.util.function.BooleanSupplier;

public final class Await {
    private Await() {
    }

    public static void until(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for: " + description);
            }
            Thread.sleep(10);
        }
    }
}
