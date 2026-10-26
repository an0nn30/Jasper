package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.platform.DesktopObservation.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DesktopColorsTest {
    private static final Path HELPERS = Path.of("packaged", "helpers").toAbsolutePath();

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

    @Test void portalOnlyDistributionKeepsPreferencesWhenToolkitExecutablesAreMissing() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var desktop = new DesktopColors(Path.of("installed helpers").toAbsolutePath(), f.time, f.queue::add, f.time::now, command -> {
                if (!command.getFirst().endsWith("jasper-desktop-portal"))
                    throw new java.io.UncheckedIOException(new java.io.FileNotFoundException(command.getFirst()));
                return f.launch(DesktopProcessTest.child("echo"));
            });
            f.await(() -> desktop.current().portal().appearance() == Polarity.DARK);
            desktop.request(7, Set.of(Source.GTK3, Source.GTK4), true);
            f.await(() -> desktop.current().palettes().size() == 2);
            assertThat(desktop.current().palettes().values()).allSatisfy(palette -> {
                assertThat(palette.colors()).isEmpty();
                assertThat(palette.reason()).isNotBlank();
            });
            assertThat(desktop.current().portal().appearance()).isEqualTo(Polarity.DARK);
            assertThat(f.children).hasSize(1);
            desktop.close(); f.pump();
            f.await(() -> f.children.stream().noneMatch(Process::isAlive));
        }
    }
    @Test void startsOnlyPortalUntilToolkitDemandAndStopsUnusedToolkit() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var commands = new java.util.concurrent.CopyOnWriteArrayList<List<String>>();
            var desktop = service(f, commands, "echo");
            f.await(() -> desktop.current().portal().appearance() == Polarity.DARK);
            assertThat(commands).hasSize(1);
            assertThat(commands.getFirst()).containsExactly(HELPERS.resolve("jasper-desktop-portal").toString(), "--protocol=1");
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
            var desktop = new DesktopColors(HELPERS, f.time, f.queue::add, f.time::now, command -> {
                commands.add(command); Process process = f.launch(DesktopProcessTest.child("echo"));
                owners.put(command.getFirst(), process); return process;
            });
            desktop.request(1, Set.of(Source.GTK3), true);
            f.await(() -> desktop.current().palettes().containsKey(Source.GTK3));
            Process old = owners.get(HELPERS.resolve("jasper-desktop-gtk3").toString()); old.destroyForcibly();
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
            var desktop = new DesktopColors(HELPERS, f.time, f.queue::add, f.time::now, command -> {
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
            var desktop = new DesktopColors(HELPERS, f.time, f.queue::add, f.time::now, command -> {
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
            var desktop = new DesktopColors(HELPERS, f.time, f.queue::add, f.time::now,
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
    @ParameterizedTest @ValueSource(strings = { "QT5", "QT6", "KDE5", "KDE6" })
    void currentOnlyQtReplyKeepsObserverAliveAcrossRequestsAndPeriodicSamples(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.portal(1, true, "");
            h.service.request(1, Set.of(source), true);
            h.palette(source, false, "1,1,1,1", "desktop is Light");
            assertThat(h.service.current().palettes().get(source).appearance()).isEqualTo(Polarity.LIGHT);
            h.awaitToolkit(source, true);
            assertThat(h.launches()).contains("jasper-desktop-" + name.toLowerCase(Locale.ROOT));
            h.advance(30);
            h.answerPings(); h.portal(2, true, "");
            h.palette(source, false, "1,1,1,1", "periodic Light");
            h.service.request(2, Set.of(source), false);
            h.awaitSample(source, 2);
            h.palette(source, false, "1,1,1,1", "requested Light");
            h.advance(30);
            h.answerPings(); h.portal(3, true, "");
            h.palette(source, false, "1,1,1,1", "second periodic Light");
            h.advance(3);
            assertThat(h.toolkitAlive(source)).isTrue();
            assertThat(h.launches().stream().filter(("jasper-desktop-" + name.toLowerCase(Locale.ROOT))::equals).count()).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(strings = { "QT5", "QT6", "KDE5", "KDE6" })
    void unknownOrMissingQtPairIsHealthyAndLiveEventsRecoverOnTheSameGeneration(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.portal(1, true, ""); h.service.request(8, Set.of(source), true); h.awaitSample(source, 8);
            h.observation(source, source, 8, Polarity.UNKNOWN, "", "missing brush");
            h.awaitPalette(source, 8, Polarity.UNKNOWN, "missing brush");
            h.advance(3); assertThat(h.toolkitAlive(source)).isTrue();
            h.observation(source, source, 8, Polarity.LIGHT, "", "no Window pair");
            h.awaitPalette(source, 8, Polarity.LIGHT, "no Window pair");
            assertThat(h.service.current().palettes().get(source).colors()).isEmpty();
            h.observation(source, source, 8, Polarity.LIGHT, "1,1,1,1", "opposite event");
            h.awaitPalette(source, 8, Polarity.LIGHT, "opposite event");
            h.observation(source, source, 8, Polarity.DARK, "0,0,0,1", "recovered");
            h.awaitPalette(source, 8, Polarity.DARK, "recovered");
            assertThat(h.service.current().palettes().get(source).colors()).containsKey(Pair.WINDOW);
            h.observation(source, source, 8, Polarity.LIGHT, "1,1,1,1", "desktop changed back");
            h.awaitPalette(source, 8, Polarity.LIGHT, "desktop changed back");
            assertThat(h.toolkitAlive(source)).isTrue();
            assertThat(h.launches()).hasSize(2);
        }
    }

    @ParameterizedTest @ValueSource(strings = { "GTK3", "GTK4" })
    void gtkOppositeReplyRetainsFailureAndRestartBehavior(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.service.request(1, Set.of(source), true);
            h.reply(source, false, "1,1,1,1", "opposite palette");
            h.awaitFailure(source, "opposite palette");
            assertThat(h.service.current().palettes().get(source).colors()).isEmpty();
            h.retry(); h.awaitToolkit(source, true);
            h.palette(source, true, "0,0,0,1", "");
        }
    }

    @ParameterizedTest @ValueSource(strings = { "GTK3", "GTK4" })
    void gtkOppositeUnsolicitedEventCannotReplaceCompatibleSnapshot(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.service.request(1, Set.of(source), true);
            h.palette(source, true, "0,0,0,1", "initial");
            h.observation(source, source, 1, Polarity.LIGHT, "1,1,1,1", "opposite event");
            h.observation(source, source, 1, Polarity.DARK, "0,0,0,1", "still dark");
            h.awaitPalette(source, 1, Polarity.DARK, "still dark");
            assertThat(h.toolkitAlive(source)).isTrue();
            assertThat(h.launches()).hasSize(2);
        }
    }

    @ParameterizedTest @ValueSource(strings = { "QT5", "QT6", "KDE5", "KDE6" })
    void qtCrashKeepsActualOppositePolarityAndColorsThenRecovers(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.service.request(1, Set.of(source), true);
            h.palette(source, false, "1,1,1,1", "");
            Palette prior = h.service.current().palettes().get(source);
            h.fail(source);
            h.awaitFailure(source, "exited");
            Palette failed = h.service.current().palettes().get(source);
            assertThat(failed.appearance()).isEqualTo(Polarity.LIGHT);
            assertThat(failed.colors()).isEqualTo(prior.colors());
            assertThat(failed.implementation()).isEqualTo("fixture");
            assertThat(failed.version()).isEqualTo("1");
            h.retry(); h.awaitSample(source, 1);
            h.palette(source, true, "0,0,0,1", "recovered");
            assertThat(h.service.current().palettes().get(source).appearance()).isEqualTo(Polarity.DARK);
            assertThat(h.launches()).hasSize(3);
        }
    }

    @ParameterizedTest @ValueSource(strings = { "QT5", "QT6", "KDE5", "KDE6" })
    void qtTimeoutAndMalformedOutputFollowBackoffAndHealthyUnavailableResetsIt(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.portal(1, true, ""); h.service.request(1, Set.of(source), true); h.awaitSample(source, 1);
            h.advance(2); h.awaitFailure(source, "timed out");
            h.retry(); h.awaitToolkit(source, true); h.awaitSample(source, 1);
            h.raw(source, "{bad json}"); h.awaitFailure(source, "invalid");
            long launches = h.launches().size();
            h.advance(1); assertThat(h.launches()).hasSize((int) launches);
            h.advance(1); h.awaitToolkit(source, true); h.awaitSample(source, 1);
            h.unavailable(source, "Qt fixture", "6.2.4", "brush unavailable");
            h.raw(source, "{bad json}"); h.awaitFailure(source, "invalid");
            h.retry(); h.awaitToolkit(source, true); h.awaitSample(source, 1);
            h.palette(source, true, "0,0,0,1", "recovered");
        }
    }

    @ParameterizedTest @ValueSource(strings = { "QT5", "QT6", "KDE5", "KDE6" })
    void qtSlotRejectsWrongSourceWithoutBorrowingItsPalette(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.service.request(1, Set.of(source), true); h.awaitSample(source, 1);
            h.observation(source, Source.GTK3, 1, Polarity.DARK, "0,0,0,1", "foreign palette");
            h.awaitToolkit(source, false);
            assertThat(h.service.current().palettes()).containsOnlyKeys(source);
            assertThat(h.service.current().palettes().get(source).colors()).isEmpty();
            assertThat(h.service.current().palettes().get(source).implementation()).isEmpty();
            h.retry(); h.awaitSample(source, 1);
            h.palette(source, false, "1,1,1,1", "native Light");
            Palette prior = h.service.current().palettes().get(source);
            h.observation(source, Source.GTK3, 1, Polarity.LIGHT, "0.5,0.5,0.5,1", "foreign event");
            h.awaitFailure(source, "unexpected source");
            assertThat(h.service.current().palettes().get(source).colors()).isEqualTo(prior.colors());
            assertThat(h.service.current().palettes().get(source).appearance()).isEqualTo(Polarity.LIGHT);
        }
    }

    @ParameterizedTest @ValueSource(strings = { "QT5", "QT6", "KDE5", "KDE6" })
    void staleQtReplyCannotSatisfyNewGenerationAndDemandRemovalStopsIt(String name) {
        Source source = Source.valueOf(name);
        try (var h = new DesktopColorsHarness()) {
            h.service.request(1, Set.of(source), true); h.awaitSample(source, 1);
            h.service.request(9007199254740993L, Set.of(source), false); h.pump();
            h.observation(source, source, 1, Polarity.DARK, "0,0,0,1", "stale");
            h.awaitSample(source, 9007199254740993L);
            assertThat(h.service.current().palettes()).isEmpty();
            h.observation(source, source, 9007199254740993L, Polarity.LIGHT, "1,1,1,1", "latest");
            h.awaitPalette(source, 9007199254740993L, Polarity.LIGHT, "latest");
            h.observation(source, source, 1, Polarity.DARK, "0,0,0,1", "late stale");
            h.observation(source, source, 9007199254740993L, Polarity.LIGHT, "1,1,1,1", "live");
            h.awaitPalette(source, 9007199254740993L, Polarity.LIGHT, "live");
            h.service.request(9007199254740994L, Set.of(), true); h.awaitToolkit(source, false);
            assertThat(h.service.current().palettes()).isEmpty();
            h.advance(3); assertThat(h.launches()).hasSize(2);
        }
    }

    @Test void closeStopsPortalAndEveryDemandedSourceWithoutRestart() {
        try (var h = new DesktopColorsHarness()) {
            h.portal(1, true, "");
            h.service.request(1, EnumSet.allOf(Source.class), true);
            for (Source source : Source.values()) h.palette(source, true, "0,0,0,1", "");
            assertThat(h.launches()).containsExactlyInAnyOrderElementsOf(DesktopColors.helperNames());
            h.service.close(); h.service.close(); h.pump(); h.awaitAllStopped();
            h.advance(60);
            assertThat(h.launches()).hasSize(7);
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
        return new DesktopColors(HELPERS, f.time, f.queue::add, f.time::now, command -> {
            synchronized (commands) { commands.add(command); } return f.launch(DesktopProcessTest.child(mode));
        });
    }
}
