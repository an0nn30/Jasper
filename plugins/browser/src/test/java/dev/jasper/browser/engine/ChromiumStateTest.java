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
    // JCEF's name for a request context's native handle.
    private static final String NATIVE = "CefRequestContext";
    @TempDir Path data;
    private final Queue<Runnable> starts = new ConcurrentLinkedQueue<>();
    private final Executor recording = starts::add;
    private final StartOnce process = new StartOnce();
    private final AtomicInteger tellings = new AtomicInteger();
    private final AtomicBoolean toldOnUi = new AtomicBoolean();
    private final CompletableFuture<Throwable> told = new CompletableFuture<>();
    // Each time the start made the data directory its owner's alone, and whether the marker was there yet.
    private final List<String> restricted = new CopyOnWriteArrayList<>();
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
        var again = chromium.client();
        assertThat(again).as("N-F N6: a copy of the one future, which no caller can complete for the others").isNotSameAs(client);
        assertThat(starts).as("one start, on the worker, not run by the caller").hasSize(1);
        assertThat(client).isNotDone();
        client.cancel(false);
        assertThat(again).as("a caller's cancel is its own").isNotDone();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STARTING);
        EventQueue.invokeAndWait(chromium::close);
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
        assertThat(failure(again)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        flushUi();
        assertThat(tellings).as("a start the close cut short is not a failure to tell").hasValue(0);
    }

    @Test void clientAfterCloseFailsWithoutStartingAnythingAndTellsWhyOnce() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.close();
        var refused = chromium.client();
        assertThat(failure(refused)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(failure(chromium.client())).as("the same failure").isSameAs(failure(refused));
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
        assertThat(failure(chromium.client())).as("a failed start is not tried again").isSameAs(cause);
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
     * Plan 5's H37: the start makes the data directory its owner's alone as it enters JCEF, before it writes the marker
     * and before Chromium makes its profile there, cookies included.
     */
    @Test void aStartThatEntersMakesTheDataDirectoryItsOwnersAloneFirst() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.client();
        assertThat(restricted).as("asked for, not entered").isEmpty();
        assertThat(chromium.enter()).isTrue();
        assertThat(restricted).containsExactly("before the marker");
        end(chromium);
        assertThat(restricted).hasSize(1);
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
        assertThat(restricted).as("nothing done in the data directory").isEmpty();
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
        Chromium chromium = new Chromium(notADirectory, () -> { }, recording, new AboutScheme(), new PreviewScheme(),
            cause -> tellings.incrementAndGet(), process, LIMIT);
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

    /**
     * The preview client waits for the client: asking for it starts Chromium as the client does, once, and asking again
     * shares its future. A close while it waits fails it as the client fails, and nothing more starts.
     */
    @Test void thePreviewClientWaitsForTheClientAndSharesItsOneStart() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var preview = chromium.previewClient();
        assertThat(starts).as("one start, on the worker").hasSize(1);
        assertThat(chromium.client()).as("the client shares that start").isNotDone();
        var again = chromium.previewClient();
        assertThat(again).as("N6: a copy of the one future").isNotSameAs(preview);
        assertThat(starts).as("never a second start (R28)").hasSize(1);
        assertThat(preview).isNotDone();
        EventQueue.invokeAndWait(chromium::close);
        assertThat(failure(preview)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(failure(again)).isSameAs(failure(preview));
        assertThat(starts).hasSize(1);
        flushUi();
        assertThat(tellings).as("a start the close cut short is not a failure to tell").hasValue(0);
    }

    @Test void afterAFailedStartThePreviewClientFailsWithTheSameReason() throws Exception {
        Chromium refused = chromium(task -> { throw new RejectedExecutionException("The worker stopped"); }, LIMIT);
        Throwable cause = failure(refused.client());
        assertThat(failure(refused.previewClient())).isSameAs(cause);
        assertThat(refused.state()).isEqualTo(Chromium.State.FAILED);
        refused.close();
    }

    @Test void aPreviewClientAskedForDuringAStartThatFailsFailsWithTheSameReason() throws Exception {
        Chromium threw = chromium(recording, LIMIT);
        var waiting = threw.previewClient();
        var failedOnUi = new AtomicBoolean();
        waiting.whenComplete((client, failure) -> failedOnUi.set(EventQueue.isDispatchThread()));
        assertThat(threw.enter()).isTrue();
        var missing = new UnsatisfiedLinkError("no Chromium libraries");
        threw.threw(missing);
        flushUi();
        assertThat(failure(waiting)).as("asked for during the start").isSameAs(missing);
        assertThat(failedOnUi).as("N6: a failure too completes on the UI thread, though the start failed off it").isTrue();
        assertThat(failure(threw.previewClient())).as("asked for after it").isSameAs(missing);
        assertThat(starts).as("a failed start is not tried again").hasSize(1);
        threw.close();
    }

    @Test void afterCloseThePreviewClientFailsWithoutStarting() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.close();
        var refused = chromium.previewClient();
        assertThat(failure(refused)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(starts).isEmpty();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STOPPED);
    }

    /**
     * Each client has only its own installers: none of the pages' reaches the preview client, nor any of the previews'
     * the pages' client. No client can be made here, so the made clients are stand-in nulls.
     */
    @Test void thePreviewClientHasOnlyThePreviewInstallers() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var ran = new java.util.ArrayList<String>();
        java.util.function.Consumer<org.cef.CefClient> page = client -> ran.add("page"), preview = client -> ran.add("preview"),
            render = client -> ran.add("render");
        chromium.onClient(page);
        chromium.onPreviewClient(preview);
        chromium.onPreviewClient(render);
        chromium.onPreviewClient(preview);
        EventQueue.invokeAndWait(() -> chromium.install(null));
        assertThat(ran).containsExactly("page");
        EventQueue.invokeAndWait(() -> chromium.installPreview(null));
        assertThat(ran).as("once each, in the order given").containsExactly("page", "preview", "render");
        EventQueue.invokeAndWait(() -> chromium.installPreview(null));
        chromium.onPreviewClient(client -> ran.add("late"));
        assertThat(ran).containsExactly("page", "preview", "render", "late");
        assertThat(starts).as("giving installers starts nothing").isEmpty();
        chromium.close();
    }

    /** N-F12: {@code jasper-preview:} answers no browser before the preview client exists, and no request of none. */
    @Test void jasperPreviewAnswersNoBrowserBeforeThePreviewClientExists() {
        Chromium chromium = chromium(recording, LIMIT);
        String shell = "jasper-preview://0123456789abcdef0123456789abcdef/";
        CefBrowser preview = browser();
        chromium.answerPreviewFor(browser -> browser == preview ? "0123456789abcdef0123456789abcdef" : null);
        assertThat(chromium.answersPreview(preview, shell)).isFalse();
        assertThat(chromium.answersPreview(null, shell)).isFalse();
        assertThat(starts).isEmpty();
    }

    /** N6: a preview's browser is answered only on its own origin, as the previews name each browser's token. */
    @Test void aPreviewsBrowserIsAnsweredOnlyOnItsOwnOrigin() {
        String token = "0123456789abcdef0123456789abcdef", other = "ffffffffffffffffffffffffffffffff";
        CefBrowser preview = browser(), page = browser();
        java.util.function.Function<CefBrowser, String> tokens = browser -> browser == preview ? token : null;
        assertThat(Chromium.onItsOrigin(tokens, preview, "jasper-preview://" + token + "/preview.js")).isTrue();
        assertThat(Chromium.onItsOrigin(tokens, preview, "jasper-preview://" + other + "/preview.js")).as("another preview's").isFalse();
        assertThat(Chromium.onItsOrigin(tokens, page, "jasper-preview://" + token + "/")).as("a browser no preview names").isFalse();
        assertThat(Chromium.onItsOrigin(null, preview, "jasper-preview://" + token + "/")).as("before the previews name any").isFalse();
    }

    /**
     * Plan 6: work for Chromium's own UI thread, such as making an in-memory context, given before Chromium has started
     * waits for its thread and starts nothing; once Chromium is closed, it fails, and so does any given after.
     */
    @Test void aTaskForChromiumsThreadGivenBeforeAnyStartWaitsStartsNothingAndFailsOnceClosed() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var ran = new AtomicInteger();
        var waiting = chromium.onChromiumThread(ran::incrementAndGet);
        var context = chromium.inMemoryContext();
        flushUi();
        assertThat(waiting).isNotDone();
        assertThat(context).isNotDone();
        assertThat(chromium.state()).isEqualTo(Chromium.State.NOT_STARTED);
        assertThat(starts).as("it starts nothing").isEmpty();
        EventQueue.invokeAndWait(chromium::close);
        assertThat(failure(waiting)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(failure(context)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(failure(chromium.onChromiumThread(ran::incrementAndGet))).as("given after the close").hasMessage(Chromium.ONCE);
        assertThat(failure(chromium.inMemoryContext())).hasMessage(Chromium.ONCE);
        assertThat(ran).hasValue(0);
        assertThat(starts).isEmpty();
        flushUi();
        assertThat(tellings).as("nothing to tell").hasValue(0);
    }

    /**
     * Work given before Chromium's context initialized runs inside its initialization, on Chromium's thread, in the order
     * given; a task that throws fails its own future only.
     */
    @Test void tasksGivenBeforeTheContextInitializedRunInsideItInTheOrderGiven() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.client();
        assertThat(chromium.enter()).isTrue();
        var ran = new CopyOnWriteArrayList<String>();
        var on = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var first = chromium.onChromiumThread(() -> {
            on.set(Thread.currentThread());
            ran.add("first");
        });
        var broken = chromium.onChromiumThread(() -> { throw new IllegalStateException("broken"); });
        var second = chromium.onChromiumThread(() -> ran.add("second"));
        assertThat(ran).isEmpty();
        Thread initializing = Thread.ofPlatform().name("chromium-ui").start(chromium::contextInitialized);
        initializing.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(ran).containsExactly("first", "second");
        assertThat(on.get()).as("on Chromium's thread, inside its initialization").isSameAs(initializing);
        assertThat(first).isCompleted();
        assertThat(first.isCompletedExceptionally()).isFalse();
        assertThat(failure(broken)).isInstanceOf(IllegalStateException.class).hasMessage("broken");
        assertThat(second.isCompletedExceptionally()).isFalse();
        end(chromium);
    }

    /**
     * Once both Chromium's context and JCEF have initialized, in either order, work is handed to Chromium's thread; work
     * the thread refuses fails, and work handed over that runs only after Chromium can no longer serve pages fails
     * instead of running.
     */
    @Test void workAfterBothInitializationsIsHandedToChromiumsThread() {
        var unavailable = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<Throwable>>(java.util.Optional.empty());
        var thread = new ChromiumThread(unavailable::get);
        var handed = new java.util.ArrayList<Runnable>();
        var ran = new CopyOnWriteArrayList<String>();
        var first = thread.run(() -> ran.add("first"));
        thread.contextInitialized();
        assertThat(ran).as("inside the context's initialization").containsExactly("first");
        assertThat(first.isCompletedExceptionally()).isFalse();
        var between = thread.run(() -> ran.add("between"));
        assertThat(ran).as("JCEF has not initialized: it waits").containsExactly("first");
        thread.handOverWith(task -> handed.add(task));
        assertThat(handed).as("handed over as JCEF initializes").hasSize(1);
        var later = thread.run(() -> ran.add("later"));
        assertThat(handed).hasSize(2);
        handed.get(0).run();
        assertThat(between).isCompleted();
        assertThat(ran).containsExactly("first", "between");
        var stopped = new IllegalStateException(Chromium.ONCE);
        unavailable.set(java.util.Optional.of(stopped));
        thread.fail(stopped);
        assertThat(failure(later)).isSameAs(stopped);
        handed.get(1).run();
        assertThat(ran).as("handed over, never run").containsExactly("first", "between");
        assertThat(failure(thread.run(() -> ran.add("after")))).isSameAs(stopped);

        var jcefFirst = new ChromiumThread(java.util.Optional::empty);
        jcefFirst.handOverWith(task -> { throw new AssertionError("handed over before the context initialized"); });
        var waiting = jcefFirst.run(() -> ran.add("waiting"));
        jcefFirst.contextInitialized();
        assertThat(waiting.isCompletedExceptionally()).isFalse();
        assertThat(ran).endsWith("waiting");

        var refusing = new ChromiumThread(java.util.Optional::empty);
        refusing.contextInitialized();
        refusing.handOverWith(task -> false);
        assertThat(failure(refusing.run(() -> ran.add("refused")))).isInstanceOf(IllegalStateException.class)
            .hasMessage("Chromium's thread did not take the work");
        assertThat(ran).doesNotContain("refused");
    }

    /**
     * V10: JCEF disposes a browser's context object as the browser closes, so each browser is handed one of its own: it
     * carries the kept context's native handle, does what is asked of it on the kept context, and its dispose leaves the
     * kept context alone.
     */
    @Test void aBrowsersContextObjectIsItsOwnOverTheKeptContextAndDisposesNothing() {
        Chromium chromium = chromium(recording, LIMIT);
        var kept = new RecordingContext(42);
        kept.setPreference("first", 1);
        org.cef.browser.CefRequestContext one = chromium.browserContext(kept), two = chromium.browserContext(kept);
        assertThat(one).isNotSameAs(kept).isNotSameAs(two);
        assertThat(one.getNativeRef("CefRequestContext")).isEqualTo(42);
        assertThat(two.getNativeRef("CefRequestContext")).isEqualTo(42);
        assertThat(one.getPreference("first")).isEqualTo(1);
        assertThat(one.setPreference("second", 2)).isNull();
        assertThat(kept.getPreference("second")).as("set on the kept context").isEqualTo(2);
        assertThat(one.hasPreference("second")).isTrue();
        assertThat(one.getAllPreferences(false)).containsEntry("first", 1);
        assertThat(one.isGlobal()).isFalse();
        one.dispose();
        one.dispose();
        assertThat(kept.disposals()).isZero();
        assertThat(two.getPreference("first")).as("the kept context is still there").isEqualTo(1);
        assertThat(chromium.browserContext(null)).as("Chromium's global context, which JCEF makes for a null").isNull();
        assertThat(starts).isEmpty();
    }

    /** Plan 6: a group's kept context is released once, however often its group's close asks. */
    @Test void aKeptContextIsReleasedOnceHoweverOftenAsked() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        RecordingContext one = kept(chromium, new RecordingContext(1)).getFirst();
        EventQueue.invokeAndWait(() -> {
            chromium.release(one);
            chromium.release(one);
        });
        assertThat(one.disposals()).isEqualTo(1);
        end(chromium);
    }

    /**
     * V12: a browser's creation, queued for Chromium's thread, reads its context object's handle only when it runs, so a
     * group that closes while a browser still holds an object over its kept context keeps that context until JCEF has
     * disposed the object, as the browser closes; whichever comes last disposes the kept context, once, and a disposed
     * object carries no handle.
     */
    @Test void aGroupThatClosesWhileABrowserHoldsItsContextKeepsItUntilThatBrowsersObjectIsDisposed() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        List<RecordingContext> kept = kept(chromium, new RecordingContext(7), new RecordingContext(8));
        RecordingContext closesFirst = kept.get(0), browserFirst = kept.get(1);

        org.cef.browser.CefRequestContext held = chromium.browserContext(closesFirst);
        chromium.release(closesFirst);
        assertThat(closesFirst.disposals()).as("a browser still holds it").isZero();
        assertThat(held.getNativeRef(NATIVE)).isEqualTo(7);
        held.dispose();
        assertThat(closesFirst.disposals()).as("its last object went: now").isEqualTo(1);
        assertThat(held.getNativeRef(NATIVE)).as("a disposed object carries no handle").isZero();
        held.dispose();
        chromium.release(closesFirst);
        assertThat(closesFirst.disposals()).as("once").isEqualTo(1);

        org.cef.browser.CefRequestContext first = chromium.browserContext(browserFirst), second = chromium.browserContext(browserFirst);
        first.dispose();
        second.dispose();
        assertThat(browserFirst.disposals()).as("its group is still open").isZero();
        chromium.release(browserFirst);
        assertThat(browserFirst.disposals()).as("the group's close comes last: now").isEqualTo(1);
        end(chromium);
    }

    /**
     * V12: JCEF hands a page's DevTools browser the page's own context object and disposes it again as the DevTools
     * close, so the DevTools get an object of their own, counted the same way; Chromium's global context, which a
     * persistent page's DevTools are handed, stays as it is.
     */
    @Test void aPagesDevToolsGetAContextObjectOfTheirOwnCountedTheSame() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        RecordingContext group = kept(chromium, new RecordingContext(9)).getFirst();
        org.cef.browser.CefRequestContext page = chromium.browserContext(group);
        org.cef.browser.CefRequestContext devTools = Chromium.anotherContext(page);
        assertThat(devTools).isNotSameAs(page).isNotSameAs(group);
        assertThat(devTools.getNativeRef(NATIVE)).isEqualTo(9);
        chromium.release(group);
        page.dispose();
        assertThat(group.disposals()).as("the DevTools still hold it").isZero();
        devTools.dispose();
        assertThat(group.disposals()).isEqualTo(1);
        var global = new RecordingContext(10);
        assertThat(Chromium.anotherContext(global)).as("not an in-memory group's").isSameAs(global);
        assertThat(Chromium.anotherContext(null)).isNull();
        end(chromium);
    }

    /**
     * V12 at Chromium's close: the dispose's sweep disposes at once the kept contexts no browser holds, and each other one
     * as soon as JCEF has disposed the last object over it, which JCEF's shutdown does as it closes the browsers, before
     * CEF shuts down; a context made after the sweep is disposed at once and its ask fails.
     */
    @Test void theSweepDisposesWhatNoBrowserHoldsAndTheRestAsTheirBrowsersClose() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        List<RecordingContext> kept = kept(chromium, new RecordingContext(1), new RecordingContext(2), new RecordingContext(3));
        RecordingContext held = kept.get(0), free = kept.get(1), closed = kept.get(2);
        org.cef.browser.CefRequestContext browser = chromium.browserContext(held);
        chromium.release(closed);
        assertThat(closed.disposals()).isEqualTo(1);
        EventQueue.invokeAndWait(chromium::close);
        EventQueue.invokeAndWait(chromium::disposeContexts);
        assertThat(free.disposals()).as("held by no browser").isEqualTo(1);
        assertThat(held.disposals()).as("its browser has not closed").isZero();
        assertThat(closed.disposals()).as("released already").isEqualTo(1);
        browser.dispose();
        assertThat(held.disposals()).as("its last browser closed, before CEF shuts down").isEqualTo(1);
        chromium.terminated();

        // Another process's Chromium, as one Chromium starts once in each.
        Chromium late = new Chromium(data, () -> { }, recording, new AboutScheme(), new PreviewScheme(), cause -> { }, new StartOnce(), LIMIT);
        var after = new RecordingContext(4);
        late.makeContextsWith(() -> after);
        late.client();
        assertThat(late.enter()).isTrue();
        var asked = late.inMemoryContext();
        EventQueue.invokeAndWait(late::disposeContexts);
        Thread.ofPlatform().start(late::contextInitialized).join(TimeUnit.SECONDS.toMillis(10));
        assertThat(failure(asked)).as("made as the dispose ran").isInstanceOf(IllegalStateException.class).hasMessage(Chromium.ONCE);
        assertThat(after.disposals()).as("disposed at once").isEqualTo(1);
        end(late);
    }

    /**
     * V12 on the plugin's stop: a group closing after Chromium's close began follows the same rule, disposing its kept
     * context once nothing holds it, while CEF still runs; once Chromium has ended, nothing native is touched.
     */
    @Test void theStopPathFollowsTheSameRuleAndNothingIsTouchedOnceChromiumHasEnded() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        List<RecordingContext> kept = kept(chromium, new RecordingContext(5), new RecordingContext(6));
        RecordingContext free = kept.get(0), held = kept.get(1);
        org.cef.browser.CefRequestContext browser = chromium.browserContext(held);
        EventQueue.invokeAndWait(chromium::close);
        chromium.release(free);
        chromium.release(held);
        assertThat(free.disposals()).as("closed after Chromium's close began, held by nothing").isEqualTo(1);
        assertThat(held.disposals()).isZero();
        chromium.terminated();
        browser.dispose();
        assertThat(held.disposals()).as("Chromium has ended: nothing native is touched").isZero();
        assertThat(browser.getNativeRef(NATIVE)).isZero();
    }

    /**
     * The in-memory contexts {@code made} are, made by {@code chromium} as pages would ask for them, on its thread as
     * its context initializes; stand-ins, as none can be made without Chromium. Its start has entered, never initialized.
     */
    private List<RecordingContext> kept(Chromium chromium, RecordingContext... made) throws Exception {
        var queue = new java.util.ArrayDeque<>(List.of(made));
        chromium.makeContextsWith(queue::poll);
        chromium.client();
        assertThat(chromium.enter()).isTrue();
        var asked = new java.util.ArrayList<CompletableFuture<org.cef.browser.CefRequestContext>>();
        for (int each = 0; each < made.length; each++) asked.add(chromium.inMemoryContext());
        Thread.ofPlatform().start(chromium::contextInitialized).join(TimeUnit.SECONDS.toMillis(10));
        var kept = new java.util.ArrayList<RecordingContext>();
        for (var context : asked) kept.add((RecordingContext) context.get(10, TimeUnit.SECONDS));
        return kept;
    }

    /**
     * Plan 6: no work is handed to Chromium's thread once the close has begun: a hand-over under way when the close comes
     * ends before the close begins, and none starts after it.
     */
    @Test void noWorkIsHandedToChromiumsThreadOnceTheCloseHasBegun() throws Exception {
        var closing = new AtomicBoolean();
        var thread = new ChromiumThread(() -> closing.get() ? java.util.Optional.of(new IllegalStateException(Chromium.ONCE))
            : java.util.Optional.empty());
        var inHand = new java.util.concurrent.CountDownLatch(1);
        var leave = new java.util.concurrent.CountDownLatch(1);
        var sawClosing = new AtomicBoolean();
        var hands = new AtomicInteger();
        thread.contextInitialized();
        thread.handOverWith(task -> {
            hands.incrementAndGet();
            inHand.countDown();
            try {
                leave.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            sawClosing.set(closing.get());
            return true;
        });
        Thread giver = Thread.ofPlatform().start(() -> thread.run(() -> { }));
        assertThat(inHand.await(10, TimeUnit.SECONDS)).isTrue();
        Thread closer = Thread.ofPlatform().start(() -> thread.closing(() -> {
            closing.set(true);
            return null;
        }));
        closer.join(300);
        assertThat(closer.isAlive()).as("the close waits for the hand-over under way").isTrue();
        leave.countDown();
        giver.join(TimeUnit.SECONDS.toMillis(10));
        closer.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(sawClosing).as("the hand-over ended before the close began").isFalse();
        assertThat(failure(thread.run(() -> { }))).hasMessage(Chromium.ONCE);
        assertThat(hands).as("nothing handed over after it").hasValue(1);
    }

    /** A start that fails fails the work waiting for Chromium's thread, with its reason, and any given after it. */
    @Test void aFailedStartFailsTheWorkWaitingForChromiumsThread() throws Exception {
        Chromium chromium = chromium(recording, Duration.ofMillis(50));
        var waiting = chromium.onChromiumThread(() -> { });
        chromium.client();
        Throwable cause = told();
        assertThat(failure(waiting)).isSameAs(cause);
        assertThat(failure(chromium.onChromiumThread(() -> { }))).isSameAs(cause);
        assertThat(failure(chromium.inMemoryContext())).isSameAs(cause);
        chromium.close();
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
        return new Chromium(data, () -> restricted.add(Files.exists(marker()) ? "after the marker" : "before the marker"), worker,
            new AboutScheme(), new PreviewScheme(), cause -> {
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
