package dev.jasper.browser.item;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan 4b's P47: at most so many source pages are built at once; the rest wait their turn, in order, holding no thread,
 * and one cancelled while it waits is never built. A failed or refused build leaves its turn.
 */
class SourceBuildsTest {
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> started = new CopyOnWriteArrayList<>();

    @AfterEach void stop() {
        release.countDown();
        threads.shutdownNow();
    }

    @Test void atMostTwoBuildAtOnceAndTheRestWaitTheirTurn() throws Exception {
        var builds = new SourceBuilds(2, threads);
        CompletableFuture<String> a = builds.build(held("a")), b = builds.build(held("b"));
        CompletableFuture<String> c = builds.build(held("c")), d = builds.build(held("d"));
        awaitStarted(2);
        assertThat(started).containsExactlyInAnyOrder("a", "b");
        Thread.sleep(50);
        assertThat(started).as("c and d wait").hasSize(2);
        release.countDown();
        assertThat(List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS), c.get(5, TimeUnit.SECONDS), d.get(5, TimeUnit.SECONDS)))
            .containsExactly("a", "b", "c", "d");
        assertThat(started.subList(2, 4)).containsExactlyInAnyOrder("c", "d");
    }

    /** With one turn, the waiting builds go in the order they came. */
    @Test void aBuildCancelledWhileItWaitsIsNeverBuiltAndLeavesItsTurn() throws Exception {
        var builds = new SourceBuilds(1, threads);
        CompletableFuture<String> first = builds.build(held("first"));
        CompletableFuture<String> cancelled = builds.build(held("cancelled")), next = builds.build(held("next"));
        CompletableFuture<String> last = builds.build(held("last"));
        awaitStarted(1);
        assertThat(cancelled.cancel(true)).isTrue();
        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("next");
        assertThat(last.get(5, TimeUnit.SECONDS)).isEqualTo("last");
        assertThat(started).containsExactly("first", "next", "last");
    }

    @Test void aFailedBuildFailsItsFutureAndLeavesItsTurn() throws Exception {
        var builds = new SourceBuilds(1, threads);
        CompletableFuture<String> failed = builds.build(() -> { throw new IllegalStateException("broken"); });
        assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(builds.build(() -> "after").get(5, TimeUnit.SECONDS)).isEqualTo("after");
    }

    /** An executor that no longer takes work fails the build, as the plugin's does once it stops, and frees its turn. */
    @Test void aRefusedBuildFailsItsFutureAndLeavesItsTurn() throws Exception {
        var refuseOnce = new AtomicBoolean(true);
        Executor refusing = task -> {
            if (refuseOnce.getAndSet(false)) throw new RejectedExecutionException("stopped");
            threads.execute(task);
        };
        var builds = new SourceBuilds(1, refusing);
        assertThatThrownBy(() -> builds.build(() -> "refused").get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(RejectedExecutionException.class);
        assertThat(builds.build(() -> "after").get(5, TimeUnit.SECONDS)).isEqualTo("after");
    }

    /** A build that records its start and holds until the test releases it. */
    private Supplier<String> held(String name) {
        return () -> {
            started.add(name);
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("never released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return name;
        };
    }

    private void awaitStarted(int count) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (started.size() < count && System.nanoTime() < end) Thread.sleep(5);
        assertThat(started).hasSizeGreaterThanOrEqualTo(count);
    }
}
