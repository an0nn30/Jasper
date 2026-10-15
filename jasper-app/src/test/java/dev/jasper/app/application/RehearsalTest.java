package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RehearsalTest {
    /** A failure the production code must not swallow: an {@link Error}, as a font or toolkit failure can be. */
    private static final class Boom extends Error {
        Boom(String message) { super(message); }
    }

    /**
     * Keeps the expected diagnostics out of the test output and records them: the rehearsal's warnings (and the pane's
     * own error log for a failed launch), and what reaches the default uncaught-exception handler (an Error that leaves
     * the EDT). Restores everything exactly.
     */
    private static final class Quiet implements AutoCloseable {
        private final List<Logger> loggers = List.of(Logger.getLogger(Rehearsal.class.getName()),
            Logger.getLogger(TerminalPane.class.getName()));
        private final List<Boolean> parents = loggers.stream().map(Logger::getUseParentHandlers).toList();
        private final List<Level> levels = loggers.stream().map(Logger::getLevel).toList();
        private final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();
        private final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
        private final Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };

        Quiet() {
            for (Logger logger : loggers) {
                logger.setUseParentHandlers(false);
                logger.setLevel(Level.ALL);
                logger.addHandler(handler);
            }
            Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
        }

        /** The rehearsal's warnings logged so far. */
        List<String> warnings() {
            return records.stream().filter(record -> record.getLoggerName().equals(Rehearsal.class.getName()))
                .filter(record -> record.getLevel().equals(Level.WARNING)).map(LogRecord::getMessage).toList();
        }

        /** The pane's error logs so far. */
        List<String> paneErrors() {
            return records.stream().filter(record -> record.getLoggerName().equals(TerminalPane.class.getName()))
                .filter(record -> record.getLevel().equals(Level.SEVERE)).map(LogRecord::getMessage).toList();
        }

        List<Throwable> uncaught() { return uncaught; }

        @Override public void close() {
            Thread.setDefaultUncaughtExceptionHandler(previous);
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).removeHandler(handler);
                loggers.get(i).setLevel(levels.get(i));
                loggers.get(i).setUseParentHandlers(parents.get(i));
            }
        }
    }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void theCannedTerminalPlaysOnlyOnceReleasedAndEndsWhenClosed() throws Exception {
        Rehearsal.Feed feed = Rehearsal.feed(100, 35);
        var changed = new CountDownLatch(1);
        try {
            feed.session().addListener(new TerminalSessionListener() {
                @Override public void screenChanged() { changed.countDown(); }
            });
            assertThat(changed.await(200, TimeUnit.MILLISECONDS)).as("nothing plays before release").isFalse();
            feed.release().run();
            assertThat(changed.await(5, TimeUnit.SECONDS)).isTrue();
            DesktopTestSupport.until(() -> "jasper".equals(feed.session().title()));
            assertThat(feed.session().columns()).isEqualTo(100);
            assertThat(feed.session().exitFuture()).as("it idles like a waiting shell").isNotDone();
        } finally {
            feed.session().close();
        }
        DesktopTestSupport.until(() -> feed.session().exitFuture().isDone());
    }

    @Test void theLauncherHandsOverTheCannedSessionOnTheEdt() throws Exception {
        Rehearsal.Feed feed = Rehearsal.feed(80, 24);
        var delivered = new CompletableFuture<TerminalSession>();
        try {
            edt(() -> Rehearsal.launcher(feed).launch(DesktopTestSupport.HOME, (session, failure) -> {
                if (failure != null) delivered.completeExceptionally(failure);
                else if (!SwingUtilities.isEventDispatchThread()) delivered.completeExceptionally(new AssertionError("off the EDT"));
                else delivered.complete(session);
            }));
            assertThat(delivered.get(5, TimeUnit.SECONDS)).isSameAs(feed.session());
        } finally {
            feed.session().close();
        }
    }

    @Test void playPaintsOnceWhenTheOutputArrivesThenFinishes() throws Exception {
        Rehearsal.Feed feed = Rehearsal.feed(80, 24);
        var paints = new AtomicInteger();
        var finished = new CountDownLatch(1);
        try {
            edt(() -> Rehearsal.play(feed, paints::incrementAndGet, finished::countDown, Duration.ofSeconds(30)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).as("the output, not the 30 s fallback, ended it").isTrue();
            edt(() -> { });
            assertThat(paints).hasValue(1);
        } finally {
            feed.session().close();
        }
    }

    @Test void playStillPaintsAndFinishesWhenNothingArrivesOrPaintingFails() throws Exception {
        var silent = new Rehearsal.Feed(IdleTerminalSession.start(), () -> { });
        var paints = new AtomicInteger();
        var finished = new CountDownLatch(1);
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.play(silent, () -> {
                paints.incrementAndGet();
                throw new IllegalStateException("no screen to paint on");
            }, finished::countDown, Duration.ofMillis(100)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> { });
            assertThat(paints).hasValue(1);
            assertThat(quiet.warnings()).as("a failing paint is logged, not rethrown").hasSize(1)
                .allSatisfy(message -> assertThat(message).contains("could not paint"));
            assertThat(quiet.uncaught()).isEmpty();
        } finally {
            silent.session().close();
        }
    }

    @Test void playFinishesOnceAndLetsAnErrorFromPaintingPropagateEvenWhenBothTriggersFire() throws Exception {
        // The output and the short fallback both fire: whichever is first paints and finishes, the other does nothing.
        Rehearsal.Feed feed = Rehearsal.feed(80, 24);
        var paints = new AtomicInteger();
        var finishes = new AtomicInteger();
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.play(feed, () -> {
                paints.incrementAndGet();
                throw new Boom("the toolkit failed while painting");
            }, finishes::incrementAndGet, Duration.ofMillis(50)));
            DesktopTestSupport.until(() -> finishes.get() > 0);
            DesktopTestSupport.until(() -> !quiet.uncaught().isEmpty());
            Thread.sleep(300);
            edt(() -> { });
            assertThat(paints).as("one paint").hasValue(1);
            assertThat(finishes).as("finish ran despite the Error, and once").hasValue(1);
            assertThat(quiet.uncaught()).as("the Error left the EDT, once, after finish").singleElement().isInstanceOf(Boom.class);
            assertThat(quiet.warnings()).isEmpty();
        } finally {
            feed.session().close();
        }
    }

    /** A stage that records what the rehearsal did to it, in order. */
    private static final class Recording {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger dones = new AtomicInteger();
        Rehearsal.Stage stage() {
            return new Rehearsal.Stage(() -> events.add("paint"), () -> events.add("close"));
        }
        Rehearsal.Stage stage(Runnable paint, Runnable close) {
            return new Rehearsal.Stage(() -> { events.add("paint"); paint.run(); }, () -> { events.add("close"); close.run(); });
        }
        Runnable done() { return () -> { events.add("done"); dones.incrementAndGet(); }; }
    }

    @Test void runPlaysTheStageThenClosesItAndItsSessionAndReportsDoneOnce() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        var finished = new CountDownLatch(1);
        edt(() -> Rehearsal.run(() -> {
            feeds.set(Rehearsal.feed(80, 24));
            return feeds.get();
        }, feed -> recording.stage(), () -> { recording.done().run(); finished.countDown(); }, Duration.ofSeconds(30)));
        assertThat(finished.await(5, TimeUnit.SECONDS)).as("the output, not the 30 s fallback, ended it").isTrue();
        edt(() -> { });
        assertThat(recording.events).containsExactly("paint", "close", "done");
        assertThat(recording.dones).hasValue(1);
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void runClosesAndReportsDoneWhenPaintingFailsWithAnException() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        var finished = new CountDownLatch(1);
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.run(() -> {
                feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get();
            }, feed -> recording.stage(() -> { throw new IllegalStateException("no screen"); }, () -> { }),
                () -> { recording.done().run(); finished.countDown(); }, Duration.ofMillis(50)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> { });
            assertThat(recording.events).containsExactly("paint", "close", "done");
            assertThat(recording.dones).hasValue(1);
            assertThat(quiet.warnings()).singleElement().asString().contains("could not paint");
            assertThat(quiet.uncaught()).isEmpty();
        }
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void runClosesAndReportsDoneWhenPaintingFailsWithAnErrorWhichStillPropagates() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        var finished = new CountDownLatch(1);
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.run(() -> {
                feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get();
            }, feed -> recording.stage(() -> { throw new Boom("no toolkit"); }, () -> { }),
                () -> { recording.done().run(); finished.countDown(); }, Duration.ofMillis(50)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            DesktopTestSupport.until(() -> !quiet.uncaught().isEmpty());
            edt(() -> { });
            assertThat(recording.events).containsExactly("paint", "close", "done");
            assertThat(recording.dones).hasValue(1);
            assertThat(quiet.uncaught()).as("the Error left the EDT, once, after done").singleElement().isInstanceOf(Boom.class);
            assertThat(quiet.warnings()).isEmpty();
        }
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void runReportsDoneEvenWhenClosingTheWindowFails() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        var finished = new CountDownLatch(1);
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.run(() -> {
                feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get();
            }, feed -> recording.stage(() -> { }, () -> { throw new IllegalStateException("cannot dispose"); }),
                () -> { recording.done().run(); finished.countDown(); }, Duration.ofMillis(50)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> { });
            assertThat(recording.events).containsExactly("paint", "close", "done");
            assertThat(quiet.warnings()).singleElement().asString().contains("could not close its window");
            assertThat(quiet.uncaught()).isEmpty();
        }
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void runReportsDoneAtOnceWhenTheFeedCannotBeBuilt() throws Exception {
        var recording = new Recording();
        var composed = new AtomicInteger();
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.run(() -> { throw new IllegalStateException("no canned session"); },
                feed -> { composed.incrementAndGet(); return recording.stage(); }, recording.done(), Duration.ofSeconds(30)));
            assertThat(recording.dones).as("done ran, and a plain failure did not escape").hasValue(1);
            assertThat(composed).as("nothing was composed").hasValue(0);
            assertThat(recording.events).containsExactly("done");
            assertThat(quiet.warnings()).singleElement().asString().contains("could not be built");
            assertThat(quiet.uncaught()).isEmpty();
        }
    }

    @Test void runClosesTheFeedAndReportsDoneAtOnceWhenComposingFails() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.run(() -> {
                feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get();
            }, feed -> { throw new IllegalStateException("cannot compose"); }, recording.done(), Duration.ofSeconds(30)));
            assertThat(recording.dones).as("done ran, and a plain failure did not escape").hasValue(1);
            assertThat(recording.events).containsExactly("done");
            assertThat(quiet.warnings()).singleElement().asString().contains("could not be built");
            assertThat(quiet.uncaught()).isEmpty();
        }
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void anErrorWhileBuildingOrComposingPropagatesButOnlyAfterDoneRanAndTheFeedClosed() throws Exception {
        var buildDone = new AtomicInteger();
        assertThatThrownBy(() -> edt(() -> Rehearsal.run(() -> { throw new Boom("build"); },
            feed -> { throw new AssertionError("never composed"); }, buildDone::incrementAndGet, Duration.ofSeconds(30))))
            .hasRootCauseInstanceOf(Boom.class);
        assertThat(buildDone).as("done ran before the Error left").hasValue(1);

        var composeDone = new AtomicInteger();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        assertThatThrownBy(() -> edt(() -> Rehearsal.run(() -> {
            feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
            return feeds.get();
        }, feed -> { throw new Boom("compose"); }, composeDone::incrementAndGet, Duration.ofSeconds(30))))
            .hasRootCauseInstanceOf(Boom.class);
        assertThat(composeDone).as("done ran before the Error left").hasValue(1);
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    /**
     * An application whose collection after a warm-up only records, into the same event list as the rehearsal's stage and
     * done, and on which thread it ran. The warm-up's one collection must come after done and nowhere else.
     */
    private static final class Collecting implements AutoCloseable {
        final JasperApplication application;
        final Recording recording;
        final CountDownLatch collected = new CountDownLatch(1);
        final AtomicInteger collections = new AtomicInteger();
        final AtomicReference<Thread> thread = new AtomicReference<>();
        final AtomicReference<Boolean> onEdt = new AtomicReference<>();

        Collecting(Recording recording) throws Exception {
            this.recording = recording;
            JasperApplication[] held = new JasperApplication[1];
            edt(() -> {
                held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { });
                held[0].collector = () -> {
                    thread.set(Thread.currentThread());
                    onEdt.set(SwingUtilities.isEventDispatchThread());
                    recording.events.add("collect");
                    collections.incrementAndGet();
                    collected.countDown();
                };
            });
            application = held[0];
        }

        /** The recording's done, held up a moment: a collection started before done would show ahead of it. */
        Runnable slowDone() {
            return application.collectingAfter(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                recording.done().run();
            });
        }

        /** Waits for the collection, then for any second one that a bug would have queued behind it. */
        void awaitOneCollection() throws Exception {
            assertThat(collected.await(5, TimeUnit.SECONDS)).as("the collection ran").isTrue();
            edt(() -> { });
            Thread.sleep(200);
            assertThat(collections).as("exactly one collection").hasValue(1);
            assertThat(onEdt.get()).as("off the EDT").isFalse();
            assertThat(thread.get().isDaemon()).as("on a daemon thread").isTrue();
        }

        @Override public void close() {
            try {
                edt(application::quitNow);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    @Test void oneCollectionFollowsAPlayedRehearsalAfterDone() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        try (var collecting = new Collecting(recording)) {
            edt(() -> Rehearsal.run(() -> {
                feeds.set(Rehearsal.feed(80, 24));
                return feeds.get();
            }, feed -> recording.stage(), collecting.slowDone(), Duration.ofSeconds(30)));
            collecting.awaitOneCollection();
            assertThat(recording.events).as("never ahead of done").containsExactly("paint", "close", "done", "collect");
            assertThat(recording.dones).hasValue(1);
        }
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void oneCollectionAlsoFollowsARehearsalThatCouldNotBeBuiltOrComposed() throws Exception {
        var recording = new Recording();
        try (var quiet = new Quiet(); var collecting = new Collecting(recording)) {
            edt(() -> Rehearsal.run(() -> { throw new IllegalStateException("no canned session"); },
                feed -> recording.stage(), collecting.slowDone(), Duration.ofSeconds(30)));
            collecting.awaitOneCollection();
            assertThat(recording.events).containsExactly("done", "collect");
            assertThat(quiet.warnings()).singleElement().asString().contains("could not be built");
        }

        var composed = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        try (var quiet = new Quiet(); var collecting = new Collecting(composed)) {
            edt(() -> Rehearsal.run(() -> {
                feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get();
            }, feed -> { throw new IllegalStateException("cannot compose"); }, collecting.slowDone(), Duration.ofSeconds(30)));
            collecting.awaitOneCollection();
            assertThat(composed.events).containsExactly("done", "collect");
            assertThat(quiet.warnings()).singleElement().asString().contains("could not be built");
        }
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void oneCollectionAlsoFollowsARehearsalThatFailedWhilePaintingEvenWithAnError() throws Exception {
        var plain = new Recording();
        var feeds = new CopyOnWriteArrayList<Rehearsal.Feed>();
        try (var quiet = new Quiet(); var collecting = new Collecting(plain)) {
            edt(() -> Rehearsal.run(() -> {
                feeds.add(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get(0);
            }, feed -> plain.stage(() -> { throw new IllegalStateException("no screen"); }, () -> { }),
                collecting.slowDone(), Duration.ofMillis(50)));
            collecting.awaitOneCollection();
            assertThat(plain.events).containsExactly("paint", "close", "done", "collect");
            assertThat(quiet.uncaught()).isEmpty();
        }

        var failing = new Recording();
        try (var quiet = new Quiet(); var collecting = new Collecting(failing)) {
            edt(() -> Rehearsal.run(() -> {
                feeds.add(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get(1);
            }, feed -> failing.stage(() -> { throw new Boom("no toolkit"); }, () -> { }),
                collecting.slowDone(), Duration.ofMillis(50)));
            collecting.awaitOneCollection();
            DesktopTestSupport.until(() -> !quiet.uncaught().isEmpty());
            assertThat(failing.events).as("the collection started although an Error was leaving")
                .containsExactly("paint", "close", "done", "collect");
            assertThat(quiet.uncaught()).singleElement().isInstanceOf(Boom.class);
        }
        for (var feed : feeds) DesktopTestSupport.until(() -> feed.session().exitFuture().isDone());
    }

    @Test void oneCollectionFollowsAnErrorWhileBuildingAndADoneThatThrows() throws Exception {
        var building = new Recording();
        try (var collecting = new Collecting(building)) {
            assertThatThrownBy(() -> edt(() -> Rehearsal.run(() -> { throw new Boom("build"); },
                feed -> { throw new AssertionError("never composed"); }, collecting.slowDone(), Duration.ofSeconds(30))))
                .hasRootCauseInstanceOf(Boom.class);
            collecting.awaitOneCollection();
            assertThat(building.events).containsExactly("done", "collect");
        }

        var throwing = new Recording();
        try (var collecting = new Collecting(throwing)) {
            Runnable done = collecting.application.collectingAfter(() -> { throw new IllegalStateException("done failed"); });
            assertThatThrownBy(() -> edt(done::run)).hasRootCauseInstanceOf(IllegalStateException.class);
            collecting.awaitOneCollection();
            assertThat(throwing.events).as("done's own failure still propagated").containsExactly("collect");
        }
    }

    @Test void aFailedCannedLaunchIsLoggedAndNeverOpensADialog() throws Exception {
        // The pane reports a failed launch through its window's onLaunchFailure, which in a real window becomes an
        // incident and a notification. A silenced window reports to the log instead, and never attempts a dialog.
        var reported = new CopyOnWriteArrayList<String>();
        try (var quiet = new Quiet()) {
            edt(() -> {
                var failing = new ShellLauncher(Runnable::run, directory -> { throw new IllegalStateException("no canned session"); }, "shell");
                Rehearsal.silence(DesktopTestSupport.content(failing), reported::add);
            });
            DesktopTestSupport.until(() -> !reported.isEmpty());
            edt(() -> { });
            assertThat(reported).singleElement().asString().contains("Could not start terminal").contains("no canned session");
            assertThat(quiet.paneErrors()).as("the pane logged the failed launch").hasSize(1);
            assertThat(quiet.uncaught()).as("no dialog was attempted").isEmpty();
        }
    }

    @Test void aSilencedWindowLogsWhatWouldHaveOpenedADialog() throws Exception {
        var failing = new ShellLauncher(Runnable::run, directory -> { throw new IllegalStateException("no canned session"); }, "shell");
        try (var quiet = new Quiet()) {
            edt(() -> Rehearsal.silence(DesktopTestSupport.content(failing)));
            DesktopTestSupport.until(() -> !quiet.warnings().isEmpty());
            edt(() -> { });
            assertThat(quiet.warnings()).singleElement().asString()
                .contains("not shown").contains("Could not start terminal");
            assertThat(quiet.paneErrors()).as("the pane logged the failed launch").hasSize(1);
            assertThat(quiet.uncaught()).isEmpty();
        }
    }

    @Test void closingFailsOverToDisposingTheFrameAndKeepsTheOriginalFailure() {
        var disposed = new AtomicInteger();
        var closeFailure = new IllegalStateException("cannot close");
        var disposeFailure = new IllegalStateException("cannot dispose");
        assertThatThrownBy(() -> Rehearsal.closeAndDispose(() -> { throw closeFailure; }, () -> {
            disposed.incrementAndGet();
            throw disposeFailure;
        })).isSameAs(closeFailure).hasSuppressedException(disposeFailure);
        assertThat(disposed).as("the frame is disposed although closing failed").hasValue(1);

        assertThatThrownBy(() -> Rehearsal.closeAndDispose(() -> { throw new Boom("close"); }, disposed::incrementAndGet))
            .isInstanceOf(Boom.class);
        assertThat(disposed).as("also for an Error, which still propagates").hasValue(2);

        Rehearsal.closeAndDispose(() -> { }, disposed::incrementAndGet);
        assertThat(disposed).as("a close that worked disposed the frame itself").hasValue(2);
    }

    @Test void aSilencedWindowOpensNoFile() throws Exception {
        var opened = new CopyOnWriteArrayList<String>();
        edt(() -> {
            var content = DesktopTestSupport.content(launcher(new ArrayDeque<>()));
            content.onOpenFile = (file, line) -> opened.add(file + ":" + line);
            Rehearsal.silence(content);
            content.onOpenFile.accept(java.nio.file.Path.of("notes.txt"), 3);
        });
        assertThat(opened).isEmpty();
    }
}
