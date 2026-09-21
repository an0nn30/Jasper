package dev.jasper.app;

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
}
