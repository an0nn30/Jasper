package dev.jasper.app.platform;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LinuxResidentTrayTest {
    private static final LinuxStatusNotifier.Host A = host(":1.10");
    private static final LinuxStatusNotifier.Host B = host(":1.11");

    @Test void transitionsWithdrawBeforeReplacingAndCloseOnce() {
        var f = new Fixture();
        f.controller.legacyChanged(true); f.drain();
        assertThat(f.events).containsExactly("legacy.add");
        f.controller.hostChanged(A); f.drain();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove", "modern.add :1.10");
        f.controller.hostChanged(B); f.drain();
        assertThat(f.events).endsWith("modern.remove", "modern.add :1.11");
        f.controller.hostChanged(null); f.drain();
        assertThat(f.events).endsWith("modern.remove", "legacy.add");
        f.controller.close(); f.controller.close(); f.drain();
        assertThat(f.events).endsWith("legacy.remove", "modern.close", "legacy.close");
        assertThat(f.events.stream().filter("modern.close"::equals).count()).isEqualTo(1);
    }

    @Test void failedModernRegistrationRestoresLegacy() {
        var f = new Fixture();
        f.controller.legacyChanged(true); f.drain();
        f.modernSucceeds = false;
        f.controller.hostChanged(A); f.drain();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove", "modern.add :1.10",
            "modern.remove", "legacy.add");
        assertThat(f.delays).containsExactly(Duration.ofSeconds(1));
        f.controller.legacyChanged(true); f.controller.hostChanged(A); f.drain();
        assertThat(f.events.stream().filter("modern.add :1.10"::equals).count()).isEqualTo(1);
        assertThat(f.timers).hasSize(1);
    }

    @Test void noHostRetainsDiscoveryWithoutPublishing() {
        var f = new Fixture();
        f.controller.hostChanged(null); f.controller.legacyChanged(false); f.drain();
        assertThat(f.events).isEmpty(); assertThat(f.timers).isEmpty();
        f.controller.hostChanged(A); f.drain();
        assertThat(f.events).containsExactly("modern.add :1.10");
    }

    @Test void oldOwnerReplyCannotPublish() throws Exception {
        var f = new Fixture();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        f.inModern = () -> { entered.countDown(); await(release); };
        f.controller.hostChanged(A);
        Thread worker = Thread.ofPlatform().start(f::drain);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            f.controller.hostChanged(B);
        } finally { release.countDown(); worker.join(2000); }
        assertThat(worker.isAlive()).isFalse();
        assertThat(f.events).containsExactly("modern.add :1.10", "modern.remove", "modern.add :1.11");
        f.controller.hostChanged(B); f.drain();
        assertThat(f.events).hasSize(3);
    }

    @Test void closedGenerationDropsAlreadyQueuedClick() {
        var f = new Fixture(); f.controller.hostChanged(A); f.drain();
        f.modernClick.accept(1); f.controller.close(); f.drainEdt(); f.drain();
        assertThat(f.actions.get()).isZero();
    }

    @Test void queuedClickFromWithdrawnBackendIsIgnored() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.drain();
        f.legacyClick.accept(1); f.controller.hostChanged(A);
        // Admission invalidates the click even before the worker gets the replacement event.
        f.drainEdt(); f.drain();
        f.modernClick.accept(1); f.controller.hostChanged(B); f.drainEdt(); f.drain();
        assertThat(f.actions.get()).isZero();
    }

    @Test void duplicateHealthySnapshotsKeepActionsLive() {
        var f = new Fixture(); f.controller.hostChanged(A); f.drain();
        f.modernClick.accept(1); f.controller.hostChanged(A); f.controller.legacyChanged(true);
        f.drain(); f.drainEdt();
        assertThat(f.actions.get()).isEqualTo(1);
        assertThat(f.events).containsExactly("modern.add :1.10");
    }

    @Test void legacySupportLossInvalidatesQueuedActionBeforeRemoval() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.drain();
        f.legacyClick.accept(1); f.controller.legacyChanged(false); f.drainEdt(); f.drain();
        assertThat(f.actions.get()).isZero();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove");
    }

    @Test void lateLegacySupportLossRemovesInFlightAdd() {
        var f = new Fixture();
        f.inLegacy = () -> f.controller.legacyChanged(false);
        f.controller.legacyChanged(true); f.drain();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove");
        f.legacyClick.accept(1); f.drainEdt(); assertThat(f.actions.get()).isZero();
    }

    @Test void busDisconnectUsesLegacyAndRetriesWithCap() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.controller.hostChanged(A); f.drain();
        f.controller.connectionLost(); f.drain();
        assertThat(f.events).endsWith("modern.remove", "legacy.add");
        for (int i = 0; i < 6; i++) { f.fireTimer(); f.controller.connectionLost(); f.drain(); }
        assertThat(f.delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4),
            Duration.ofSeconds(8), Duration.ofSeconds(16), Duration.ofSeconds(30), Duration.ofSeconds(30));
        f.controller.hostChanged(B); f.drain();
        f.controller.connectionLost(); f.drain();
        assertThat(f.delays.getLast()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test void connectedWithoutHostCancelsReconnectAndResetsBackoff() {
        var f = new Fixture(); f.controller.connectionLost(); f.drain();
        f.fireTimer(); assertThat(f.reconnects).isEqualTo(1);
        f.controller.connectionLost(); f.drain();
        f.controller.connectionRestored(); f.drain();
        f.fireTimer(); assertThat(f.reconnects).isEqualTo(1);
        f.controller.connectionLost(); f.drain();
        assertThat(f.delays.getLast()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test void failedLegacyFallbackCannotStarveBusReconnect() {
        var f = new Fixture(); f.legacySucceeds = false;
        f.controller.legacyChanged(true); f.controller.connectionLost(); f.drain();
        f.fireTimer(); assertThat(f.reconnects).isEqualTo(1);
    }

    @Test void duplicateDisconnectsCoalesceOneReconnectTimer() {
        var f = new Fixture();
        for (int i = 0; i < 10000; i++) f.controller.connectionLost();
        f.drain(); assertThat(f.timers).hasSize(1);
        f.fireTimer(); assertThat(f.reconnects).isEqualTo(1);
    }

    @Test void reconnectExceptionSchedulesNextBoundedAttempt() {
        var f = new Fixture(); f.failReconnect = true;
        f.controller.connectionLost(); f.drain(); f.fireTimer();
        assertThat(f.reconnects).isEqualTo(1);
        assertThat(f.delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test void eventStormQueuesAtMostOneReconcile() {
        var f = new Fixture();
        for (int i = 0; i < 10000; i++) f.controller.hostChanged(host(":1." + i));
        assertThat(f.work).hasSize(1); f.drain();
        assertThat(f.events).containsExactly("modern.add :1.9999");
    }

    @Test void closeDuringBlockedCallReturnsPromptly() throws Exception {
        var f = new Fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        f.inModern = () -> { entered.countDown(); await(release); };
        f.controller.hostChanged(A); Thread worker = Thread.ofPlatform().start(f::drain);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofMillis(500), f.controller::close);
            f.controller.hostChanged(B); f.controller.legacyChanged(true);
        } finally { release.countDown(); worker.join(2000); }
        assertThat(worker.isAlive()).isFalse();
        assertThat(f.events).containsExactly("modern.add :1.10", "modern.remove", "modern.close", "legacy.close");
        f.modernClick.accept(1); f.drainEdt(); assertThat(f.actions.get()).isZero();
    }

    @Test void retryAfterCloseCannotPublish() {
        var f = new Fixture(); f.modernSucceeds = false; f.controller.hostChanged(A); f.drain();
        f.controller.close(); f.drain(); var before = List.copyOf(f.events);
        f.fireTimer(); assertThat(f.events).isEqualTo(before);
    }

    @Test void ownerReplacementCancelsOldRetry() {
        var f = new Fixture(); f.modernSucceeds = false; f.controller.hostChanged(A); f.drain();
        f.modernSucceeds = true; f.controller.hostChanged(B); f.drain();
        var before = List.copyOf(f.events); f.fireTimer(); assertThat(f.events).isEqualTo(before);
    }

    @Test void modernPublishExceptionCleansPartialPublicationAndRestoresLegacy() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.drain();
        f.inModern = () -> { throw new IllegalStateException("publish"); };
        f.controller.hostChanged(A); f.drain();
        assertThat(f.events).endsWith("legacy.remove", "modern.add :1.10", "modern.remove", "legacy.add");
        assertThat(f.timers).hasSize(1);
    }

    @Test void legacyInstallExceptionCleansPartialIconAndRetries() {
        var f = new Fixture(); f.inLegacy = () -> { throw new IllegalStateException("install"); };
        f.controller.legacyChanged(true); f.drain();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove"); assertThat(f.timers).hasSize(1);
        f.inLegacy = () -> { }; f.fireTimer(); assertThat(f.events).endsWith("legacy.add");
    }

    @Test void partialLegacyAddMustBeRemovedBeforeAnotherInstall() {
        var f = new Fixture(); f.failLegacyRemove = true;
        f.inLegacy = () -> { throw new IllegalStateException("partial install"); };
        f.controller.legacyChanged(true); f.drain();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove");
        f.failLegacyRemove = false; f.inLegacy = () -> { }; f.fireTimer();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove", "legacy.remove", "legacy.add");
    }

    @Test void failedLegacyInstallRetriesWithoutInventingInstalledState() {
        var f = new Fixture(); f.legacySucceeds = false; f.controller.legacyChanged(true); f.drain();
        assertThat(f.events).containsExactly("legacy.add"); assertThat(f.timers).hasSize(1);
        f.legacySucceeds = true; f.fireTimer(); assertThat(f.events).containsExactly("legacy.add", "legacy.add");
    }

    @Test void legacyRemoveFailurePreventsSecondPublicationUntilCleanupSucceeds() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.drain();
        f.failLegacyRemove = true; f.controller.hostChanged(A); f.drain();
        assertThat(f.events).containsExactly("legacy.add", "legacy.remove");
        f.failLegacyRemove = false; f.fireTimer();
        assertThat(f.events).endsWith("legacy.remove", "modern.add :1.10");
    }

    @Test void modernWithdrawFailurePreventsFallbackUntilCleanupSucceeds() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.controller.hostChanged(A); f.drain();
        f.failModernRemove = true; f.controller.hostChanged(null); f.drain();
        assertThat(f.events).containsExactly("modern.add :1.10", "modern.remove");
        f.failModernRemove = false; f.fireTimer(); assertThat(f.events).endsWith("modern.remove", "legacy.add");
    }

    @Test void asynchronousWithdrawalMustFinishBeforeFallbackAdd() throws Exception {
        var f = new Fixture(); f.controller.legacyChanged(true); f.controller.hostChanged(A); f.drain();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        f.inWithdraw = () -> { entered.countDown(); await(release); };
        f.controller.hostChanged(null); Thread worker = Thread.ofPlatform().start(f::drain);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(f.events).containsExactly("modern.add :1.10", "modern.remove");
            assertTimeoutPreemptively(Duration.ofMillis(500), f.controller::close);
        } finally { release.countDown(); worker.join(2000); }
        assertThat(worker.isAlive()).isFalse();
        assertThat(f.events).containsExactly("modern.add :1.10", "modern.remove", "modern.close", "legacy.close");
    }

    @Test void modernCleanupFailureStillClosesBothAdapters() {
        var f = new Fixture(); f.controller.hostChanged(A); f.drain();
        f.failModernRemove = true; f.failModernClose = true;
        f.controller.close(); f.drain();
        assertThat(f.events).containsExactly("modern.add :1.10", "modern.remove", "modern.close", "legacy.close");
    }

    @Test void closeAttemptsEveryCleanupDespiteFailures() {
        var f = new Fixture(); f.controller.legacyChanged(true); f.drain();
        f.failLegacyRemove = true; f.failModernClose = true; f.failLegacyClose = true;
        f.controller.close(); f.drain();
        assertThat(f.events).endsWith("legacy.remove", "modern.close", "legacy.close");
        f.controller.close(); f.drain(); assertThat(f.events).hasSize(4);
    }

    private static LinuxStatusNotifier.Host host(String owner) {
        return new LinuxStatusNotifier.Host("org.kde.StatusNotifierWatcher", owner, true);
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    private static final class Fixture {
        final ArrayDeque<Runnable> work = new ArrayDeque<>();
        final ArrayDeque<Runnable> edt = new ArrayDeque<>();
        final ArrayDeque<Runnable> timers = new ArrayDeque<>();
        final List<Duration> delays = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final AtomicInteger actions = new AtomicInteger();
        boolean modernSucceeds = true, legacySucceeds = true;
        boolean failModernRemove, failLegacyRemove, failModernClose, failLegacyClose, failReconnect;
        int reconnects;
        Runnable inModern = () -> { }, inLegacy = () -> { }, inWithdraw = () -> { };
        IntConsumer modernClick, legacyClick;
        final LinuxResidentTray controller = new LinuxResidentTray(
            new ResidentTray.Actions(actions::incrementAndGet, actions::incrementAndGet,
                actions::incrementAndGet, actions::incrementAndGet),
            new LinuxResidentTray.Modern((host, click) -> {
                events.add("modern.add " + host.owner()); modernClick = click; inModern.run(); return modernSucceeds;
            }, () -> { events.add("modern.remove"); inWithdraw.run(); if (failModernRemove) throw new IllegalStateException("withdraw"); },
                () -> { events.add("modern.close"); if (failModernClose) throw new IllegalStateException("close modern"); },
                () -> { reconnects++; if (failReconnect) throw new IllegalStateException("reconnect"); }),
            new LinuxResidentTray.Legacy(click -> {
                events.add("legacy.add"); legacyClick = click; inLegacy.run(); return legacySucceeds;
            }, () -> { events.add("legacy.remove"); if (failLegacyRemove) throw new IllegalStateException("remove"); },
                () -> { events.add("legacy.close"); if (failLegacyClose) throw new IllegalStateException("close legacy"); }),
            task -> { synchronized (work) { work.add(task); } },
            (delay, task) -> { delays.add(delay); timers.add(task); }, edt::add);
        void drain() {
            while (true) {
                Runnable next; synchronized (work) { next = work.poll(); }
                if (next == null) return; next.run();
            }
        }
        void drainEdt() { while (!edt.isEmpty()) edt.remove().run(); }
        void fireTimer() { timers.remove().run(); drain(); }
    }
}
