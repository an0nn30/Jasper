package dev.jasper.app.application;

import dev.jasper.app.workspace.DesktopTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApplicationShutdownTest {
    @Test void terminationRunsOnceOffEdtAfterPendingCleanup() throws Exception {
        var calls = new AtomicInteger();
        var onEdt = new java.util.concurrent.atomic.AtomicBoolean();
        var terminated = new CountDownLatch(1);
        var pending = new CompletableFuture<Void>();
        var shutdown = new ApplicationShutdown(() -> {
            onEdt.set(SwingUtilities.isEventDispatchThread()); calls.incrementAndGet(); terminated.countDown();
        });
        DesktopTestSupport.edt(() -> { shutdown.await(List.of(pending)); shutdown.await(List.of()); });
        assertThat(calls).hasValue(0);
        pending.complete(null);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(calls).hasValue(1);
        assertThat(onEdt).isFalse();
    }
    @Test void unfinishedCleanupCannotPreventBoundedTermination() throws Exception {
        var terminated = new CountDownLatch(1);
        var pending = new CompletableFuture<Void>();
        var shutdown = new ApplicationShutdown(terminated::countDown, Duration.ofMillis(10));
        DesktopTestSupport.edt(() -> shutdown.await(List.of(pending)));
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pending).isNotDone();
    }

    @Test void anArmedDeadlineTerminatesOffEdtEvenWhenTheEdtNeverReachesAwait() throws Exception {
        var terminated = new CountDownLatch(1);
        var onEdt = new java.util.concurrent.atomic.AtomicBoolean(true);
        var asked = new java.util.concurrent.atomic.AtomicBoolean();
        var release = new CountDownLatch(1);
        var shutdown = new ApplicationShutdown(() -> { onEdt.set(SwingUtilities.isEventDispatchThread()); terminated.countDown(); },
            Duration.ofSeconds(30), Duration.ofMillis(100));
        try {
            DesktopTestSupport.edt(() -> shutdown.arm(() -> { asked.set(true); return "test.plugin (stop)"; }));
            // A plugin's stop() that never returns: the EDT is stuck and await is never called.
            SwingUtilities.invokeLater(() -> { try { release.await(); } catch (InterruptedException ignored) { } });
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(onEdt).isFalse();
            assertThat(asked).as("the deadline names the callback that was running").isTrue();
        } finally { release.countDown(); }
    }

    @Test void normalTerminationDisarmsTheDeadlineAndTerminationRunsOnce() throws Exception {
        var calls = new AtomicInteger();
        var terminated = new CountDownLatch(1);
        var shutdown = new ApplicationShutdown(() -> { calls.incrementAndGet(); terminated.countDown(); },
            Duration.ofSeconds(5), Duration.ofMillis(150));
        DesktopTestSupport.edt(() -> { shutdown.arm(() -> null); shutdown.arm(() -> null); shutdown.await(List.of()); });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(400);
        assertThat(calls).hasValue(1);
    }
}
