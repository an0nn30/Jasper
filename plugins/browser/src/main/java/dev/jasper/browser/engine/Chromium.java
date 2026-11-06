package dev.jasper.browser.engine;

import dev.jasper.browser.engine.ChromiumLife.Step;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefRequestContext;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;

/**
 * Chromium's one life in a plugin run: in process and off screen, started when a page first asks for it and disposed
 * once. In-process Chromium starts at most once in a Jasper process, because JCEF's app is process-wide and cannot start
 * again after its dispose. {@link ChromiumLife} makes every decision of that life; this class carries them out.
 *
 * <p>Making, closing or asking the state of a Chromium that never started loads no Chromium class: every JCEF call is in
 * {@link ChromiumApp}, which only the start loads, so the plugin's start costs nothing until a page is first shown.
 *
 * <p>Pages are made on its client, and Markdown previews on a second client of the same Chromium, its preview client, which
 * none of the pages' handlers reach.
 *
 * <p>Its {@link RunMarker} marks it in the data directory from the moment its start enters JCEF, before Chromium
 * initializes, until Jasper closes it or it has ended, so a fatal Chromium error that ends Jasper meanwhile leaves the
 * mark.
 *
 * <p>Work for Chromium's own UI thread, which is not Swing's ({@link #onChromiumThread}), waits for Chromium's context to
 * initialize, and the in-memory request contexts of /dev/null pages are made there ({@link #inMemoryContext}); Chromium
 * keeps each for its group, hands each browser an object of its own over it ({@link #browserContext}), and disposes it
 * once its group has closed, or Chromium's close has swept it, and JCEF has disposed every browser's object over it.
 * Their pages' {@code jasper-about:} requests are served
 * through the pages' request handler ({@link #aboutResource}), as Chromium's scheme handler factory serves only its
 * global context.
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
    private final Runnable restrictData;
    private final Executor worker;
    private final AboutScheme about;
    private final PreviewScheme previews;
    private final Consumer<Throwable> startFailed;
    private final Duration startLimit;
    private final ChromiumLife<CefClient> life;
    private final RunMarker marker;
    private final CountDownLatch ended = new CountDownLatch(1);
    private final AtomicBoolean warned = new AtomicBoolean();
    // Set by the worker before Chromium can initialize, so before any dispose or client is decided.
    private volatile ChromiumApp app;
    private final Installers pageInstallers = new Installers(), previewInstallers = new Installers();
    // Work for Chromium's own UI thread, which fails once Chromium cannot serve pages.
    private final ChromiumThread chromiumThread = new ChromiumThread(this::unavailable);
    // Guarded by itself: each in-memory context made, with the count of its browsers' objects, kept once disposed too, so
    // a disposed one is refused; and whether Chromium's dispose has swept them.
    private final Map<CefRequestContext, KeptContext> contexts = new IdentityHashMap<>();
    private boolean contextsDisposed;
    // A test's maker of in-memory contexts; null for CEF's.
    private volatile Supplier<CefRequestContext> contextMaker;
    // Guarded by previewing: the preview client's future, made on the first ask.
    private final Object previewing = new Object();
    private CompletableFuture<CefClient> preview;
    // Set on the UI thread as the preview client is made, before any browser is made on it; read on Chromium's IO thread.
    private volatile CefClient previewMade;
    // Given as a page first asks for Chromium, before any page's browser is made; until then jasper-about: answers nothing.
    private volatile Predicate<CefBrowser> pageBrowsers;
    // Given as a preview first asks for its client, before any preview's browser is made: each preview browser's token.
    private volatile Function<CefBrowser, String> previewTokens;

    /**
     * A Chromium that starts nothing until {@link #client()} is first called.
     *
     * @param dataDir the plugin's data directory, which keeps Chromium's cookies, cache and log, and its run marker
     * @param restrictData makes the data directory its owner's alone (plan 5's H37); run on the worker as the start enters
     *     JCEF, before anything is written there
     * @param worker a platform thread of the plugin's own, which runs the start; never the UI thread, which the start
     *     needs free, or a virtual thread
     * @param about the pages {@code jasper-about:} serves
     * @param previews the Markdown previews' origins {@code jasper-preview:} serves
     * @param startFailed told once, on the UI thread, why Chromium cannot serve pages
     */
    public Chromium(Path dataDir, Runnable restrictData, Executor worker, AboutScheme about, PreviewScheme previews,
                    Consumer<Throwable> startFailed) {
        this(dataDir, restrictData, worker, about, previews, startFailed, StartOnce.PROCESS, START_LIMIT);
    }

    /** As the public constructor, with the once-per-process guard and the start's time limit given. */
    Chromium(Path dataDir, Runnable restrictData, Executor worker, AboutScheme about, PreviewScheme previews,
            Consumer<Throwable> startFailed, StartOnce process, Duration startLimit) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
        this.restrictData = Objects.requireNonNull(restrictData, "restrictData");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.about = Objects.requireNonNull(about, "about");
        this.previews = Objects.requireNonNull(previews, "previews");
        this.startFailed = Objects.requireNonNull(startFailed, "startFailed");
        this.startLimit = Objects.requireNonNull(startLimit, "startLimit");
        life = new ChromiumLife<>(Objects.requireNonNull(process, "process"));
        marker = new RunMarker(dataDir);
    }

    /**
     * The client every page is made from. The first call hands Chromium's start to the worker and returns; later calls
     * share that start. Each call returns a copy of the one future, which completes, or fails with the same cause, as it
     * does, so no caller can complete or cancel it for the others. It completes on the UI thread once Chromium is ready.
     * It fails when the start fails or has not reached Chromium's initialization within 90 seconds, and when
     * {@link #close()} cuts the start short. After {@code close()}, or once another Chromium has started in this
     * process, it fails at once and starts nothing. Each failure but a cut-short start is told to {@code startFailed},
     * once.
     *
     * @return the client, once Chromium is ready
     */
    public CompletableFuture<CefClient> client() {
        var asked = life.ask();
        run(asked.steps());
        return copy(asked.client());
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
        pageInstallers.add(installer);
    }

    /**
     * The client every Markdown preview is made on: a second client of the same Chromium, made on the UI thread once
     * {@link #client()} has completed, and set up only by the installers given to {@link #onPreviewClient}, so none of
     * the pages' handlers reach a preview. Asking for it starts Chromium as {@code client()} does, and never starts a
     * second one; each call returns a copy of the one future, as {@code client()} does. It fails as {@code client()}
     * fails, with the same reason, and when Chromium closes before it is made; after {@link #close()} it fails at once
     * and starts nothing. It completes on the UI thread, a failure too. Chromium's close disposes it with the pages'
     * client.
     *
     * @return the preview client, once it is made
     */
    public CompletableFuture<CefClient> previewClient() {
        CompletableFuture<CefClient> client = client();
        if (client.isCompletedExceptionally()) return client;
        CompletableFuture<CefClient> made;
        boolean first = false;
        synchronized (previewing) {
            if (preview == null) {
                preview = new CompletableFuture<>();
                first = true;
            }
            made = preview;
        }
        // Outside the lock: a client already made runs this at once, and the installers with it.
        if (first) client.whenComplete((ready, failure) -> onUiThread(() -> {
            if (failure != null) made.completeExceptionally(failure);
            else makePreview(made);
        }));
        return copy(made);
    }

    /**
     * Has {@code installer} set up the preview client before any preview is made on it, as {@link #onClient} does for
     * the pages' client: once, in the order given, as the client is made and before {@link #previewClient()}'s future
     * completes, or at once on a client already made. An installer that throws fails the preview client, not Chromium.
     *
     * @param installer what the preview client needs before any preview, such as its request and load handlers
     */
    public void onPreviewClient(Consumer<CefClient> installer) {
        previewInstallers.add(installer);
    }

    /**
     * Has {@code jasper-about:} answer only main-frame requests of the browsers {@code pageBrowser} takes for a page's
     * (plan 4b's P47); every other request, and any before this is given, is answered 404 and runs no action. Give it as
     * a page first asks for Chromium, with the client's installers; the last one given counts.
     *
     * @param pageBrowser whether a browser is a page's; asked on Chromium's IO thread, where it must return at once
     */
    public void answerAboutFor(Predicate<CefBrowser> pageBrowser) {
        pageBrowsers = Objects.requireNonNull(pageBrowser, "pageBrowser");
    }

    /** Whether {@code jasper-about:} answers {@code browser}'s main-frame requests, as {@link #answerAboutFor} says. Any thread. */
    boolean answersAbout(CefBrowser browser) {
        Predicate<CefBrowser> pageBrowser = pageBrowsers;
        return pageBrowser != null && pageBrowser.test(browser);
    }

    /**
     * Has {@code jasper-preview:} answer a browser of the preview client only on the origin {@code tokens} names for it
     * (plan 9's N6): a preview's page loads only its own documents and images. Before this is given, it answers no
     * browser. Give it as a preview first asks for its client, with the preview client's installers; the last one given
     * counts.
     *
     * @param tokens the token of the origin a browser's preview shows, or null for a browser that shows none; asked on
     *     Chromium's IO thread, where it must return at once
     */
    public void answerPreviewFor(Function<CefBrowser, String> tokens) {
        previewTokens = Objects.requireNonNull(tokens, "tokens");
    }

    /**
     * Whether {@code jasper-preview:} answers {@code browser}'s request for {@code url}: the browser is on the preview
     * client, and the address on its own preview's origin ({@link #answerPreviewFor}). Before that client is made, no
     * browser is. Any thread.
     */
    boolean answersPreview(CefBrowser browser, String url) {
        CefClient made = previewMade;
        return made != null && browser != null && browser.getClient() == made && onItsOrigin(previewTokens, browser, url);
    }

    /** Whether {@code url} is on the origin of the preview {@code browser} shows, as {@code tokens} names it; none for no tokens. */
    static boolean onItsOrigin(Function<CefBrowser, String> tokens, CefBrowser browser, String url) {
        String token = tokens == null ? null : tokens.apply(browser);
        return token != null && PreviewScheme.allows(url, token);
    }

    /**
     * Runs {@code task} on Chromium's own UI thread, which is not Swing's (on macOS it is AppKit's main thread), where CEF
     * makes request contexts and sets their preferences. Before Chromium's context has initialized, the task waits and
     * runs inside that initialization, in the order given; afterwards, it runs in the callback of the global cookie
     * manager's {@code flushStore}, which has no other effect. Asking starts nothing: before any page asks for Chromium,
     * the task waits for the start a page makes. Any thread.
     *
     * @param task what runs there; it must return quickly and must not wait for Swing's thread, as Chromium's close waits
     *     for it to end
     * @return completes once the task has run, or fails with what it threw; fails, and the task never runs, once Chromium
     *     has failed or begun to close (with the reason {@link #unavailable()} gives), and when Chromium refuses to call
     *     back
     */
    public CompletableFuture<Void> onChromiumThread(Runnable task) {
        return chromiumThread.run(task);
    }

    /**
     * A new request context in memory for a /dev/null group's pages: {@code CefRequestContext.createContext(null)}, made
     * on Chromium's thread ({@link #onChromiumThread}), so CEF initializes it at once, with CEF's defaults and an empty
     * cache path, which keeps its cookies, cache and storage in memory. Chromium keeps it for its group: it is never
     * handed to a browser itself ({@link #browserContext}), and it is disposed as {@link #release} says. Any thread.
     *
     * @return the context, on Chromium's thread; fails as {@link #onChromiumThread} fails, and when CEF makes none
     */
    public CompletableFuture<CefRequestContext> inMemoryContext() {
        var kept = new AtomicReference<CefRequestContext>();
        var made = new CompletableFuture<CefRequestContext>();
        // Completed once the work is over, not inside it: what follows the context runs outside the thread's work.
        onChromiumThread(() -> {
            Supplier<CefRequestContext> maker = contextMaker;
            kept.set(keep(maker == null ? ChromiumApp.inMemoryContext() : maker.get()));
        }).whenComplete((done, failure) -> {
            if (failure == null) made.complete(kept.get());
            else made.completeExceptionally(failure);
        });
        return made;
    }

    /**
     * A new context object for one browser over {@code kept}, an in-memory context {@link #inMemoryContext} made (plan
     * 6's V10 and V12). JCEF disposes a browser's context object as the browser closes, so a browser is never handed the
     * kept one. The object carries the kept context's native handle, which JCEF reads as the browser's queued creation
     * runs, and does what is asked of it on the kept context; it counts against the kept context until JCEF disposes it,
     * and the kept context outlives it. Give one to each browser made, and hand back ({@link #handBack}) one no browser
     * is made with. A context Chromium does not keep, a test's stand-in, is never disposed. Any thread; it touches
     * nothing native.
     *
     * @param kept the group's kept context, or null for Chromium's global one
     * @return a new object over it, or null for the global context
     * @throws IllegalStateException once the kept context has been disposed, as an object over it would carry no handle
     *     and so stand for Chromium's global context
     */
    public CefRequestContext browserContext(CefRequestContext kept) {
        if (kept == null) return null;
        KeptContext record;
        synchronized (contexts) {
            record = contexts.get(kept);
        }
        return record == null ? KeptContext.untracked(kept).handOut() : record.handOut();
    }

    /**
     * The context object for a page's DevTools browser, which JCEF makes with the page's own object
     * ({@code openDevTools}) and disposes as the DevTools close: for a page of an in-memory context a new object over the
     * same kept context, counted the same way ({@link #browserContext}), so no object is disposed twice; Chromium's
     * global context, a persistent page's, as it is. Any thread; it touches nothing native.
     *
     * @param pagesContext the page's own context object, or null for the global context
     * @return the DevTools' object
     */
    public static CefRequestContext anotherContext(CefRequestContext pagesContext) {
        // Typed as a context, so Chromium's own verification loads no Chromium class at the plugin's start.
        return BrowserContext.another(pagesContext);
    }

    /**
     * Keeps {@code context}, just made, for its group; one made as Chromium's dispose ran is disposed at once. Chromium's
     * thread.
     */
    private CefRequestContext keep(CefRequestContext context) {
        synchronized (contexts) {
            if (!contextsDisposed) {
                contexts.put(context, new KeptContext(context, Chromium::disposeKept, () -> ended.getCount() == 0));
                return context;
            }
        }
        ChromiumApp.dispose(context);
        throw new IllegalStateException(ONCE);
    }

    /** Disposes a kept context, which JCEF has done with. */
    private static void disposeKept(CefRequestContext context) {
        try {
            ChromiumApp.dispose(context);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not release an in-memory context", failure);
        }
    }

    /**
     * Releases {@code context}, an in-memory context {@link #inMemoryContext} made, as its group closes (V12): it is
     * disposed, once, however often released, as soon as every browser's object over it is back, at once when none is
     * out; whichever comes last disposes it, on its thread. After Chromium's close has swept the kept contexts, only a
     * browser's close disposes one natively; once Chromium has ended, nothing native is touched. Any thread.
     *
     * @param context the context, or null for none
     */
    public void release(CefRequestContext context) {
        if (context == null) return;
        KeptContext record;
        synchronized (contexts) {
            record = contexts.get(context);
        }
        if (record != null) record.release();
    }

    /**
     * The handler of a {@code jasper-about:} request of {@code browser} that Chromium's scheme handler factory does not
     * reach, as it serves only the global context: one of a page made with an in-memory context. It answers as that
     * factory does, from the same registry, under the same rule (plan 4b's P47): only a page's main-frame request is
     * answered, and any other 404. Chromium's IO thread, where it returns at once.
     *
     * @param browser the browser whose request it is
     * @param request the request
     * @return the handler; null before Chromium has started
     */
    public CefResourceHandler aboutResource(CefBrowser browser, CefRequest request) {
        ChromiumApp started = app;
        return started == null ? null : started.aboutResource(browser, request);
    }

    /**
     * Hands back {@code context}, an object {@link #browserContext} or {@link #anotherContext} gave for a browser that is
     * not made, or whose creation was never asked for: it counts off its kept context, as JCEF's dispose of a closed
     * browser's would, but once Chromium's close has swept the kept contexts it makes no native call, as CEF may be
     * shutting down. Nothing for Chromium's global context. Any thread.
     *
     * @param context the object, or null
     */
    public static void handBack(CefRequestContext context) {
        BrowserContext.handBack(context);
    }

    /** Has {@link #inMemoryContext} make its contexts with {@code made} instead of CEF: a test's stand-ins. */
    void makeContextsWith(Supplier<CefRequestContext> made) {
        contextMaker = Objects.requireNonNull(made, "made");
    }

    /** Chromium's context has initialized: the work waiting for its thread runs now. Chromium's thread. */
    void contextInitialized() {
        chromiumThread.contextInitialized();
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
     * Closes Chromium: its clients' every browser, then its app, once, however often this is called. On the UI thread
     * it returns at once, and a thread of its own waits for Chromium's end; anywhere else the caller waits. Either wait
     * lasts at most 10 seconds. A Chromium that never started closes at once and touches no Chromium class. Its run
     * marker goes at once, before Chromium has ended.
     */
    @Override public void close() {
        // Under the lock that hands work to Chromium's thread: none is handed over or runs once the close has begun.
        List<Step<CefClient>> steps = chromiumThread.closing(life::close);
        // Jasper closes Chromium: the marker goes now, as a quit's dispose can outlast Jasper's exit.
        marker.stopped();
        run(steps);
        if (!EventQueue.isDispatchThread()) awaitEnd();
    }

    /** Whether the client may still be made. UI thread. */
    boolean mayCreateClient() { return life.mayCreateClient(); }

    /** Runs the installers on the client just made, before it reaches any page; once. UI thread. */
    void install(CefClient made) {
        pageInstallers.install(made);
    }

    /** Runs the preview installers on the preview client just made, before it reaches any preview; once. UI thread. */
    void installPreview(CefClient made) {
        previewInstallers.install(made);
    }

    /**
     * Makes the preview client, which the client's completion asked for, unless Chromium has closed since: then it fails
     * as {@link #client()} does. UI thread, once.
     */
    private void makePreview(CompletableFuture<CefClient> made) {
        if (state() != State.RUNNING) {
            made.completeExceptionally(unavailable().orElseGet(() -> new IllegalStateException(ONCE)));
            return;
        }
        CefClient client;
        try {
            client = app.createPreviewClient();
            previewMade = client;
            installPreview(client);
        } catch (Throwable failure) { // As for the pages' client: an Error too must fail it, or nobody hears of it.
            LOG.log(System.Logger.Level.WARNING, "Chromium could not make the Markdown preview's client", failure);
            made.completeExceptionally(failure);
            return;
        }
        made.complete(client);
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
            started = ChromiumApp.start(dataDir, about, this::answersAbout, previews, this::answersPreview, this::terminated,
                this::contextInitialized);
        } catch (Throwable failure) { // JCEF reports a runtime without Chromium's libraries as an Error.
            threw(failure);
            return;
        }
        app = started;
        started.onInitialized(() -> {
            // From now on JCEF's cookie manager can hand work to Chromium's thread; it is got here, outside the work's lock.
            chromiumThread.handOverWith(started.handOver());
            run(life.initialized());
        });
    }

    /**
     * The worker begins the start: whether it may call JCEF, which it may only while the start is under way. When it may,
     * the data directory is made its owner's alone, then Chromium is marked, so a fatal error while it initializes leaves
     * the mark. A close that comes between the two leaves no mark: once closed, the marker is not written. Worker.
     */
    boolean enter() {
        if (!life.enter()) return false;
        restrictData.run();
        marker.started();
        return true;
    }

    /** The start threw before Chromium had an app. Worker. */
    void threw(Throwable failure) { run(life.threw(failure)); }

    /** Chromium has ended, or its dispose failed. Any thread. */
    void terminated() { run(life.terminated()); }

    /**
     * Carries out the life's steps, in order, outside its lock. Once Chromium cannot serve pages, the work waiting for its
     * thread fails first, with the reason.
     */
    private void run(List<Step<CefClient>> steps) {
        unavailable().ifPresent(chromiumThread::fail);
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

    /**
     * A future of the caller's own that completes as {@code shared} does, on the same thread: with its value, or failing
     * with the cause it failed with. Generic, so this class names no Chromium type here.
     */
    private static <C> CompletableFuture<C> copy(CompletableFuture<C> shared) {
        var own = new CompletableFuture<C>();
        shared.whenComplete((value, failure) -> {
            if (failure == null) own.complete(value);
            else own.completeExceptionally(failure);
        });
        return own;
    }

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

    /**
     * Sweeps the in-memory contexts still kept, then disposes the app, on the UI thread, when the life says so: once.
     * JCEF's dispose closes every browser, and disposes each browser's context object in its {@code onBeforeClose},
     * before the client's dispose completes and so before CEF shuts down ({@code CefApp.dispose} waits for its clients,
     * and {@code CefClient.onBeforeClose} calls the browser's {@code onBeforeClose} before it counts the browser off):
     * a swept context goes as the last browser over it closes.
     */
    private void dispose() {
        disposeContexts();
        try {
            app.dispose();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not stop", failure);
            terminated();
        }
    }

    /**
     * The dispose's sweep: every in-memory context still kept is disposed as soon as no browser's object holds it, at
     * once when none does, its group closed or not, and one made from now on is disposed as it is made. Swing's thread,
     * once Chromium has stopped.
     */
    void disposeContexts() {
        List<KeptContext> left;
        synchronized (contexts) {
            contextsDisposed = true;
            left = List.copyOf(contexts.values());
        }
        for (KeptContext context : left) context.sweep();
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

    /**
     * One client's installers: every installer given, those waiting for the client, and the client they ran on. Each
     * runs once, however often given, in the order given.
     */
    private static final class Installers {
        // Guarded by this.
        private final Set<Consumer<CefClient>> given = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<Consumer<CefClient>> waiting = new ArrayList<>();
        private boolean installed;
        private CefClient installedOn;

        /** Runs {@code installer} on the client once it is made, or now on the one made. Any thread. */
        void add(Consumer<CefClient> installer) {
            Objects.requireNonNull(installer, "installer");
            CefClient now;
            synchronized (this) {
                if (!given.add(installer)) return;
                if (!installed) {
                    waiting.add(installer);
                    return;
                }
                now = installedOn;
            }
            installer.accept(now);
        }

        /** Runs the waiting installers on the client just made; once. UI thread. */
        void install(CefClient made) {
            List<Consumer<CefClient>> each;
            synchronized (this) {
                if (installed) return;
                installed = true;
                installedOn = made;
                each = List.copyOf(waiting);
                waiting.clear();
            }
            for (Consumer<CefClient> installer : each) installer.accept(made);
        }
    }
}
