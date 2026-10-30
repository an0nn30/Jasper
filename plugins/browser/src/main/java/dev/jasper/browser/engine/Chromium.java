package dev.jasper.browser.engine;

import dev.jasper.browser.engine.ChromiumLife.Step;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *
 * <p>Its {@link RunMarker} marks it in the data directory from the moment its start enters JCEF, before Chromium
 * initializes, until Jasper closes it or it has ended, so a fatal Chromium error that ends Jasper meanwhile leaves the
 * mark.
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
    private final RunMarker marker;
    private final CountDownLatch ended = new CountDownLatch(1);
    private final AtomicBoolean warned = new AtomicBoolean();
    // Set by the worker before Chromium can initialize, so before any dispose or client is decided.
    private volatile ChromiumApp app;
    // Guarded by installing: every installer given, those waiting for the client, and the client they ran on.
    private final Object installing = new Object();
    private final Set<Consumer<CefClient>> installers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Consumer<CefClient>> waiting = new ArrayList<>();
    private boolean installed;
    private CefClient installedOn;

    /**
     * A Chromium that starts nothing until {@link #client()} is first called.
     *
     * @param dataDir the plugin's data directory, which keeps Chromium's cookies, cache and log, and its run marker
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
        marker = new RunMarker(dataDir);
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
     * Has {@code installer} set up the client before any page is made on it. JCEF keeps the first handler of each kind
     * a client is given and ignores later ones, so each kind has one handler, installed once, that hands each page's
     * callbacks to that page. An installer given before the client is made runs as it is made, on the UI thread, in
     * the order given and before {@link #client()}'s future completes; one given later runs at once on the client. An
     * installer that throws fails the start. The same installer given again runs once. Give installers when a page
     * first asks for Chromium, not when the plugin starts: naming the client's type loads a Chromium class.
     *
     * @param installer what the client needs before any page, such as its display and load handlers
     */
    public void onClient(Consumer<CefClient> installer) {
        Objects.requireNonNull(installer, "installer");
        CefClient now;
        synchronized (installing) {
            if (!installers.add(installer)) return;
            if (!installed) {
                waiting.add(installer);
                return;
            }
            now = installedOn;
        }
        installer.accept(now);
    }

    /**
     * Where Chromium's life stands.
     *
     * @return the state
     */
    public State state() { return life.state(); }

    /**
     * Why Chromium cannot serve pages: what failed its start, as {@code startFailed} is told, or, once it is closed, that
     * it can start only once each time Jasper runs. Any thread.
     *
     * @return the reason; empty while it has not started, starts or runs
     */
    public Optional<Throwable> unavailable() { return life.unavailable(); }

    /**
     * Closes Chromium: its client's every browser, then its app, once, however often this is called. On the UI thread
     * it returns at once, and a thread of its own waits for Chromium's end; anywhere else the caller waits. Either wait
     * lasts at most 10 seconds. A Chromium that never started closes at once and touches no Chromium class. Its run
     * marker goes at once, before Chromium has ended.
     */
    @Override public void close() {
        List<Step<CefClient>> steps = life.close();
        // Jasper closes Chromium: the marker goes now, as a quit's dispose can outlast Jasper's exit.
        marker.stopped();
        run(steps);
        if (!EventQueue.isDispatchThread()) awaitEnd();
    }

    /** Whether the client may still be made. UI thread. */
    boolean mayCreateClient() { return life.mayCreateClient(); }

    /** Runs the installers on the client just made, before it reaches any page; once. UI thread. */
    void install(CefClient made) {
        List<Consumer<CefClient>> each;
        synchronized (installing) {
            if (installed) return;
            installed = true;
            installedOn = made;
            each = List.copyOf(waiting);
            waiting.clear();
        }
        for (Consumer<CefClient> installer : each) installer.accept(made);
    }

    /** The client is made. UI thread. */
    void clientCreated(CefClient made) { run(life.clientCreated(made)); }

    /** The client could not be made: the start fails. UI thread. */
    void clientFailed(Throwable cause) { run(life.failed(cause)); }

    /** The start, on the worker. Calls JCEF only while the start is still under way. */
    private void start() {
        if (!enter()) return;
        ChromiumApp started;
        try {
            started = ChromiumApp.start(dataDir, about, this::terminated);
        } catch (Throwable failure) { // JCEF reports a runtime without Chromium's libraries as an Error.
            threw(failure);
            return;
        }
        app = started;
        started.onInitialized(() -> run(life.initialized()));
    }

    /**
     * The worker begins the start: whether it may call JCEF, which it may only while the start is under way. When it may,
     * Chromium is marked first, so a fatal error while it initializes leaves the mark. A close that comes between the
     * two leaves no mark: once closed, the marker is not written. Worker.
     */
    boolean enter() {
        if (!life.enter()) return false;
        marker.started();
        return true;
    }

    /** The start threw before Chromium had an app. Worker. */
    void threw(Throwable failure) { run(life.threw(failure)); }

    /** Chromium has ended, or its dispose failed. Any thread. */
    void terminated() { run(life.terminated()); }

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
                case Step.End<CefClient> end -> {
                    marker.stopped();
                    ended.countDown();
                }
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
            terminated();
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
