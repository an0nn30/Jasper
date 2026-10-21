package dev.jasper.editor.markdown.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("try")
class ImageBudgetTest {
    @Test void reserveCountsInflightPixelsAndReleaseIsIdempotent() {
        var budget = new ImageBudget(64L * 1024 * 1024);
        var lease = budget.reserve(4000, 4000).orElseThrow();
        assertEquals(64_000_000L, budget.usedBytes());
        assertTrue(budget.reserve(1000, 1000).isEmpty());
        lease.close(); lease.close();
        assertEquals(0, budget.usedBytes());
        try (var smaller = budget.reserve(1000, 1000).orElseThrow()) { assertEquals(4_000_000, budget.usedBytes()); }
    }
    @Test void dimensionsCannotOverflowOrExceedPixelLimit() {
        var budget = new ImageBudget(Long.MAX_VALUE);
        assertTrue(budget.reserve(Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
        assertTrue(budget.reserve(4001, 4000).isEmpty());
        assertTrue(budget.reserve(0, 2).isEmpty());
        assertTrue(budget.reserve(-1, 2).isEmpty());
        assertEquals(0, budget.usedBytes());
    }
    @Test void parallelReservationsNeverOversubscribe() throws Exception {
        var budget = new ImageBudget(400);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var leases = pool.invokeAll(java.util.stream.IntStream.range(0, 50)
                .<java.util.concurrent.Callable<java.util.Optional<ImageBudget.Lease>>>mapToObj(i -> () -> budget.reserve(10,10)).toList());
            assertEquals(1, leases.stream().filter(f -> { try { return f.get().isPresent(); } catch (Exception e) { throw new AssertionError(e); } }).count());
            for (var lease : leases) lease.get().ifPresent(ImageBudget.Lease::close);
        }
        assertEquals(0, budget.usedBytes());
    }
}
