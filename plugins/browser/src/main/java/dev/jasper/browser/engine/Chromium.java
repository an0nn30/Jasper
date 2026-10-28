package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.cef.CefClient;

/**
 * Chromium's one life in a plugin run: in process and off screen, started when a page first asks for it and disposed
 * once. In-process Chromium starts at most once in a Jasper process, because JCEF's app is process-wide and cannot start
 * again after its dispose.
 *
 * <p>Making, closing or asking the state of a Chromium that never started loads no Chromium class: every JCEF call is in
 * {@link ChromiumApp}, which only the start loads, so the plugin's start costs nothing until a page is first shown.
 */
public final class Chromium implements AutoCloseable {
    /** Where Chromium's life stands. */
    public enum State {
        /** No page has asked for Chromium yet. */
        NOT_STARTED,
        /** The start is under way. */
        STARTING,
        /** The client is ready. */
        RUNNING,
        /** The start failed. It is not tried again. */
        FAILED,
        /** Closed: Chromium is ending or has ended, and serves nothing more. */
        STOPPED
    }

    static final String ONCE = "Chromium can start once each time Jasper runs. Quit and reopen Jasper to use the browser again.";
    static final String SLOW = "Chromium did not start within 90 seconds";
    private static final Duration START_LIMIT = Duration.ofSeconds(90);
    private static final Duration STOP_LIMIT = Duration.ofSeconds(10);
    private static final System.Logger LOG = System.getLogger(Chromium.class.getName());

    private final Path dataDir;
    private final Executor worker;
    private final AboutScheme about;
    private final Consumer<Throwable> startFailed;
    private final StartOnce process;
    private final Duration startLimit;
    private final CountDownLatch ended = new CountDownLatch(1);
    private final AtomicBoolean told = new AtomicBoolean();
    private final AtomicBoolean warned = new AtomicBoolean();
    private final Object lock = new Object();
    // Guarded by lock. entered: the start reached JCEF; threw: it failed before Chromium had an app.
    private State state = State.NOT_STARTED;
    private CompletableFuture<CefClient> client;
    private IllegalStateException refusal;
    private boolean entered, threw, initialized;
    private ChromiumApp app;

    /**
     * A Chromium that starts nothing until {@link #client()} is first called.
     *
     * @param dataDir the plugin's data directory, which keeps Chromium's cookies, cache and log
     * @param worker a platform thread of the plugin's own, which runs the start; never the UI thread, which the start
     *     needs free, or a virtual thread
     * @param about the pages {@code jasper-about:} serves
     * @param startFailed told once, on the UI thread, why Chromium cannot serve pages
     */
    public Chromium(Path dataDir, Executor worker, AboutScheme about, Consumer<Throwable> startFailed) {
        this(dataDir, worker, about, startFailed, StartOnce.PROCESS, START_LIMIT);
    }

    /** As the public constructor, with the once-per-process guard and the start's time limit given. */
    Chromium(Path dataDir, Executor worker, AboutScheme about, Consumer<Throwable> startFailed, StartOnce process, Duration startLimit) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.about = Objects.requireNonNull(about, "about");
        this.startFailed = Objects.requireNonNull(startFailed, "startFailed");
        this.process = Objects.requireNonNull(process, "process");
        this.startLimit = Objects.requireNonNull(startLimit, "startLimit");
    }

    /**
     * The client every page is made from. The first call hands Chromium's start to the worker and returns; later calls
     * return the same future. It completes on the UI thread once Chromium is ready. It fails when the start fails or
     * has not reached Chromium's initialization within 90 seconds, and when {@link #close()} cuts the start short. After
     * {@code close()}, or once another Chromium has started in this process, it fails at once and starts nothing. Each
     * failure but a cut-short start is told to {@code startFailed}, once.
     *
     * @return the client, once Chromium is ready
     */
    public CompletableFuture<CefClient> client() {
        CompletableFuture<CefClient> starting;
        synchronized (lock) {
            if (state == State.STOPPED) {
                tell(refusal());
                return client;
            }
            if (client != null) return client;
            if (!process.claim()) {
                state = State.FAILED;
                client = CompletableFuture.failedFuture(refusal());
                tell(refusal());
                return client;
            }
            state = State.STARTING;
            client = starting = new CompletableFuture<>();
        }
        try {
            worker.execute(() -> start(starting));
        } catch (RejectedExecutionException refused) {
            failed(refused);
            return starting;
        }
        CompletableFuture.delayedExecutor(startLimit.toMillis(), TimeUnit.MILLISECONDS).execute(this::timedOut);
        return starting;
    }

    /**
     * Where Chromium's life stands.
     *
     * @return the state
     */
    public State state() {
        synchronized (lock) { return state; }
    }

    /**
     * Closes Chromium: its client's every browser, then its app, once, however often this is called. On the UI thread
     * it returns at once, and a thread of its own waits for Chromium's end; anywhere else the caller waits. Either wait
     * lasts at most 10 seconds. A Chromium that never started closes at once and touches no Chromium class.
     */
    @Override public void close() {
        boolean first, dispose = false, nothingRan = false;
        CompletableFuture<CefClient> pending = null;
        IllegalStateException refused;
        synchronized (lock) {
            first = state != State.STOPPED;
            refused = refusal();
            if (first) {
                state = State.STOPPED;
                pending = client;
                client = CompletableFuture.failedFuture(refused);
                if (!entered || threw) nothingRan = true;
                else dispose = initialized; // Otherwise initialized() disposes the app when it arrives.
            }
        }
        if (first) {
            if (pending != null) pending.completeExceptionally(refused);
            if (nothingRan) ended.countDown();
            if (dispose) onUiThread(this::dispose);
        }
        if (!EventQueue.isDispatchThread()) awaitEnd();
        else if (first && ended.getCount() > 0) Thread.ofPlatform().daemon().name("jasper-browser-chromium-stop").start(this::awaitEnd);
    }

    /** Whether the start is still under way. */
    boolean starting() {
        synchronized (lock) { return state == State.STARTING; }
    }

    /** The client is made: Chromium runs, unless the start ended meanwhile. UI thread. */
    boolean running() {
        synchronized (lock) {
            if (state != State.STARTING) return false;
            state = State.RUNNING;
            return true;
        }
    }

    /** The start failed: its future fails and the reason is told, once. Does nothing once the start has ended. */
    void failed(Throwable cause) {
        CompletableFuture<CefClient> failing;
        synchronized (lock) {
            if (state != State.STARTING) return;
            state = State.FAILED;
            failing = client;
        }
        failing.completeExceptionally(cause);
        tell(cause);
    }

    /** The start, on the worker. Calls JCEF only while the start is still under way. */
    private void start(CompletableFuture<CefClient> starting) {
        synchronized (lock) {
            if (state != State.STARTING) return;
            entered = true;
        }
        ChromiumApp started;
        try {
            started = ChromiumApp.start(dataDir, about, ended::countDown);
        } catch (Throwable failure) { // JCEF reports a runtime without Chromium's libraries as an Error.
            boolean closed;
            synchronized (lock) {
                threw = true;
                closed = state == State.STOPPED;
            }
            if (closed) ended.countDown();
            else failed(failure);
            return;
        }
        synchronized (lock) { app = started; }
        started.onInitialized(() -> initialized(started, starting));
    }

    /** Chromium has initialized, on its own thread: the client is made on the UI thread, or, after a close, the app disposed. */
    private void initialized(ChromiumApp started, CompletableFuture<CefClient> starting) {
        boolean closed;
        synchronized (lock) {
            initialized = true;
            closed = state == State.STOPPED;
        }
        EventQueue.invokeLater(closed ? this::dispose : () -> started.createClient(this, starting));
    }

    /** No initialization within the limit: JCEF does not report every failed start, so the start fails here. */
    private void timedOut() {
        synchronized (lock) {
            if (initialized) return;
        }
        failed(new TimeoutException(SLOW));
    }

    /** Disposes the app; once, on the UI thread, as close() and initialized() leave it to exactly one of them. */
    private void dispose() {
        ChromiumApp target;
        synchronized (lock) { target = app; }
        try {
            target.dispose();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not stop", failure);
            ended.countDown();
        }
    }

    private void awaitEnd() {
        try {
            if (!ended.await(STOP_LIMIT.toMillis(), TimeUnit.MILLISECONDS) && warned.compareAndSet(false, true))
                LOG.log(System.Logger.Level.WARNING, "Chromium did not stop within 10 seconds");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Tells {@code startFailed} why Chromium cannot serve pages, once, on the UI thread. */
    private void tell(Throwable cause) {
        if (told.compareAndSet(false, true)) EventQueue.invokeLater(() -> startFailed.accept(cause));
    }

    /** The one refusal this Chromium answers with once it is closed or cannot start. Under lock. */
    private IllegalStateException refusal() {
        if (refusal == null) refusal = new IllegalStateException(ONCE);
        return refusal;
    }

    private static void onUiThread(Runnable task) {
        if (EventQueue.isDispatchThread()) task.run();
        else EventQueue.invokeLater(task);
    }
}
