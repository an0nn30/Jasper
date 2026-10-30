package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chromium's life without Chromium. The worker here records the start instead of running it, because running it would
 * start Chromium, so no test reaches JCEF. Each test has its own once-per-process guard, as a fresh Jasper process would.
 */
class ChromiumStateTest {
    private static final Duration LIMIT = Duration.ofSeconds(90);
    @TempDir Path data;
    private final Queue<Runnable> starts = new ConcurrentLinkedQueue<>();
    private final Executor recording = starts::add;
    private final StartOnce process = new StartOnce();
    private final AtomicInteger tellings = new AtomicInteger();
    private final AtomicBoolean toldOnUi = new AtomicBoolean();
    private final CompletableFuture<Throwable> told = new CompletableFuture<>();

    @Test void aChromiumWhoseStartIsNeverAskedForStaysNotStarted() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        flushUi();
        assertThat(chromium.state()).isEqualTo(Chromium.State.NOT_STARTED);
        assertThat(starts).isEmpty();
    }

    @Test void closeThenAnswersAtOnceAndStartsNothing() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        long began = System.nanoTime();
        chromium.close();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("no wait for an end (it waits up to 10 s)").isLessThan(Duration.ofSeconds(2));
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        assertThat(starts).isEmpty();
        flushUi();
        assertThat(tellings).as("closing is not a failure to tell").hasValue(0);
    }

    @Test void aSecondCloseDoesNothing() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.close();
        long began = System.nanoTime();
        chromium.close();
        EventQueue.invokeAndWait(chromium::close);
        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(2));
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        assertThat(starts).isEmpty();
        flushUi();
        assertThat(tellings).hasValue(0);
    }

    @Test void theFirstClientCallHandsTheStartToTheWorkerAndLaterCallsShareItsFuture() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var client = chromium.client();
        assertThat(chromium.client()).isSameAs(client);
        assertThat(starts).as("one start, on the worker, not run by the caller").hasSize(1);
        assertThat(client).isNotDone();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STARTING);
        EventQueue.invokeAndWait(chromium::close);
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        assertThat(failure(client)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        flushUi();
        assertThat(tellings).as("a start the close cut short is not a failure to tell").hasValue(0);
    }

    @Test void clientAfterCloseFailsWithoutStartingAnythingAndTellsWhyOnce() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.close();
        var refused = chromium.client();
        assertThat(failure(refused)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(chromium.client()).isSameAs(refused);
        assertThat(told()).isSameAs(failure(refused));
        flushUi();
        assertThat(tellings).hasValue(1);
        assertThat(starts).isEmpty();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aChromiumMadeAfterAnotherOneStoppedInTheSameProcessCannotStart() throws Exception {
        Chromium first = chromium(recording, LIMIT);
        first.client();
        first.close();
        starts.clear();
        Chromium second = chromium(recording, LIMIT);
        var refused = second.client();
        assertThat(failure(refused)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(second.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(starts).as("nothing reaches JCEF").isEmpty();
        assertThat(told()).isSameAs(failure(refused));
        flushUi();
        assertThat(tellings).hasValue(1);
        second.close();
        assertThat(second.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aStartThatDoesNotReachItsInitializationInTimeFails() throws Exception {
        Chromium chromium = chromium(recording, Duration.ofMillis(50));
        var client = chromium.client();
        Throwable cause = told();
        assertThat(cause).isInstanceOf(TimeoutException.class).hasMessage("Chromium did not start within 90 seconds");
        assertThat(failure(client)).isSameAs(cause);
        assertThat(chromium.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(chromium.client()).as("a failed start is not tried again").isSameAs(client);
        assertThat(starts).hasSize(1);
        flushUi();
        assertThat(tellings).hasValue(1);
        chromium.close();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
    }

    @Test void aWorkerThatRefusesTheStartFailsIt() throws Exception {
        Chromium chromium = chromium(task -> { throw new RejectedExecutionException("The worker stopped"); }, LIMIT);
        var client = chromium.client();
        assertThat(failure(client)).isInstanceOf(RejectedExecutionException.class).hasMessage("The worker stopped");
        assertThat(chromium.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(told()).isSameAs(failure(client));
        chromium.close();
    }

    /**
     * What a client needs before any page is made on it: each installer once, in the order given, when the client is
     * made, and one given later at once. No client can be made here, so the made client is a stand-in null.
     */
    @Test void installersSetUpTheClientOnceInTheOrderGivenAndALateOneRunsAtOnce() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var ran = new java.util.ArrayList<String>();
        java.util.function.Consumer<org.cef.CefClient> display = client -> ran.add("display"), links = client -> ran.add("links");
        chromium.onClient(display);
        chromium.onClient(links);
        chromium.onClient(display);
        assertThat(ran).as("nothing runs before the client is made").isEmpty();
        EventQueue.invokeAndWait(() -> chromium.install(null));
        assertThat(ran).containsExactly("display", "links");
        EventQueue.invokeAndWait(() -> chromium.install(null));
        chromium.onClient(display);
        assertThat(ran).as("once each, however often given or installed").containsExactly("display", "links");
        chromium.onClient(client -> ran.add("late"));
        assertThat(ran).containsExactly("display", "links", "late");
        assertThat(starts).as("giving installers starts nothing").isEmpty();
        chromium.close();
    }

    /**
     * A Chromium that runs marks it in its data directory before its client reaches any page, and the mark goes once
     * Chromium has ended. No client can be made here, so the made client is a stand-in null.
     */
    @Test void aRunningChromiumIsMarkedUntilItHasEnded() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        Path marker = data.resolve("chromium.running");
        var markedWhenReady = new CompletableFuture<Boolean>();
        var client = chromium.client();
        client.whenComplete((made, failure) -> markedWhenReady.complete(Files.exists(marker)));
        assertThat(marker).as("starting").doesNotExist();
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        assertThat(chromium.state()).isEqualTo(Chromium.State.RUNNING);
        assertThat(client).isCompleted();
        assertThat(markedWhenReady.get(5, TimeUnit.SECONDS)).as("marked before the client's future completes").isTrue();
        assertThat(marker).isRegularFile().isEmptyFile();
        chromium.close();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        assertThat(marker).as("ended").doesNotExist();
    }

    @Test void aChromiumThatNeverStartsOrWhoseStartIsRefusedLeavesNoMarker() throws Exception {
        Path marker = data.resolve("chromium.running");
        chromium(recording, LIMIT).close();
        assertThat(marker).as("never started").doesNotExist();
        Chromium refused = chromium(task -> { throw new RejectedExecutionException("The worker stopped"); }, LIMIT);
        assertThat(refused.client()).isCompletedExceptionally();
        assertThat(refused.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(marker).as("its start failed").doesNotExist();
        refused.close();
        assertThat(marker).doesNotExist();
    }

    @Test void aClientMadeAfterTheStartFailedLeavesNoMarker() throws Exception {
        Path marker = data.resolve("chromium.running");
        Chromium slow = chromium(recording, Duration.ofMillis(50));
        slow.client();
        assertThat(told()).isInstanceOf(TimeoutException.class);
        EventQueue.invokeAndWait(() -> slow.clientCreated(null));
        assertThat(slow.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(marker).doesNotExist();
        slow.close();
        assertThat(marker).doesNotExist();
    }

    /** A marker that cannot be written fails neither the start nor the close. */
    @Test void aMarkerThatCannotBeWrittenFailsNothing() throws Exception {
        Path notADirectory = Files.createFile(data.resolve("file"));
        Chromium chromium = new Chromium(notADirectory, recording, new AboutScheme(), cause -> tellings.incrementAndGet(), process, LIMIT);
        var client = chromium.client();
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        assertThat(client).isCompleted();
        assertThat(client.isCompletedExceptionally()).isFalse();
        assertThat(chromium.state()).isEqualTo(Chromium.State.RUNNING);
        chromium.close();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        flushUi();
        assertThat(tellings).hasValue(0);
    }

    private Chromium chromium(Executor worker, Duration limit) {
        return new Chromium(data, worker, new AboutScheme(), cause -> {
            tellings.incrementAndGet();
            toldOnUi.set(EventQueue.isDispatchThread());
            told.complete(cause);
        }, process, limit);
    }

    /** What Chromium told the plugin, which it tells on the UI thread. */
    private Throwable told() throws Exception {
        Throwable cause = told.get(5, TimeUnit.SECONDS);
        assertThat(toldOnUi).as("told on the UI thread").isTrue();
        return cause;
    }

    /** The cause a future failed with, as it was completed. */
    private static Throwable failure(CompletableFuture<?> future) {
        assertThat(future).isCompletedExceptionally();
        return future.handle((value, thrown) -> thrown).join();
    }

    /** Runs whatever the UI thread has queued, such as a telling. */
    private static void flushUi() throws Exception {
        EventQueue.invokeAndWait(() -> { });
    }
}
