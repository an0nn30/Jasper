package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.platform.DesktopObservation.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DesktopColorsTest {

    @Test void resampleBoundaryIsIndependentOfEqualObservationsAndStopsAtUnsubscribeOrClose() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var desktop = service(f, new ArrayList<>(), "echo");
            f.await(() -> desktop.current().portal().appearance() == Polarity.DARK);
            var boundaries = new AtomicInteger();
            Runnable remove = desktop.subscribeResample(boundaries::incrementAndGet);
            f.time.advance(29, TimeUnit.SECONDS); f.pump();
            assertThat(boundaries.get()).isZero();
            f.time.advance(1, TimeUnit.SECONDS); f.pump();
            assertThat(boundaries.get()).isEqualTo(1);
            remove.run(); remove.run();
            f.time.advance(30, TimeUnit.SECONDS); f.pump();
            assertThat(boundaries.get()).isEqualTo(1);
            desktop.subscribeResample(boundaries::incrementAndGet);
            f.time.advance(30, TimeUnit.SECONDS); // Boundary queued on manager, not delivered yet.
            desktop.close(); f.pump();
            assertThat(boundaries.get()).isEqualTo(1);
        }
    }
    @Test void aFailingResampleSubscriberDoesNotSuppressOtherSubscribersOrFutureIntervals() {
        try (var warnings = new CapturedWarnings(DesktopColors.class);
             var f = new DesktopProcessTest.Fixture()) {
            var desktop = service(f, new ArrayList<>(), "echo");
            f.await(() -> desktop.current().portal().appearance() == Polarity.DARK);
            var failed = new java.util.concurrent.atomic.AtomicBoolean();
            desktop.subscribeResample(() -> { if (failed.compareAndSet(false, true)) throw new IllegalStateException("listener"); });
            var boundaries = new AtomicInteger(); desktop.subscribeResample(boundaries::incrementAndGet);
            f.time.advance(30, TimeUnit.SECONDS); f.pump();
            f.time.advance(30, TimeUnit.SECONDS); f.pump();
            assertThat(boundaries.get()).isEqualTo(2);
            desktop.close(); f.pump();
            warnings.assertListenerFailure("Desktop colors resample listener failed");
        }
    }

    @Test void startsOnlyPortalUntilToolkitDemandAndStopsUnusedToolkit() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>();
            var desktop = service(f, commands, "echo");
            f.await(() -> desktop.current().portal().appearance() == Polarity.DARK);
            assertThat(commands).hasSize(1);
            assertThat(commands.getFirst()).containsExactly("/packaged/helpers/jasper-desktop-portal", "--protocol=1");
            desktop.request(7, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            assertThat(commands).hasSize(2);
            assertThat(desktop.current().palettes().get(Source.GTK3).generation()).isEqualTo(7);
            desktop.request(8, Set.of(), false); f.pump();
            assertThat(desktop.current().palettes()).isEmpty();
            f.await(() -> f.children.stream().filter(Process::isAlive).count() == 1);
            desktop.close(); f.pump();
            f.await(() -> f.children.stream().noneMatch(Process::isAlive));
        }
    }
    @Test void snapshotsAreImmutableAndCoalescedAndListenerCannotPreventClose() {
        try (var diagnostics = new CapturedWarnings(DesktopColors.class);
             var f = new DesktopProcessTest.Fixture()) {
            var desktop = service(f, new ArrayList<>(), "echo");
            var seen = new ArrayList<DesktopObservation>(); desktop.subscribe(seen::add);
            desktop.subscribe(value -> { desktop.close(); throw new IllegalStateException("listener"); });
            f.await(() -> desktop.current().portal().appearance() == Polarity.DARK);
            var before = desktop.current(); desktop.request(1, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            assertThat(before.palettes()).isEmpty();
            assertThatThrownBy(() -> desktop.current().palettes().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(seen).isEmpty();
            f.time.advance(100, TimeUnit.MILLISECONDS); f.pump();
            assertThat(seen).hasSize(1); desktop.close(); f.pump();
            f.await(() -> f.children.stream().noneMatch(Process::isAlive));
            diagnostics.assertListenerFailure("Desktop colors listener failed");
        }
    }
    @Test void retriesAreCancelledByCloseAndRepeatedCloseIsSafe() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>(); var desktop = service(f, commands, "exit");
            f.await(() -> desktop.current().portal().reason().contains("exit"));
            assertThat(commands).hasSize(1);
            desktop.close(); desktop.close(); f.pump();
            f.time.advance(1, TimeUnit.DAYS); f.pump(); assertThat(commands).hasSize(1);
        }
    }
    @Test void restartUsesNewProcessIdentityEvenWhenWireGenerationIsReused() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>();
            var owners = new java.util.concurrent.ConcurrentHashMap<String, Process>();
            var desktop = new DesktopColors(Path.of("/packaged/helpers"), f.time, f.queue::add, f.time::now, command -> {
                commands.add(command); Process process = f.launch(DesktopProcessTest.child("echo"));
                owners.put(command.getFirst(), process); return process;
            });
            desktop.request(1, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            Process old = owners.get("/packaged/helpers/jasper-desktop-gtk3"); old.destroyForcibly();
            f.await(() -> desktop.current().palettes().get(Source.GTK3).reason().contains("exit"));
            f.time.advance(1, TimeUnit.SECONDS); f.pump();
            f.await(() -> commands.size() == 3 && desktop.current().palettes().get(Source.GTK3).reason().isEmpty());
            desktop.request(2, Set.of(Source.GTK3), false);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3) && desktop.current().palettes().get(Source.GTK3).generation() == 2);
            assertThat(desktop.current().palettes().get(Source.GTK3).appearance()).isEqualTo(Polarity.LIGHT);
            desktop.close(); f.pump();
        }
    }
    @Test void rapidDemandReplacementLaunchesOnlyFinalToolkitAndAppearance() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>(); var desktop = service(f, commands, "echo");
            for (int i = 0; i < 1000; i++) desktop.request(i, Set.of(Source.GTK4), true);
            desktop.request(1000, Set.of(Source.GTK3), false); f.pump();
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            assertThat(commands).noneMatch(c -> c.getFirst().endsWith("gtk4"));
            assertThat(desktop.current().palettes().get(Source.GTK3).generation()).isEqualTo(1000);
            assertThat(desktop.current().palettes().get(Source.GTK3).appearance()).isEqualTo(Polarity.LIGHT);
            desktop.close(); f.pump();
        }
    }
    @Test void lowerGenerationDemandCannotReplaceNewerDemand() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var desktop = service(f, new ArrayList<>(), "echo");
            desktop.request(9, Set.of(Source.GTK3), true);
            desktop.request(8, Set.of(Source.GTK4), false); f.pump();
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            assertThat(desktop.current().palettes().get(Source.GTK3).generation()).isEqualTo(9);
            desktop.close(); f.pump();
        }
    }
    @Test void reloadReplacesChildrenAndRetiresOldCallbacksAndRetry() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>(); var desktop = service(f, commands, "echo");
            desktop.request(4, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            var old = List.copyOf(f.children); desktop.reload(); f.pump();
            f.await(() -> commands.size() == 4);
            f.await(() -> old.stream().noneMatch(Process::isAlive));
            desktop.close(); f.pump(); f.time.advance(1, TimeUnit.DAYS); f.pump();
            assertThat(commands).hasSize(4);
        }
    }
    @Test void activeDemandIsResampledEveryThirtySeconds() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var desktop = service(f, new ArrayList<>(), "count");
            desktop.request(2, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            f.time.advance(30, TimeUnit.SECONDS); f.pump();
            f.await(() -> desktop.current().palettes().get(Source.GTK3).version().equals("2"));
            desktop.request(3, Set.of(Source.GTK3), false);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3) && desktop.current().palettes().get(Source.GTK3).generation() == 3);
            desktop.close(); f.pump();
        }
    }
    @Test void unavailableCapabilityPaletteIsPublishedWithItsReason() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var desktop = service(f, new ArrayList<>(), "capability");
            desktop.request(4, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            var palette = desktop.current().palettes().get(Source.GTK3);
            assertThat(palette.colors()).doesNotContainKey(Pair.WINDOW);
            assertThat(palette.reason()).isEqualTo("toolkit unavailable");
            desktop.close(); f.pump();
        }
    }
    @Test void longLaunchFailureRemainsBoundedDataAndRetries() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var attempts = new AtomicInteger();
            var desktop = new DesktopColors(Path.of("/packaged/helpers"), f.time, f.queue::add, f.time::now, command -> {
                attempts.incrementAndGet(); throw new IllegalStateException("x".repeat(2000));
            });
            f.await(() -> !desktop.current().portal().reason().equals("Desktop preferences pending"));
            assertThat(desktop.current().portal().reason().length()).isLessThanOrEqualTo(1024);
            f.time.advance(1, TimeUnit.SECONDS); f.pump(); f.await(() -> attempts.get() == 2);
            desktop.close(); f.pump();
        }
    }
    @Test void unavailableToolkitRestartsWithBackoffUntilUsableAndThenResetsBackoff() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var attempts = new AtomicInteger();
            var toolkit = new java.util.concurrent.atomic.AtomicReference<Process>();
            var desktop = new DesktopColors(Path.of("/packaged/helpers"), f.time, f.queue::add, f.time::now, command -> {
                boolean gtk = command.getFirst().endsWith("gtk3");
                int attempt = gtk ? attempts.incrementAndGet() : 0;
                Process process = f.launch(DesktopProcessTest.child(gtk && attempt <= 2 ? "capability" : "echo"));
                if (gtk) toolkit.set(process); return process;
            });
            desktop.request(5, Set.of(Source.GTK3), true);
            f.await(() -> attempts.get() == 1 && desktop.current().palettes().containsKey(Source.GTK3));
            f.await(() -> !toolkit.get().isAlive());
            Process first = toolkit.get();
            f.time.advance(1, TimeUnit.SECONDS); f.pump();
            f.await(() -> attempts.get() == 2 && toolkit.get() != first && !toolkit.get().isAlive());
            f.time.advance(1, TimeUnit.SECONDS); f.pump(); assertThat(attempts.get()).isEqualTo(2);
            f.time.advance(1, TimeUnit.SECONDS); f.pump();
            f.await(() -> attempts.get() == 3 && desktop.current().palettes().get(Source.GTK3).colors().containsKey(Pair.WINDOW));
            toolkit.get().destroyForcibly();
            f.await(() -> !desktop.current().palettes().get(Source.GTK3).reason().isEmpty());
            f.time.advance(1, TimeUnit.SECONDS); f.pump(); f.await(() -> attempts.get() == 4);
            desktop.close(); f.pump();
        }
    }
    @Test void portalOwnerSequenceMayRestartAtZeroInANewProcess() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var attempts = new AtomicInteger();
            var desktop = new DesktopColors(Path.of("/packaged/helpers"), f.time, f.queue::add, f.time::now,
                command -> f.launch(DesktopProcessTest.child(attempts.incrementAndGet() == 1 ? "portal9" : "echo")));
            f.await(() -> desktop.current().portal().owner() == 9);
            f.children.getFirst().destroyForcibly();
            f.await(() -> desktop.current().portal().appearance() == Polarity.UNKNOWN);
            f.time.advance(1, TimeUnit.SECONDS); f.pump();
            f.await(() -> desktop.current().portal().owner() == 0 && desktop.current().portal().appearance() == Polarity.DARK);
            desktop.close(); f.pump();
        }
    }
    @Test void closeCancelsPortalAndToolkitRetriesTogether() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>();
            var desktop = service(f, commands, "exit"); desktop.request(6, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3)
                && !desktop.current().portal().reason().equals("Desktop preferences pending"));
            desktop.close(); desktop.close(); f.pump();
            f.time.advance(1, TimeUnit.DAYS); f.pump(); assertThat(commands).hasSize(2);
        }
    }
    static final class CapturedWarnings implements AutoCloseable {
        private final java.util.logging.Logger logger;
        private final java.util.logging.Level previousLevel;
        private final boolean previousParents;
        private final List<java.util.logging.LogRecord> records = new ArrayList<>();
        private final java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        CapturedWarnings(Class<?> owner) {
            logger = java.util.logging.Logger.getLogger(owner.getName());
            previousLevel = logger.getLevel(); previousParents = logger.getUseParentHandlers();
            logger.setLevel(java.util.logging.Level.ALL); logger.setUseParentHandlers(false); logger.addHandler(handler);
        }
        void assertListenerFailure(String message) {
            assertThat(records).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getMessage()).isEqualTo(message);
                assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class).hasMessage("listener");
            });
        }
        @Override public void close() {
            logger.removeHandler(handler); logger.setLevel(previousLevel); logger.setUseParentHandlers(previousParents);
        }
    }
    static DesktopColors service(DesktopProcessTest.Fixture f, List<List<String>> commands, String mode) {
        return new DesktopColors(Path.of("/packaged/helpers"), f.time, f.queue::add, f.time::now, command -> {
            synchronized (commands) { commands.add(command); } return f.launch(DesktopProcessTest.child(mode));
        });
    }
}
