package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.cef.browser.CefBrowser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    // The run marker's warnings, captured instead of printed. The logger is held, so it keeps the handler.
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger markerLog = Logger.getLogger(RunMarker.class.getName());
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record.getMessage());
        }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private boolean parentHandlers;

    @BeforeEach void listen() {
        parentHandlers = markerLog.getUseParentHandlers();
        markerLog.setUseParentHandlers(false);
        markerLog.addHandler(capture);
    }

    @AfterEach void stopListening() {
        markerLog.removeHandler(capture);
        markerLog.setUseParentHandlers(parentHandlers);
    }

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
     * The start marks Chromium as it enters JCEF, before Chromium initializes, so a fatal error while it initializes, as
     * a helper or the GPU process can raise, leaves the mark; the mark stays while Chromium runs. The worker here never
     * runs the start, so the test enters it, and no client can be made, so the made client is a stand-in null.
     */
    @Test void aStartThatHasEnteredMarksChromiumBeforeItRuns() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.client();
        assertThat(marker()).as("asked for, not entered").doesNotExist();
        assertThat(chromium.enter()).isTrue();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STARTING);
        assertThat(marker()).as("entered, not running").isRegularFile().isEmptyFile();
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        assertThat(chromium.state()).isEqualTo(Chromium.State.RUNNING);
        assertThat(marker()).as("running").isRegularFile();
        end(chromium);
        assertThat(warnings).isEmpty();
    }

    /**
     * Jasper's close deletes the marker at once: a quit's dispose can outlast Jasper's exit, and Chromium's end, which
     * deletes it too, may never come. The close on the UI thread returns before Chromium ends.
     */
    @Test void jaspersCloseDeletesTheMarkerBeforeChromiumHasEnded() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.client();
        chromium.enter();
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        assertThat(marker()).isRegularFile();
        EventQueue.invokeAndWait(chromium::close);
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        assertThat(marker()).as("closed, not ended").doesNotExist();
        chromium.terminated();
        assertThat(marker()).as("ended").doesNotExist();
        assertThat(warnings).isEmpty();
    }

    @Test void aStartThatNeverEntersLeavesNoMarker() throws Exception {
        chromium(recording, LIMIT).close();
        assertThat(marker()).as("never asked for").doesNotExist();
        Chromium refused = chromium(task -> { throw new RejectedExecutionException("The worker stopped"); }, LIMIT);
        assertThat(refused.client()).isCompletedExceptionally();
        assertThat(refused.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(marker()).as("the worker refused the start").doesNotExist();
        refused.close();
        assertThat(marker()).doesNotExist();
    }

    @Test void aStartTheCloseCutShortBeforeItEntersLeavesNoMarker() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.client();
        EventQueue.invokeAndWait(chromium::close);
        assertThat(chromium.enter()).as("the worker comes too late").isFalse();
        assertThat(marker()).doesNotExist();
    }

    /** A start that threw before Chromium had an app keeps its marker, as Chromium may have loaded, until the close. */
    @Test void aStartThatThrewHasItsMarkerGoneOnceClosed() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.client();
        chromium.enter();
        chromium.threw(new UnsatisfiedLinkError("no Chromium libraries"));
        assertThat(told()).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(chromium.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(marker()).as("failed, not closed").isRegularFile();
        long began = System.nanoTime();
        chromium.close();
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("nothing left to end").isLessThan(Duration.ofSeconds(2));
        assertThat(marker()).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    /** A start that never initializes keeps its marker: Chromium may still be initializing, until Jasper closes it. */
    @Test void aStartThatNeverInitializesKeepsItsMarkerUntilClosed() throws Exception {
        Chromium slow = chromium(recording, Duration.ofMillis(50));
        slow.client();
        slow.enter();
        assertThat(told()).isInstanceOf(TimeoutException.class);
        assertThat(slow.state()).isEqualTo(Chromium.State.FAILED);
        assertThat(marker()).as("timed out").isRegularFile();
        end(slow);
        assertThat(warnings).isEmpty();
    }

    /** A marker that cannot be written fails neither the start nor the close, and is a warning. */
    @Test void aMarkerThatCannotBeWrittenFailsNothing() throws Exception {
        Path notADirectory = Files.createFile(data.resolve("file"));
        Chromium chromium = new Chromium(notADirectory, recording, new AboutScheme(), cause -> tellings.incrementAndGet(), process, LIMIT);
        var client = chromium.client();
        assertThat(chromium.enter()).isTrue();
        assertThat(warnings).singleElement().asString().startsWith("The browser's run marker could not be written: ");
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        assertThat(client).isCompleted();
        assertThat(client.isCompletedExceptionally()).isFalse();
        assertThat(chromium.state()).isEqualTo(Chromium.State.RUNNING);
        end(chromium);
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        flushUi();
        assertThat(tellings).hasValue(0);
    }

    /** Plan 4b's P47: {@code jasper-about:} answers the browsers the pages name, none before they name them. */
    @Test void jasperAboutAnswersOnlyTheBrowsersThePagesName() {
        Chromium chromium = chromium(recording, LIMIT);
        CefBrowser page = browser(), devTools = browser();
        assertThat(chromium.answersAbout(page)).as("before the pages name any").isFalse();
        chromium.answerAboutFor(browser -> browser == page);
        assertThat(chromium.answersAbout(page)).isTrue();
        assertThat(chromium.answersAbout(devTools)).isFalse();
        assertThat(starts).as("naming them starts nothing").isEmpty();
    }

    /** A browser that is only itself. */
    private static CefBrowser browser() {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** The marker file in the data directory. */
    private Path marker() {
        return data.resolve("chromium.running");
    }

    /**
     * Closes a Chromium whose start entered JCEF, as Jasper does on the UI thread, and ends it as JCEF would, so no thread
     * is left waiting for its end. Leaves no marker.
     */
    private void end(Chromium chromium) throws Exception {
        EventQueue.invokeAndWait(chromium::close);
        assertThat(marker()).as("closed").doesNotExist();
        chromium.terminated();
        assertThat(marker()).doesNotExist();
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
