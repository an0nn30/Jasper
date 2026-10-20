package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SplashAnimationTest {
    @Test void disabledMotionDoesNotStartAnAnimationWorker() throws Exception {
        var animator = new SplashAnimation(tick -> { throw new AssertionError("Motion disabled"); }, false);
        try {
            animator.start();
            assertThat(animator.awaitStopped(5, TimeUnit.SECONDS)).isTrue();
        } finally { animator.close(); }
    }

    @Test void framesRunWithoutTheEventThreadAndStopWhenClosed() throws Exception {
        var rendered = new CountDownLatch(2);
        var frames = new AtomicInteger();
        var animator = new SplashAnimation(tick -> {
            assertThat(javax.swing.SwingUtilities.isEventDispatchThread()).isFalse();
            assertThat(Thread.currentThread().isDaemon()).isTrue();
            frames.incrementAndGet(); rendered.countDown();
        });
        try {
            animator.start();
            assertThat(rendered.await(5, TimeUnit.SECONDS)).isTrue();
        } finally { animator.close(); }
        assertThat(animator.awaitStopped(5, TimeUnit.SECONDS)).isTrue();
        assertThat(frames.get()).isGreaterThanOrEqualTo(2);
        int last = frames.get();
        animator.start(); // A closed splash cannot restart its worker.
        assertThat(frames.get()).isEqualTo(last);
        animator.close();
    }

    @Test void failedPaintingStopsTheWorkerInsteadOfKeepingStartupAlive() throws Exception {
        var animator = new SplashAnimation(tick -> { throw new IllegalStateException("closed surface"); });
        try {
            animator.start();
            assertThat(animator.awaitStopped(5, TimeUnit.SECONDS)).isTrue();
        } finally { animator.close(); }
    }
}
