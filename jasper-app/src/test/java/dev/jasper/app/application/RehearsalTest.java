package dev.jasper.app.application;

import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RehearsalTest {
    /** A failure the production code must not swallow: an {@link Error}, as a font or toolkit failure can be. */
    private static final class Boom extends Error {
        Boom(String message) { super(message); }
    }

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
        try {
            edt(() -> Rehearsal.play(silent, () -> {
                paints.incrementAndGet();
                throw new IllegalStateException("no screen to paint on");
            }, finished::countDown, Duration.ofMillis(100)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            edt(() -> { });
            assertThat(paints).hasValue(1);
        } finally {
            silent.session().close();
        }
    }

    @Test void playFinishesOnceEvenWhenPaintingThrowsAnErrorAndBothTriggersFire() throws Exception {
        // The output and the short fallback both fire: whichever is first paints and finishes, the other does nothing.
        Rehearsal.Feed feed = Rehearsal.feed(80, 24);
        var paints = new AtomicInteger();
        var finishes = new AtomicInteger();
        try {
            edt(() -> Rehearsal.play(feed, () -> {
                paints.incrementAndGet();
                throw new Boom("the toolkit failed while painting");
            }, finishes::incrementAndGet, Duration.ofMillis(50)));
            DesktopTestSupport.until(() -> finishes.get() > 0);
            Thread.sleep(300);
            edt(() -> { });
            assertThat(paints).as("one paint").hasValue(1);
            assertThat(finishes).as("finish ran despite the Error, and once").hasValue(1);
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

    @Test void runClosesAndReportsDoneWhenPaintingFailsWithAnExceptionOrAnError() throws Exception {
        for (Throwable failure : List.of(new IllegalStateException("no screen"), new Boom("no toolkit"))) {
            var recording = new Recording();
            var feeds = new AtomicReference<Rehearsal.Feed>();
            var finished = new CountDownLatch(1);
            edt(() -> Rehearsal.run(() -> {
                feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
                return feeds.get();
            }, feed -> recording.stage(() -> sneakyThrow(failure), () -> { }),
                () -> { recording.done().run(); finished.countDown(); }, Duration.ofMillis(50)));
            assertThat(finished.await(5, TimeUnit.SECONDS)).as(failure.getClass().getSimpleName()).isTrue();
            edt(() -> { });
            assertThat(recording.events).as(failure.getClass().getSimpleName()).containsExactly("paint", "close", "done");
            assertThat(recording.dones).hasValue(1);
            DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
        }
    }

    @Test void runReportsDoneEvenWhenClosingTheWindowFails() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        var finished = new CountDownLatch(1);
        edt(() -> Rehearsal.run(() -> {
            feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
            return feeds.get();
        }, feed -> recording.stage(() -> { }, () -> { throw new IllegalStateException("cannot dispose"); }),
            () -> { recording.done().run(); finished.countDown(); }, Duration.ofMillis(50)));
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        edt(() -> { });
        assertThat(recording.events).containsExactly("paint", "close", "done");
        DesktopTestSupport.until(() -> feeds.get().session().exitFuture().isDone());
    }

    @Test void runReportsDoneAtOnceWhenTheFeedCannotBeBuilt() throws Exception {
        var recording = new Recording();
        var composed = new AtomicInteger();
        edt(() -> Rehearsal.run(() -> { throw new IllegalStateException("no canned session"); },
            feed -> { composed.incrementAndGet(); return recording.stage(); }, recording.done(), Duration.ofSeconds(30)));
        assertThat(recording.dones).as("done ran, and a plain failure did not escape").hasValue(1);
        assertThat(composed).as("nothing was composed").hasValue(0);
        assertThat(recording.events).containsExactly("done");
    }

    @Test void runClosesTheFeedAndReportsDoneAtOnceWhenComposingFails() throws Exception {
        var recording = new Recording();
        var feeds = new AtomicReference<Rehearsal.Feed>();
        edt(() -> Rehearsal.run(() -> {
            feeds.set(new Rehearsal.Feed(IdleTerminalSession.start(), () -> { }));
            return feeds.get();
        }, feed -> { throw new IllegalStateException("cannot compose"); }, recording.done(), Duration.ofSeconds(30)));
        assertThat(recording.dones).as("done ran, and a plain failure did not escape").hasValue(1);
        assertThat(recording.events).containsExactly("done");
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

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable failure) throws T { throw (T) failure; }
}
