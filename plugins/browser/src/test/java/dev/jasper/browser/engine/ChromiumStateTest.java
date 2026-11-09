package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    // Chromium's global context, as its context's initialization hands it over; a stand-in, as none can be made here.
    private final RecordingContext global = new RecordingContext(100);
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
        Chromium chromium = new Chromium(notADirectory, () -> { }, recording, new AboutScheme(), new PreviewScheme(Runnable::run),
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
        Thread initializing = Thread.ofPlatform().name("chromium-ui").start(() -> chromium.contextInitialized(global));
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
        Chromium late = new Chromium(data, () -> { }, recording, new AboutScheme(), new PreviewScheme(Runnable::run), cause -> { }, new StartOnce(), LIMIT);
        var after = new RecordingContext(4);
        late.makeContextsWith(() -> after);
        late.client();
        assertThat(late.enter()).isTrue();
        var asked = late.inMemoryContext();
        EventQueue.invokeAndWait(late::disposeContexts);
        Thread.ofPlatform().start(() -> late.contextInitialized(global)).join(TimeUnit.SECONDS.toMillis(10));
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
     * Round 3's A: once the sweep has run, CEF may shut down as soon as JCEF's browsers have closed, so only a browser's
     * close, JCEF's dispose of its object, may dispose a kept context natively; an object Jasper hands back itself, or a
     * group that closes after the sweep, lets go of it without a native call. Before the sweep, Jasper's own hand-back
     * disposes as any does.
     */
    @Test void afterTheSweepOnlyABrowsersCloseDisposesAKeptContext() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        List<RecordingContext> kept = kept(chromium, new RecordingContext(1), new RecordingContext(2), new RecordingContext(3),
            new RecordingContext(4));
        RecordingContext both = kept.get(0), unusedOnly = kept.get(1), closesAfter = kept.get(2), running = kept.get(3);

        org.cef.browser.CefRequestContext early = chromium.browserContext(running);
        chromium.release(running);
        Chromium.handBack(early);
        assertThat(running.disposals()).as("before the sweep, Jasper's own hand-back disposes it").isEqualTo(1);

        org.cef.browser.CefRequestContext browser = chromium.browserContext(both), never = chromium.browserContext(both);
        org.cef.browser.CefRequestContext alone = chromium.browserContext(unusedOnly);
        org.cef.browser.CefRequestContext held = chromium.browserContext(closesAfter);
        EventQueue.invokeAndWait(chromium::close);
        EventQueue.invokeAndWait(chromium::disposeContexts);
        Chromium.handBack(never);
        assertThat(both.disposals()).as("a browser still holds it").isZero();
        browser.dispose();
        assertThat(both.disposals()).as("its last browser closed: JCEF's dispose").isEqualTo(1);
        Chromium.handBack(alone);
        assertThat(unusedOnly.disposals()).as("after the sweep, Jasper's own hand-back makes no native call").isZero();
        assertThat(alone.getNativeRef(NATIVE)).isZero();
        chromium.release(closesAfter);
        held.dispose();
        assertThat(closesAfter.disposals()).as("the browser's close disposes it, the group's late close never").isEqualTo(1);
        end(chromium);
    }

    /**
     * Round 3's B: a kept context that has been disposed is refused, never handed out again: an object over it would
     * carry handle 0, which JCEF resolves to Chromium's global context, the persistent one.
     */
    @Test void aDisposedKeptContextIsRefusedNeverHandedOutAgain() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        RecordingContext group = kept(chromium, new RecordingContext(12)).getFirst();
        org.cef.browser.CefRequestContext page = chromium.browserContext(group);
        chromium.release(group);
        page.dispose();
        assertThat(group.disposals()).isEqualTo(1);
        assertThatThrownBy(() -> chromium.browserContext(group)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Chromium.anotherContext(page)).as("a page's DevTools too").isInstanceOf(IllegalStateException.class);
        end(chromium);
    }

    /**
     * Plan 6 Task 11: the proxy set before any start is only remembered, and starts nothing; the start applies it to
     * Chromium's global context as Chromium's context initializes, on Chromium's thread, before any work given before,
     * and closes the context's connections. A Chromium given no proxy applies the system's, as the profile may keep
     * another from an earlier run.
     */
    @Test void aProxySetBeforeAnyStartIsRememberedStartsNothingAndTheStartAppliesItToTheGlobalContext() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.setProxy(Optional.of("127.0.0.1:3128"));
        chromium.setProxy(Optional.of("127.0.0.1:8118"));
        flushUi();
        assertThat(chromium.state()).isEqualTo(Chromium.State.NOT_STARTED);
        assertThat(starts).as("it starts nothing").isEmpty();
        var order = new CopyOnWriteArrayList<Object>();
        chromium.client();
        assertThat(chromium.enter()).isTrue();
        chromium.onChromiumThread(() -> order.add(global.hasPreference("proxy") ? global.getPreference("proxy") : "none"));
        Thread.ofPlatform().name("chromium-ui").start(() -> chromium.contextInitialized(global)).join(TimeUnit.SECONDS.toMillis(10));
        assertThat(global.getPreference("proxy")).isEqualTo(Map.of("mode", "fixed_servers", "server", "127.0.0.1:8118"));
        assertThat(global.setOn("proxy")).as("on Chromium's thread, inside its context's initialization").isEqualTo("chromium-ui");
        assertThat(order).as("before the work given before it").containsExactly(Map.of("mode", "fixed_servers", "server", "127.0.0.1:8118"));
        assertThat(global.connectionsClosed()).as("set once: the value remembered last").isEqualTo(1);
        end(chromium);

        Chromium unset = new Chromium(data, () -> { }, recording, new AboutScheme(), new PreviewScheme(Runnable::run), cause -> { }, new StartOnce(), LIMIT);
        var another = new RecordingContext(101);
        unset.client();
        assertThat(unset.enter()).isTrue();
        Thread.ofPlatform().start(() -> unset.contextInitialized(another)).join(TimeUnit.SECONDS.toMillis(10));
        assertThat(another.getPreference("proxy")).as("the system's proxy settings").isEqualTo(Map.of("mode", "system"));
        end(unset);
    }

    /**
     * Plan 6 Task 11: once Chromium runs, a change is handed to Chromium's thread, which gives it to the global context
     * and to every in-memory context still kept ({@link NetworkPreferencesTest} has which ones), and closes their
     * connections.
     */
    @Test void aChangeWhileRunningIsGivenOnChromiumsThreadToTheGlobalContextAndTheKeptOnes() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        RecordingContext open = kept(chromium, new RecordingContext(1)).getFirst();
        assertThat(open.getPreference("proxy")).as("made with the proxy remembered").isEqualTo(Map.of("mode", "system"));
        var handed = new ConcurrentLinkedQueue<Runnable>();
        chromium.handOverWith(handed::add);
        chromium.setProxy(Optional.of("[::1]:8118"));
        assertThat(handed).as("handed to Chromium's thread").hasSize(1);
        assertThat(global.getPreference("proxy")).as("not before Chromium's thread runs it").isEqualTo(Map.of("mode", "system"));
        Thread.ofPlatform().name("chromium-ui").start(() -> handed.poll().run()).join(TimeUnit.SECONDS.toMillis(10));
        var proxy = Map.of("mode", "fixed_servers", "server", "[::1]:8118");
        assertThat(global.getPreference("proxy")).isEqualTo(proxy);
        assertThat(global.setOn("proxy")).isEqualTo("chromium-ui");
        assertThat(open.getPreference("proxy")).isEqualTo(proxy);
        assertThat(open.setOn("proxy")).isEqualTo("chromium-ui");
        assertThat(global.connectionsClosed()).isEqualTo(2);
        assertThat(open.connectionsClosed()).isEqualTo(2);
        end(chromium);
    }

    /**
     * The review's M4: an in-memory context made between {@code setProxy}'s write and Chromium's thread running the change
     * is made with the new proxy, whichever of the two Chromium's thread runs first; one made before the write gets it
     * from the change.
     */
    @Test void aContextMadeBetweenTheProxysWriteAndItsHandOverGetsTheNewProxy() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var before = new RecordingContext(1);
        var made = new java.util.ArrayDeque<>(List.of(before, new RecordingContext(2), new RecordingContext(3)));
        chromium.makeContextsWith(made::poll);
        chromium.client();
        assertThat(chromium.enter()).isTrue();
        chromium.inMemoryContext();
        Thread.ofPlatform().start(() -> chromium.contextInitialized(global)).join(TimeUnit.SECONDS.toMillis(10));
        var handed = new ConcurrentLinkedQueue<Runnable>();
        chromium.handOverWith(handed::add);
        var proxy = Map.of("mode", "fixed_servers", "server", "127.0.0.1:8118");

        var asked = chromium.inMemoryContext();
        chromium.setProxy(Optional.of("127.0.0.1:8118"));
        assertThat(handed).as("the context's making, then the change").hasSize(2);
        handed.poll().run();
        RecordingContext first = (RecordingContext) asked.get(10, TimeUnit.SECONDS);
        assertThat(first.getPreference("proxy")).as("made after the write: the new proxy at once").isEqualTo(proxy);
        handed.poll().run();
        assertThat(first.getPreference("proxy")).isEqualTo(proxy);
        assertThat(before.getPreference("proxy")).as("made before the write: given it by the change").isEqualTo(proxy);

        chromium.setProxy(Optional.of("127.0.0.1:3128"));
        var later = chromium.inMemoryContext();
        handed.poll().run();
        handed.poll().run();
        assertThat(((RecordingContext) later.get(10, TimeUnit.SECONDS)).getPreference("proxy"))
            .isEqualTo(Map.of("mode", "fixed_servers", "server", "127.0.0.1:3128"));
        end(chromium);
    }

    /**
     * Ruling F4 through Chromium: while the proxy is on, an in-memory context that refuses it is never handed out, so a
     * /dev/null page cannot load instead of going direct; the failure is told once, on the UI thread, with Chromium's
     * reason ({@link NetworkPreferencesTest} has the context's dispose, and the proxy off).
     */
    @Test void whileTheProxyIsOnAnInMemoryContextThatRefusesItIsNeverHandedOutAndTheFailureIsTold() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var toldOf = new CopyOnWriteArrayList<String>();
        chromium.onProxyFailed(reason -> toldOf.add(reason + (EventQueue.isDispatchThread() ? " on the UI thread" : "")));
        chromium.setProxy(Optional.of("127.0.0.1:8118"));
        var refusing = new RecordingContext(3).refusing("no request context");
        chromium.makeContextsWith(() -> refusing);
        chromium.client();
        assertThat(chromium.enter()).isTrue();
        var on = chromium.inMemoryContext();
        Thread.ofPlatform().start(() -> chromium.contextInitialized(global)).join(TimeUnit.SECONDS.toMillis(10));
        assertThat(failure(on)).isInstanceOf(IllegalStateException.class).hasMessage(Chromium.PROXY_REFUSED + ": no request context");
        assertThat(refusing.disposals()).as("never kept").isEqualTo(1);
        flushUi();
        assertThat(toldOf).containsExactly("no request context on the UI thread");
        end(chromium);
    }

    /**
     * Plan 6 Task 11: a change Chromium's thread does not take is told, on the UI thread; once Chromium has closed, a
     * change is only remembered, touches nothing native and tells nothing.
     */
    @Test void aChangeChromiumsThreadRefusesIsToldAndAChangeAfterTheCloseTouchesNothing() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var toldOf = new CopyOnWriteArrayList<String>();
        chromium.onProxyFailed(toldOf::add);
        List<RecordingContext> kept = kept(chromium, new RecordingContext(5));
        var handed = new ConcurrentLinkedQueue<Runnable>();
        var hands = new AtomicInteger();
        chromium.handOverWith(task -> hands.incrementAndGet() == 1 && handed.add(task));
        chromium.setProxy(Optional.of("127.0.0.1:8118"));
        handed.poll().run();
        chromium.setProxy(Optional.of("127.0.0.1:3128"));
        flushUi();
        assertThat(toldOf).as("a change Chromium's thread did not take").containsExactly("Chromium's thread did not take the work");
        end(chromium);
        handed.clear();
        chromium.setProxy(Optional.of("127.0.0.1:9050"));
        flushUi();
        assertThat(handed).as("closed: nothing handed over").isEmpty();
        assertThat(toldOf).hasSize(1);
        assertThat(kept.getFirst().connectionsClosed()).as("at its making and the one change taken").isEqualTo(2);
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
        Thread.ofPlatform().name("chromium-ui").start(() -> chromium.contextInitialized(global)).join(TimeUnit.SECONDS.toMillis(10));
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

    /**
     * Plan 6 Task 8, before any start: Clear Website Data marks the profile on the worker, after the data directory is
     * made its owner's alone, and wipes it there at once, as no Chromium uses it; Clear Cookies leaves its marker for
     * the start to come. Neither starts Chromium.
     */
    @Test void clearingBeforeAnyStartMarksOnTheWorkerWipesThereAndStartsNoChromium() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var live = live(chromium);
        Path cookies = profile();
        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        assertThat(cleared).as("nothing on the caller's thread").isNotDone();
        assertThat(clearMarker()).doesNotExist();
        assertThat(starts).as("the clearing's work, on the worker").hasSize(1);
        runNext();
        assertThat(restricted).as("H37 first").containsExactly("before the marker");
        assertThat(succeeded(cleared)).isTrue();
        assertThat(cookies).as("wiped now").doesNotExist();
        assertThat(clearMarker()).doesNotExist();

        var cookiesCleared = chromium.clearCookies();
        runNext();
        assertThat(succeeded(cookiesCleared)).isTrue();
        assertThat(Files.readString(clearMarker())).as("for the start to come").isEqualTo("cookies");
        assertThat(chromium.state()).isEqualTo(Chromium.State.NOT_STARTED);
        assertThat(starts).as("no start").isEmpty();
        assertThat(live).isEmpty();
        chromium.close();
    }

    /** A clearing queued before a start runs before it on the worker: the start then finds no profile to keep. */
    @Test void aClearingQueuedBeforeTheStartWipesBeforeTheStartEnters() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        Path cookies = profile();
        var cleared = chromium.clearWebsiteData(Set.of(), false);
        chromium.client();
        assertThat(starts).as("the clearing, then the start").hasSize(2);
        runNext();
        assertThat(succeeded(cleared)).isTrue();
        assertThat(cookies).doesNotExist();
        assertThat(chromium.state()).isEqualTo(Chromium.State.STARTING);
        starts.clear(); // the start itself, which would reach JCEF
        end(chromium);
    }

    /** After a failed start, a clearing only marks: the next start wipes or clears, and nothing starts now. */
    @Test void clearingAfterAFailedStartOnlyMarksAndStartsNoChromium() throws Exception {
        Chromium chromium = chromium(recording, Duration.ofMillis(50));
        var live = live(chromium);
        chromium.client();
        assertThat(told()).isInstanceOf(TimeoutException.class);
        assertThat(chromium.state()).isEqualTo(Chromium.State.FAILED);
        starts.clear();
        Path cookies = profile();
        var cookiesCleared = chromium.clearCookies();
        runNext();
        assertThat(succeeded(cookiesCleared)).isTrue();
        assertThat(Files.readString(clearMarker())).isEqualTo("cookies");
        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        runNext();
        assertThat(succeeded(cleared)).isTrue();
        assertThat(Files.readString(clearMarker())).as("all wins").isEqualTo("all");
        assertThat(cookies).as("a failed start may still hold its profile: the next start wipes it").isRegularFile();
        assertThat(starts).isEmpty();
        assertThat(live).isEmpty();
        assertThat(chromium.state()).isEqualTo(Chromium.State.FAILED);
        chromium.close();
    }

    /**
     * F16, while starting: the clearing marks the profile, then waits for the client, and clears live once Chromium
     * runs: the cookies go, and their sites, as https and http origins, are cleared with the ones given. The marker stays
     * for the next start's wipe (V1).
     */
    @Test void clearingWhileChromiumStartsWaitsForTheClientThenClearsLive() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var live = live(chromium);
        startEntered(chromium);
        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        runNext();
        assertThat(Files.readString(clearMarker())).isEqualTo("all");
        flushUi();
        assertThat(cleared).as("waits for the client").isNotDone();
        assertThat(live).isEmpty();
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        assertThat(chromium.state()).isEqualTo(Chromium.State.RUNNING);
        await(cleared);
        assertThat(live).containsExactly("cookies on the UI thread", "storage on the UI thread: [http://b.example, http://c.example, "
            + "https://a.example, https://b.example, https://c.example]");
        assertThat(succeeded(cleared)).isTrue();
        assertThat(Files.readString(clearMarker())).as("V1: wiped at the next start").isEqualTo("all");
        end(chromium);
    }

    /** F16: a start that fails while a clearing waits for it leaves the marker, which suffices: nothing native runs. */
    @Test void aStartThatFailsWhileAClearingWaitsLeavesTheMarkerToTheNextStart() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var live = live(chromium);
        startEntered(chromium);
        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        var cookiesCleared = chromium.clearCookies();
        runNext();
        runNext();
        chromium.threw(new UnsatisfiedLinkError("no Chromium libraries"));
        await(cleared);
        await(cookiesCleared);
        assertThat(succeeded(cleared)).isTrue();
        assertThat(succeeded(cookiesCleared)).isTrue();
        assertThat(Files.readString(clearMarker())).isEqualTo("all");
        flushUi();
        assertThat(live).isEmpty();
        chromium.close();
    }

    /** Task 2's A: a close while a clearing waits for the client leaves the marker and touches nothing native. */
    @Test void aCloseWhileAClearingWaitsTouchesNothingNative() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var live = live(chromium);
        startEntered(chromium);
        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        runNext();
        EventQueue.invokeAndWait(chromium::close);
        await(cleared);
        assertThat(succeeded(cleared)).isTrue();
        assertThat(Files.readString(clearMarker())).isEqualTo("all");
        flushUi();
        assertThat(live).isEmpty();
        var after = chromium.clearCookies();
        runNext();
        assertThat(succeeded(after)).as("closed: the marker only").isTrue();
        assertThat(live).isEmpty();
        chromium.terminated();
    }

    /** F16: the whole clearing is bounded, here for a start that never ends; past the bound it fails. */
    @Test void aClearingThatWaitsPastItsBoundFails() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.clearing().clearsWithin(Duration.ofMillis(100));
        live(chromium);
        startEntered(chromium);
        var cleared = chromium.clearWebsiteData(Set.of(), false);
        runNext();
        assertThat(failure(await(cleared))).isInstanceOf(TimeoutException.class).hasMessage("Chromium did not clear its data within 60 seconds");
        end(chromium);
    }

    /**
     * While running, Clear Cookies deletes them live, then takes back the marker it wrote first, which kept them for the
     * next start had Chromium failed meanwhile; Clear Website Data clears live and keeps its marker.
     */
    @Test void clearingWhileRunningClearsLive() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var live = live(chromium);
        startEntered(chromium);
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        var cookiesCleared = chromium.clearCookies();
        runNext();
        assertThat(Files.readString(clearMarker())).as("written first").isEqualTo("cookies");
        flushUi();
        assertThat(live).containsExactly("cookies on the UI thread");
        runNext(); // the marker taken back, on the worker
        assertThat(succeeded(cookiesCleared)).isTrue();
        assertThat(clearMarker()).doesNotExist();

        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        runNext();
        await(cleared);
        assertThat(live).hasSize(3).endsWith("storage on the UI thread: [http://b.example, http://c.example, https://a.example, "
            + "https://b.example, https://c.example]");
        assertThat(Files.readString(clearMarker())).isEqualTo("all");
        var again = chromium.clearCookies();
        runNext();
        flushUi();
        runNext();
        assertThat(succeeded(again)).isTrue();
        assertThat(Files.readString(clearMarker())).as("Clear Cookies never takes back a wipe").isEqualTo("all");
        end(chromium);
    }

    /** A live clearing that fails fails the clearing, and Clear Cookies' marker stays for the next start. */
    @Test void aLiveClearingThatFailsFailsAndLeavesTheMarker() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        chromium.clearing().clearLiveWith(() -> CompletableFuture.failedFuture(new IllegalStateException("Chromium could not delete its cookies")),
            origins -> CompletableFuture.completedFuture(null));
        startEntered(chromium);
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        var cookiesCleared = chromium.clearCookies();
        runNext();
        assertThat(failure(await(cookiesCleared))).hasMessage("Chromium could not delete its cookies");
        assertThat(Files.readString(clearMarker())).isEqualTo("cookies");
        end(chromium);
    }

    /**
     * The start finds the profile marked: as it enters, after the data directory is made its owner's alone, an
     * {@code all} marker wipes it before JCEF, and a {@code cookies} marker has the cookies deleted before the client is
     * made, so before any page; the marker goes once they have.
     */
    @Test void aStartThatEntersWipesAMarkedProfileAndClearsMarkedCookiesBeforeTheClient() throws Exception {
        Path cookies = profile();
        ProfileWipe.mark(data, ProfileWipe.Mark.ALL);
        var seen = new CopyOnWriteArrayList<String>();
        Chromium wiped = new Chromium(data, () -> seen.add(Files.exists(cookies) ? "profile there" : "profile gone"), recording,
            new AboutScheme(), new PreviewScheme(Runnable::run), cause -> { }, process, LIMIT);
        wiped.client();
        starts.clear();
        assertThat(wiped.enter()).isTrue();
        assertThat(seen).as("H37 before the wipe").containsExactly("profile there");
        assertThat(cookies).doesNotExist();
        assertThat(clearMarker()).doesNotExist();
        assertThat(marker()).as("then the run marker").isRegularFile();
        var asked = new AtomicInteger();
        assertThat(succeeded(wiped.clearing().beforeClient(() -> {
            asked.incrementAndGet();
            return CompletableFuture.completedFuture(Set.of());
        }))).as("no cookies wait").isTrue();
        assertThat(asked).hasValue(0);
        end(wiped);

        Chromium later = new Chromium(data, () -> { }, recording, new AboutScheme(), new PreviewScheme(Runnable::run), cause -> { }, new StartOnce(), LIMIT);
        ProfileWipe.mark(data, ProfileWipe.Mark.COOKIES);
        later.client();
        starts.clear();
        assertThat(later.enter()).isTrue();
        var deleting = new CompletableFuture<Set<String>>();
        var gate = later.clearing().beforeClient(() -> {
            asked.incrementAndGet();
            return deleting;
        });
        assertThat(asked).hasValue(1);
        assertThat(gate).as("the client waits for the cookies").isNotDone();
        deleting.complete(Set.of(".a.example"));
        runNext();
        assertThat(succeeded(gate)).isTrue();
        assertThat(clearMarker()).doesNotExist();
        end(later);
    }

    /**
     * Review round 1's item 8: cookies that cannot be deleted as Chromium starts are not left silently: the marker becomes
     * a wipe at the next start, the failure is told as Clear Cookies' own, and Chromium goes on.
     */
    @Test void markedCookiesThatCannotBeDeletedMarkAWipeAreToldAndTheStartGoesOn() throws Exception {
        var chromiumLog = Logger.getLogger(Chromium.class.getName());
        var logged = new CopyOnWriteArrayList<String>();
        var listening = new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record.getLevel() + " " + record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parents = chromiumLog.getUseParentHandlers();
        chromiumLog.setUseParentHandlers(false);
        chromiumLog.addHandler(listening);
        try {
            ProfileWipe.mark(data, ProfileWipe.Mark.COOKIES);
            Chromium chromium = chromium(recording, LIMIT);
            var toldOf = new CopyOnWriteArrayList<String>();
            chromium.onClearFailed(cause -> toldOf.add(cause.getMessage() + (EventQueue.isDispatchThread() ? " on the UI thread" : "")));
            startEntered(chromium);
            var gate = chromium.clearing().beforeClient(() -> CompletableFuture.failedFuture(new IllegalStateException("refused")));
            assertThat(succeeded(gate)).as("the start goes on").isTrue();
            runNext(); // the wipe marked, on the worker
            assertThat(Files.readString(clearMarker())).as("the next start wipes").isEqualTo("all");
            flushUi();
            assertThat(toldOf).containsExactly("refused on the UI thread");
            assertThat(logged).singleElement().asString().startsWith("WARNING Chromium could not delete the cookies Clear Cookies left");
            end(chromium);
        } finally {
            chromiumLog.removeHandler(listening);
            chromiumLog.setUseParentHandlers(parents);
        }
    }

    /**
     * Review round 1's item 5: a profile whose wipe could not move it keeps its marker, and Chromium starts with it as
     * usual (V1); its cookies still go before the client is made, as a Clear Cookies' would.
     */
    @Test void aProfileTheWipeCouldNotMoveHasItsCookiesDeletedBeforeTheClient() throws Exception {
        Path cookies = profile();
        ProfileWipe.mark(data, ProfileWipe.Mark.ALL);
        Chromium chromium = chromium(recording, LIMIT);
        chromium.clearing().wipesWith(new ProfileWipe.Disk() {
            @Override public void move(Path from, Path to) throws java.io.IOException {
                throw new java.nio.file.FileSystemException(from.toString(), to.toString(), "in use");
            }
        });
        var wipeLog = Logger.getLogger(ProfileWipe.class.getName());
        boolean parents = wipeLog.getUseParentHandlers();
        wipeLog.setUseParentHandlers(false);
        try {
            startEntered(chromium);
        } finally {
            wipeLog.setUseParentHandlers(parents);
        }
        assertThat(cookies).as("kept whole").isRegularFile();
        var asked = new AtomicInteger();
        var gate = chromium.clearing().beforeClient(() -> {
            asked.incrementAndGet();
            return CompletableFuture.completedFuture(Set.of());
        });
        assertThat(asked).as("the cookies go all the same").hasValue(1);
        assertThat(succeeded(gate)).isTrue();
        runNext();
        assertThat(Files.readString(clearMarker())).as("the wipe stays marked").isEqualTo("all");
        end(chromium);
    }

    /**
     * Review round 1's item 12: a close between the cookies' deletion and the storage's clearing touches nothing more:
     * the storage is never asked, and the marker waits for the next start.
     */
    @Test void aCloseBetweenTheCookiesAndTheStorageTouchesNothingMore() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        var deleting = new CompletableFuture<Set<String>>();
        var storage = new AtomicInteger();
        chromium.clearing().clearLiveWith(() -> deleting, origins -> {
            storage.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        startEntered(chromium);
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        var cleared = chromium.clearWebsiteData(Set.of("https://a.example"), false);
        runNext();
        flushUi();
        assertThat(cleared).as("the cookies are being deleted").isNotDone();
        EventQueue.invokeAndWait(chromium::close);
        deleting.complete(Set.of(".b.example"));
        await(cleared);
        flushUi();
        assertThat(succeeded(cleared)).isTrue();
        assertThat(storage).as("nothing native after the close").hasValue(0);
        assertThat(Files.readString(clearMarker())).isEqualTo("all");
        chromium.terminated();
    }

    /**
     * Ruling V25: a clearing whose database ran in memory marks the database for the next start with the wipe; before
     * any start the profile is wiped at once, and the database's mark stays until the database's files go.
     */
    @Test void aClearingThatMarksTheDatabaseKeepsThatMarkPastTheProfilesWipe() throws Exception {
        Chromium chromium = chromium(recording, LIMIT);
        Path cookies = profile();
        var cleared = chromium.clearWebsiteData(Set.of(), true);
        runNext();
        assertThat(succeeded(cleared)).isTrue();
        assertThat(cookies).doesNotExist();
        assertThat(Files.readString(clearMarker())).isEqualTo("database");

        var live = live(chromium);
        startEntered(chromium);
        EventQueue.invokeAndWait(() -> chromium.clientCreated(null));
        var again = chromium.clearWebsiteData(Set.of(), true);
        runNext();
        await(again);
        assertThat(succeeded(again)).isTrue();
        assertThat(live).hasSize(2);
        assertThat(Files.readString(clearMarker())).isEqualTo("all database");
        end(chromium);
    }

    /**
     * Has {@code chromium}'s live clearing recorded instead of reaching JCEF: the cookies' deletion answers the domains
     * {@code .b.example} and {@code C.example}, and each step says on which thread it ran.
     */
    private List<String> live(Chromium chromium) {
        var live = new CopyOnWriteArrayList<String>();
        chromium.clearing().clearLiveWith(() -> {
            live.add("cookies " + thread());
            return CompletableFuture.completedFuture(Set.of(".b.example", "C.example"));
        }, origins -> {
            live.add("storage " + thread() + ": " + origins.stream().sorted().toList());
            return CompletableFuture.completedFuture(null);
        });
        return live;
    }

    private static String thread() {
        return EventQueue.isDispatchThread() ? "on the UI thread" : "off the UI thread";
    }

    /** Has {@code chromium}'s start entered JCEF, as its worker would, without running it. */
    private void startEntered(Chromium chromium) {
        chromium.client();
        starts.clear();
        assertThat(chromium.enter()).isTrue();
    }

    /** Runs the next task the worker was given. */
    private void runNext() {
        Runnable next = starts.poll();
        assertThat(next).as("a task for the worker").isNotNull();
        next.run();
    }

    /** Chromium's profile in the data directory, with a cookie file. */
    private Path profile() throws java.io.IOException {
        Path cookies = Files.createDirectories(data.resolve("chromium").resolve("Default")).resolve("Cookies");
        Files.writeString(cookies, "session=1");
        return cookies;
    }

    private Path clearMarker() {
        return data.resolve("chromium-clear");
    }

    /** Waits up to 10 seconds for {@code future} to complete, as it does or fails. */
    private static <T> CompletableFuture<T> await(CompletableFuture<T> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException failed) {
            // looked at by the caller
        }
        return future;
    }

    private static boolean succeeded(CompletableFuture<?> future) {
        assertThat(future).isCompleted();
        return !future.isCompletedExceptionally();
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
            new AboutScheme(), new PreviewScheme(Runnable::run), cause -> {
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
