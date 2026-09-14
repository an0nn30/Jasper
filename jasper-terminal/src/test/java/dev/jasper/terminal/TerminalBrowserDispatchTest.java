package dev.jasper.terminal;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalBrowserDispatchTest {
    @Test void slowDesktopActionLeavesEdtResponsiveAndQueueRejectsExcessWithoutCallerRuns() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var done = new CountDownLatch(8);
        var edtResponsive = new AtomicBoolean();
        var ran = new AtomicInteger();
        List<LogRecord> records = new ArrayList<>();
        var logger = Logger.getLogger(TerminalView.class.getName());
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        boolean oldParents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            SwingUtilities.invokeAndWait(() -> dispatch(() -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
                entered.countDown();
                await(release);
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < 9; i++) dispatch(() -> {
                    assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
                    ran.incrementAndGet();
                    done.countDown();
                });
                edtResponsive.set(true);
            });
            assertThat(edtResponsive).isTrue();
            assertThat(ran).hasValue(0);
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().getThrown()).isNull();
            release.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(ran).hasValue(8);
        } finally {
            release.countDown();
            logger.removeHandler(handler);
            logger.setUseParentHandlers(oldParents);
        }
    }

    @Test void failingDesktopActionUsesFixedDiagnosticsAndLaterActionsStillRun() throws Exception {
        List<LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();
        var logger = Logger.getLogger(TerminalView.class.getName());
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        boolean oldParents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        var done = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                dispatch(() -> { throw new IllegalArgumentException("https://private.example/secret"); });
                dispatch(done::countDown);
            });
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().getMessage()).doesNotContain("private", "secret");
            assertThat(records.getFirst().getThrown()).isNull();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(oldParents);
        }
    }

    @Test void defaultBrowserOpenerChecksDesktopSupportOffTheEdt() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.awt.GraphicsEnvironment.isHeadless());
        var received = new CountDownLatch(1);
        var offEdt = new AtomicBoolean();
        var logger = Logger.getLogger(TerminalView.class.getName());
        var handler = new Handler() {
            @Override public void publish(LogRecord record) {
                offEdt.set(!SwingUtilities.isEventDispatchThread());
                received.countDown();
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        boolean oldParents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var method = TerminalView.class.getDeclaredMethod("openInBrowser", String.class);
                    method.setAccessible(true);
                    method.invoke(null, "https://example.test");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(offEdt).isTrue();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(oldParents);
        }
    }

    private static void dispatch(Runnable action) {
        try {
            var method = TerminalView.class.getDeclaredMethod("dispatchBrowserAction", Runnable.class);
            method.setAccessible(true);
            method.invoke(null, action);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
}
