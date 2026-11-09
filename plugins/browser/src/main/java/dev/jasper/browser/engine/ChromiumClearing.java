package dev.jasper.browser.engine;

import dev.jasper.browser.engine.ProfileWipe.Mark;
import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.cef.CefClient;

/**
 * {@link Chromium}'s part of Clear Cookies and Clear Website Data, for the persistent store's profile, Chromium's global
 * context ({@link Chromium#clearCookies}, {@link Chromium#clearWebsiteData}): on Chromium's worker, before any start the
 * profile is wiped at once or marked for the start, and while Chromium starts or runs it is cleared live, the cookies
 * and the sites' storage ({@link ProfileClearing}); the start that finds a marker wipes the profile, or deletes the
 * cookies before its client is made ({@link ProfileWipe}). It logs as Chromium. Making one starts nothing.
 */
final class ChromiumClearing {
    static final String SLOW_CLEAR = "Chromium did not clear its data within 60 seconds";
    // F16: the bound of a whole clearing, the wait for a start under way included.
    private static final Duration CLEAR_LIMIT = Duration.ofSeconds(60);
    // Chromium's own logger: the clearing's lines are Chromium's.
    private static final System.Logger LOG = System.getLogger(Chromium.class.getName());

    private final Path dataDir;
    private final Runnable restrictData;
    private final Executor worker;
    private final ChromiumLife<CefClient> life;
    private final BooleanSupplier running;
    private final Supplier<ChromiumApp> app;
    // Set by the worker as the start enters: cookies Clear Cookies marked wait to be deleted before the client is made.
    private volatile boolean cookiesPending;
    // A test's bound of a clearing, and its stand-ins for the live clearing; null for Chromium's own.
    private volatile Duration clearLimit = CLEAR_LIMIT;
    private volatile Supplier<CompletableFuture<Set<String>>> liveCookies;
    private volatile Function<List<String>, CompletableFuture<Void>> liveStorage;
    // How the start's wipe moves and deletes: the file system's own, or a test's that refuses.
    private volatile ProfileWipe.Disk wipeDisk = ProfileWipe.Disk.REAL;
    // Told, on the UI thread, why cookies a Clear Cookies left for the start could not be deleted.
    private volatile Consumer<Throwable> clearFailed = cause -> { };

    /**
     * The clearing of one Chromium.
     *
     * @param dataDir the plugin's data directory, which keeps Chromium's profile and the clearing's marker
     * @param restrictData makes the data directory its owner's alone (H37); run on the worker before the marker
     * @param worker Chromium's worker, which alone enters the start
     * @param life Chromium's life, which says how a clearing goes now
     * @param running whether Chromium runs, asked on the UI thread before each native step, and on Chromium's thread as
     *     a clearing's helper browser is made
     * @param app Chromium's app, which the live clearing calls once Chromium runs
     */
    ChromiumClearing(Path dataDir, Runnable restrictData, Executor worker, ChromiumLife<CefClient> life, BooleanSupplier running,
                     Supplier<ChromiumApp> app) {
        this.dataDir = Objects.requireNonNull(dataDir, "dataDir");
        this.restrictData = Objects.requireNonNull(restrictData, "restrictData");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.life = Objects.requireNonNull(life, "life");
        this.running = Objects.requireNonNull(running, "running");
        this.app = Objects.requireNonNull(app, "app");
    }

    /** Clear Cookies, as {@link Chromium#clearCookies} says. Any thread. */
    CompletableFuture<Void> clearCookies() {
        return clear(Mark.COOKIES, Set.of(), false);
    }

    /** Clear Website Data's part in Chromium, as {@link Chromium#clearWebsiteData} says. Any thread. */
    CompletableFuture<Void> clearWebsiteData(Set<String> origins, boolean database) {
        return clear(Mark.ALL, Set.copyOf(origins), database);
    }

    /** Has {@code told} hear, as {@link Chromium#onClearFailed} says. */
    void onClearFailed(Consumer<Throwable> told) {
        clearFailed = Objects.requireNonNull(told, "told");
    }

    /** Has the start's wipe move and delete through {@code disk}: a test's that refuses. */
    void wipesWith(ProfileWipe.Disk disk) {
        wipeDisk = Objects.requireNonNull(disk, "disk");
    }

    /**
     * The start enters JCEF: a profile Clear Website Data marked is wiped, and cookies Clear Cookies marked wait for
     * {@link #beforeClient}. Worker, after the data directory is made its owner's alone.
     */
    void entered() {
        // Review round 1's item 5: a profile whose wipe could not move it still has its cookies deleted.
        cookiesPending = ProfileWipe.runIfMarked(dataDir, wipeDisk) != Mark.NONE;
    }

    /** A clearing of {@code mark}, on the worker, bounded as F16 has it; the caller gets a copy of its future. */
    private CompletableFuture<Void> clear(Mark mark, Set<String> origins, boolean database) {
        var done = new CompletableFuture<Void>();
        CompletableFuture.delayedExecutor(clearLimit.toMillis(), TimeUnit.MILLISECONDS)
            .execute(() -> done.completeExceptionally(new TimeoutException(SLOW_CLEAR)));
        try {
            worker.execute(() -> clearOnWorker(mark, origins, database, done));
        } catch (RejectedExecutionException refused) {
            done.completeExceptionally(refused);
        }
        return Chromium.copy(done);
    }

    /**
     * The clearing's work on the worker, which alone enters the start: a start not entered yet enters only after it.
     * Worker.
     */
    private void clearOnWorker(Mark mark, Set<String> origins, boolean database, CompletableFuture<Void> done) {
        if (done.isDone()) return;
        try {
            restrictData.run();
            ProfileWipe.mark(dataDir, mark, database);
            switch (life.clearing()) {
                case BEFORE_START -> {
                    if (mark == Mark.ALL && ProfileWipe.runIfMarked(dataDir, wipeDisk) == Mark.ALL)
                        throw new IOException("Chromium's profile is in use and could not be cleared: " + dataDir.resolve(ProfileWipe.PROFILE));
                    done.complete(null);
                }
                case MARK_ONLY -> done.complete(null);
                case LIVE -> {
                    CompletableFuture<CefClient> client = life.current();
                    if (client == null) done.complete(null);
                    // F16: a start that fails leaves the marker, which suffices.
                    else whenSettled(client, failure -> {
                        if (failure != null) done.complete(null);
                        else Chromium.onUiThread(() -> clearRunning(mark, origins, done));
                    });
                }
            }
        } catch (IOException | RuntimeException failure) {
            done.completeExceptionally(failure);
        }
    }

    /**
     * The live clearing, once the client is made: the cookies, then for Clear Website Data the sites' storage. Each
     * native step runs on the UI thread while Chromium runs; once it has closed, the marker waits for the next start and
     * nothing native is touched (Task 2's A). UI thread.
     */
    private void clearRunning(Mark mark, Set<String> origins, CompletableFuture<Void> done) {
        if (done.isDone()) return;
        if (!running.getAsBoolean()) {
            done.complete(null);
            return;
        }
        live(this::liveCookies).whenComplete((domains, failure) -> {
            if (failure != null) {
                done.completeExceptionally(failure);
                return;
            }
            if (mark == Mark.COOKIES) {
                takeBackCookies(() -> done.complete(null));
                return;
            }
            var all = new LinkedHashSet<>(origins);
            all.addAll(ProfileClearing.cookieOrigins(domains));
            Chromium.onUiThread(() -> {
                if (done.isDone()) return;
                if (!running.getAsBoolean()) {
                    done.complete(null);
                    return;
                }
                CompletableFuture<Void> cleared = live(() -> liveStorage(List.copyOf(all)));
                // Given up, as past the bound: no further call.
                done.whenComplete((over, late) -> cleared.cancel(false));
                cleared.whenComplete((over, late) -> {
                    if (late == null) done.complete(null);
                    else done.completeExceptionally(late);
                });
            });
        });
    }

    /** The cookies are gone: their marker goes, on the worker, then {@code then} runs; a wipe marked stays. */
    private void takeBackCookies(Runnable then) {
        try {
            worker.execute(() -> {
                try {
                    ProfileWipe.cookiesCleared(dataDir);
                } catch (IOException | RuntimeException kept) {
                    LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be deleted", kept);
                }
                then.run();
            });
        } catch (RejectedExecutionException stopped) {
            // The plugin has stopped: the marker stays, and the next start deletes the cookies again.
            then.run();
        }
    }

    /** What a live step gives, or a failed future when it throws. */
    private static <T> CompletableFuture<T> live(Supplier<CompletableFuture<T>> step) {
        try {
            return Objects.requireNonNull(step.get(), "the live clearing's future");
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * What the client waits for as Chromium initializes: the cookies a Clear Cookies before this start marked, or those
     * of a profile whose wipe could not move it, are deleted first, so before any page, and a {@code cookies} mark then
     * goes, on the worker. Cookies that cannot be deleted are logged and told ({@link #onClearFailed}), the profile is
     * marked for the next start's wipe, and Chromium goes on. Chromium's thread.
     *
     * @param cookies deletes every cookie, answering their domains
     * @return completes once nothing waits; never fails
     */
    CompletableFuture<Void> beforeClient(Supplier<CompletableFuture<Set<String>>> cookies) {
        if (!cookiesPending) return CompletableFuture.completedFuture(null);
        var open = new CompletableFuture<Void>();
        live(cookies).whenComplete((domains, failure) -> {
            if (failure == null) takeBackCookies(() -> { });
            else cookiesStayed(failure);
            open.complete(null);
        });
        return open;
    }

    /**
     * Review round 1's item 8: the cookies the start was to delete stayed, so they are not left silently: the next start
     * wipes the profile, marked on the worker, and the failure is told.
     */
    private void cookiesStayed(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        LOG.log(System.Logger.Level.WARNING, "Chromium could not delete the cookies Clear Cookies left; the next start wipes its profile", cause);
        try {
            worker.execute(() -> {
                try {
                    ProfileWipe.mark(dataDir, Mark.ALL);
                } catch (IOException | RuntimeException unmarked) {
                    LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be written", unmarked);
                }
            });
        } catch (RejectedExecutionException stopped) {
            // The plugin has stopped: the cookies' marker stays for the next start.
        }
        Consumer<Throwable> told = clearFailed;
        EventQueue.invokeLater(() -> told.accept(cause));
    }

    /** The live deletion of the cookies: a test's stand-in, or Chromium's own. */
    private CompletableFuture<Set<String>> liveCookies() {
        Supplier<CompletableFuture<Set<String>>> standIn = liveCookies;
        return standIn != null ? standIn.get() : app.get().clearCookies();
    }

    /** The live clearing of the sites' storage: a test's stand-in, or Chromium's own. UI thread. */
    private CompletableFuture<Void> liveStorage(List<String> origins) {
        Function<List<String>, CompletableFuture<Void>> standIn = liveStorage;
        return standIn != null ? standIn.apply(origins) : app.get().clearStorage(origins, running);
    }

    /** Has a clearing's live part done by {@code cookies} and {@code storage} instead of JCEF: a test's stand-ins. */
    void clearLiveWith(Supplier<CompletableFuture<Set<String>>> cookies, Function<List<String>, CompletableFuture<Void>> storage) {
        liveCookies = Objects.requireNonNull(cookies, "cookies");
        liveStorage = Objects.requireNonNull(storage, "storage");
    }

    /** Bounds each clearing by {@code limit} instead of 60 seconds: a test's. */
    void clearsWithin(Duration limit) {
        clearLimit = Objects.requireNonNull(limit, "limit");
    }

    /** Tells {@code then} how {@code future} ended: null once it completed; generic, so this class names no Chromium type here. */
    private static <C> void whenSettled(CompletableFuture<C> future, Consumer<Throwable> then) {
        future.whenComplete((value, failure) -> then.accept(failure));
    }
}
