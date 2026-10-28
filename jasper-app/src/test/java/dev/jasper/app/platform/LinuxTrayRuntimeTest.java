package dev.jasper.app.platform;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LinuxTrayRuntimeTest {
    private static final LinuxStatusNotifier.Host HOST = new LinuxStatusNotifier.Host("org.kde.StatusNotifierWatcher", ":1.10", true);

    @Test void coldConstructionDoesNotStartDiscoveryAndUsesOneDaemonScheduler() throws Exception {
        try (var f = new Fixture()) {
            assertThat(f.events).isEmpty(); assertThat(f.scheduler.getPoolSize()).isZero();
            f.runtime.start(); f.settle();
            assertThat(f.events).containsExactly("modern.start", "legacy.start");
            assertThat(f.scheduler.getCorePoolSize()).isEqualTo(1);
            assertThat(f.scheduler.submit(() -> Thread.currentThread().isDaemon()).get()).isTrue();
            assertThat(f.scheduler.getQueue()).hasSize(1);
        }
    }
    @Test void earlyFactoryObservationsAreRetainedUntilControllerExists() throws Exception {
        try (var f = new Fixture(true)) {
            f.settle(); assertThat(f.events).as("even factory observations remain cold until installation starts").isEmpty();
            f.runtime.start(); f.settle();
            assertThat(f.events).contains("modern.add");
            assertThat(f.events).doesNotContain("legacy.add");
        }
    }
    @Test void confirmedPublicationCancelsProbeAndWithdrawalResumesWithFreshSnapshot() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.start(); f.observations.get().legacy().accept(true); f.settle();
            assertThat(f.events).endsWith("legacy.add");
            IntConsumer stale = f.legacyAction.get();
            f.observations.get().host().accept(HOST); f.settle();
            assertThat(f.events).endsWith("legacy.remove", "modern.add", "discovery false");
            assertThat(f.scheduler.getQueue()).isEmpty();
            stale.accept(1); f.settle(); assertThat(f.events).doesNotContain("action");
            f.observations.get().host().accept(null); f.settle();
            assertThat(f.events).endsWith("modern.remove", "discovery true", "probe", "legacy.add");
            assertThat(f.scheduler.getQueue()).hasSize(1);
        }
    }
    @Test void successfulModernReplacementCancelsRegistrationRetryAndProbe() throws Exception {
        try (var f = new Fixture()) {
            f.modernSucceeds = false; f.runtime.start();
            f.observations.get().legacy().accept(true); f.observations.get().host().accept(HOST); f.settle();
            assertThat(f.events).containsSubsequence("modern.add", "modern.remove", "legacy.add");
            assertThat(f.events).doesNotContain("discovery false");
            assertThat(f.scheduler.getQueue()).hasSize(2);
            f.modernSucceeds = true;
            f.observations.get().host().accept(new LinuxStatusNotifier.Host("org.kde.StatusNotifierWatcher", ":1.11", true)); f.settle();
            assertThat(f.events).endsWith("legacy.remove", "modern.add", "discovery false");
            assertThat(f.scheduler.getQueue()).isEmpty();
        }
    }
    @Test void failedWithdrawalDoesNotResumeDiscoveryOrPublishLegacy() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.start(); f.observations.get().legacy().accept(true);
            f.observations.get().host().accept(HOST); f.settle();
            f.failWithdraw = true; f.observations.get().host().accept(null); f.settle();
            assertThat(f.events).doesNotContain("discovery true", "legacy.add");
            assertThat(f.scheduler.getQueue()).hasSize(1); // retry only
        }
    }
    @Test void restorationPhysicallyRemovesReconnectTimer() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.start(); f.observations.get().connected().accept(false); f.settle();
            assertThat(f.scheduler.getQueue()).hasSize(2); // probe + reconnect
            f.observations.get().connected().accept(true); f.settle();
            assertThat(f.scheduler.getQueue()).hasSize(1);
        }
    }
    @Test void closeOnEdtInvalidatesAndReturnsWhileNativeCleanupIsBlocked() throws Exception {
        var f = new Fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            f.runtime.start(); f.observations.get().host().accept(HOST); f.settle();
            f.withdraw = () -> { entered.countDown(); await(release); };
            f.observations.get().host().accept(null);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofMillis(500), () -> SwingUtilities.invokeAndWait(f.runtime::close));
            assertThat(f.events).contains("popup.close", "modern.close", "legacy.close");
            assertThat(f.scheduler.getQueue().stream().noneMatch(task -> task instanceof java.util.concurrent.Delayed delayed
                && delayed.getDelay(TimeUnit.MILLISECONDS) > 100)).isTrue();
        } finally { release.countDown(); f.close(); }
        assertThat(f.scheduler.isTerminated()).isTrue();
    }
    @Test void closeBeforeQueuedDiscoveryAndLateCallbacksCannotPublish() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.close(); f.runtime.start();
            f.observations.get().legacy().accept(true); f.observations.get().host().accept(HOST);
            assertThat(f.scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(f.events).containsExactly("popup.close", "modern.close", "legacy.close");
            assertThat(f.scheduler.getQueue()).isEmpty();
        }
    }
    @Test void bothCleanupFailuresStillCancelTimersAndCloseTheOtherAdapter() throws Exception {
        var f = new Fixture(); f.failClose = true;
        f.runtime.start(); f.settle(); f.close();
        assertThat(f.events).endsWith("popup.close", "modern.close", "legacy.close");
        assertThat(f.scheduler.getQueue()).isEmpty(); assertThat(f.scheduler.isTerminated()).isTrue();
    }
    @Test void heldAuthorityWorkerCannotDelayModernPublicationOrQuit() throws Exception {
        LinuxTrayXcbTest.nativeOnly();
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("/tmp"), "jtr-");
        var authority = java.nio.file.Files.write(directory.resolve("auth"), new byte[10]);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var cancelHolder = new AtomicReference<LinuxTrayXcb.Cancellation>();
        var auditHolder = new AtomicReference<LinuxTrayXcbTest.Audit>();
        var workerFailure = new AtomicReference<Throwable>(); var workerHolder = new AtomicReference<Thread>();
        var scheduler = LinuxTrayRuntime.scheduler();
        try (var bus = LinuxTestBus.start(directory, false); var server = LinuxX11TestServer.filesystem();
             var watcherConnection = org.freedesktop.dbus.connections.impl.DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build()) {
            var watcher = new LinuxTrayWireTest.Watcher(false);
            watcherConnection.exportObject("/StatusNotifierWatcher", watcher); watcherConnection.requestBusName(LinuxTrayDbus.WATCHER);
            var runtime = new LinuxTrayRuntime(new ResidentTray.Actions(() -> {}, () -> {}, () -> {}, () -> {}), scheduler,
                SwingUtilities::invokeLater, callbacks -> {
                    var modern = new LinuxStatusNotifier(bus.address(), callbacks.host(), callbacks.connected());
                    return new LinuxResidentTray.Modern(modern::publish, modern::withdrawAndAwait, modern::close, modern::start);
                }, callbacks -> new LinuxTrayRuntime.LegacyAdapter(new LinuxResidentTray.Legacy(action -> false, () -> {}, () -> {
                    var cancel = cancelHolder.get(); if (cancel != null) cancel.requestClose();
                }), () -> workerHolder.set(Thread.ofPlatform().daemon().name("jasper-held-runtime-authority").start(() -> {
                    try (var cancel = new LinuxTrayXcb.Cancellation()) {
                        cancelHolder.set(cancel); var audit = new LinuxTrayXcbTest.Audit(cancel); auditHolder.set(audit);
                        audit.before = (name, args) -> { if (name.equals("open")) { entered.countDown(); await(release); } };
                        try (var connection = LinuxTrayXcb.connect(java.util.Map.of("DISPLAY", server.address(), "XAUTHORITY", authority.toString()), LinuxTrayXcbTest.deadline(), cancel)) {
                            throw new AssertionError("late authority result connected to " + connection.setup().root());
                        }
                    } catch (Throwable failure) { workerFailure.set(failure); }
                })), () -> {}, enabled -> {}), () -> {});
            try {
                runtime.start(); assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                watcher.host = true;
                watcherConnection.sendMessage(new LinuxTrayDbus.Watcher.StatusNotifierHostRegistered("/StatusNotifierWatcher"));
                assertThat(watcher.registrations.poll(3, TimeUnit.SECONDS)).startsWith(":");
                assertThat(workerHolder.get().isAlive()).isTrue();
                assertThat(auditHolder.get().connects).hasValue(0);
                assertTimeoutPreemptively(Duration.ofMillis(500), () -> SwingUtilities.invokeAndWait(runtime::close));
                assertThat(scheduler.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
                assertThat(workerHolder.get().isAlive()).isTrue();
            } finally {
                runtime.close(); release.countDown();
                var worker = workerHolder.get(); if (worker != null) worker.join(2000);
            }
            assertThat(workerHolder.get().isAlive()).isFalse();
            assertThat(workerFailure.get()).isInstanceOf(LinuxTrayXcb.Unavailable.class);
            assertThat(auditHolder.get().connects).hasValue(0); auditHolder.get().clean();
            assertThat(server.bytesRead()).isZero();
        } finally {
            release.countDown(); scheduler.shutdownNow();
            java.nio.file.Files.deleteIfExists(authority); java.nio.file.Files.deleteIfExists(directory.resolve("bus")); java.nio.file.Files.delete(directory);
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }
    private static final class Fixture implements AutoCloseable {
        final List<String> events = new CopyOnWriteArrayList<>();
        final AtomicReference<LinuxTrayRuntime.Observations> observations = new AtomicReference<>();
        final AtomicReference<IntConsumer> legacyAction = new AtomicReference<>();
        final ScheduledThreadPoolExecutor scheduler = LinuxTrayRuntime.scheduler();
        volatile boolean failWithdraw, failClose, modernSucceeds = true;
        volatile Runnable withdraw = () -> { };
        final LinuxTrayRuntime runtime;
        Fixture() { this(false); }
        Fixture(boolean early) {
            runtime = new LinuxTrayRuntime(new ResidentTray.Actions(() -> events.add("action"), () -> {}, () -> {}, () -> {}),
                scheduler, Runnable::run, callbacks -> {
                    observations.set(callbacks);
                    if (early) callbacks.host().accept(HOST);
                    return new LinuxResidentTray.Modern((host, action) -> { events.add("modern.add"); return modernSucceeds; },
                        () -> { events.add("modern.remove"); withdraw.run(); if (failWithdraw) throw new IllegalStateException("withdraw"); },
                        () -> { events.add("modern.close"); if (failClose) throw new IllegalStateException("modern close"); },
                        () -> events.add("modern.start"));
                }, callbacks -> new LinuxTrayRuntime.LegacyAdapter(new LinuxResidentTray.Legacy(action -> {
                    events.add("legacy.add"); legacyAction.set(action); return true;
                }, () -> events.add("legacy.remove"), () -> {
                    events.add("legacy.close"); if (failClose) throw new IllegalStateException("legacy close");
                }), () -> events.add("legacy.start"), () -> events.add("probe"), enabled -> events.add("discovery " + enabled)),
                () -> events.add("popup.close"));
        }
        void settle() throws Exception {
            for (int i = 0; i < 3; i++) scheduler.submit(() -> {}).get(2, TimeUnit.SECONDS);
        }
        @Override public void close() {
            runtime.close();
            try { assertThat(scheduler.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
    }
}
