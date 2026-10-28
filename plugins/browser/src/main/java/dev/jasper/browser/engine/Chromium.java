package dev.jasper.browser.engine;

import dev.jasper.browser.engine.ChromiumLife.Step;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
 * again after its dispose. {@link ChromiumLife} makes every decision of that life; this class carries them out.
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
    private final Duration startLimit;
    private final ChromiumLife<CefClient> life;
    private final CountDownLatch ended = new CountDownLatch(1);
    private final AtomicBoolean warned = new AtomicBoolean();
    // Set by the worker before Chromium can initialize, so before any dispose or client is decided.
    private volatile ChromiumApp app;

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
        this.startLimit = Objects.requireNonNull(startLimit, "startLimit");
        life = new ChromiumLife<>(Objects.requireNonNull(process, "process"));
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
        var asked = life.ask();
        run(asked.steps());
        return asked.client();
    }

    /**
     * Where Chromium's life stands.
     *
     * @return the state
     */
    public State state() { return life.state(); }

    /**
     * Closes Chromium: its client's every browser, then its app, once, however often this is called. On the UI thread
     * it returns at once, and a thread of its own waits for Chromium's end; anywhere else the caller waits. Either wait
     * lasts at most 10 seconds. A Chromium that never started closes at once and touches no Chromium class.
     */
    @Override public void close() {
        run(life.close());
        if (!EventQueue.isDispatchThread()) awaitEnd();
    }

    /** Whether the client may still be made. UI thread. */
    boolean mayCreateClient() { return life.mayCreateClient(); }

    /** The client is made. UI thread. */
    void clientCreated(CefClient made) { run(life.clientCreated(made)); }

    /** The client could not be made: the start fails. UI thread. */
    void clientFailed(Throwable cause) { run(life.failed(cause)); }

    /** The start, on the worker. Calls JCEF only while the start is still under way. */
    private void start() {
        if (!life.enter()) return;
        ChromiumApp started;
        try {
            started = ChromiumApp.start(dataDir, about, () -> run(life.terminated()));
        } catch (Throwable failure) { // JCEF reports a runtime without Chromium's libraries as an Error.
            run(life.threw(failure));
            return;
        }
        app = started;
        started.onInitialized(() -> run(life.initialized()));
    }

    /** Carries out the life's steps, in order, outside its lock. */
    private void run(List<Step<CefClient>> steps) {
        for (Step<CefClient> step : steps) {
            switch (step) {
                case Step.Start<CefClient> start -> handOver();
                case Step.CreateClient<CefClient> create -> EventQueue.invokeLater(() -> app.createClient(this));
                case Step.Complete<CefClient> done -> complete(done);
                case Step.Fail<CefClient> fail -> fail.client().completeExceptionally(fail.cause());
                case Step.Tell<CefClient> tell -> EventQueue.invokeLater(() -> startFailed.accept(tell.cause()));
                case Step.Dispose<CefClient> dispose -> onUiThread(this::dispose);
                case Step.End<CefClient> end -> ended.countDown();
                case Step.Await<CefClient> await -> {
                    if (EventQueue.isDispatchThread()) Thread.ofPlatform().daemon().name("jasper-browser-chromium-stop").start(this::awaitEnd);
                }
            }
        }
    }

    /** Hands the client to its future; generic, so this class casts nothing to a Chromium type. */
    private static <C> void complete(Step.Complete<C> done) { done.client().complete(done.value()); }

    /** Hands the start to the worker and arms its time limit: JCEF does not report every failed start. */
    private void handOver() {
        try {
            worker.execute(this::start);
        } catch (RejectedExecutionException refused) {
            run(life.failed(refused));
            return;
        }
        CompletableFuture.delayedExecutor(startLimit.toMillis(), TimeUnit.MILLISECONDS)
            .execute(() -> run(life.timedOut(new TimeoutException(SLOW))));
    }

    /** Disposes the app, on the UI thread, when the life says so: once. */
    private void dispose() {
        try {
            app.dispose();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not stop", failure);
            run(life.terminated());
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

    private static void onUiThread(Runnable task) {
        if (EventQueue.isDispatchThread()) task.run();
        else EventQueue.invokeLater(task);
    }
}
