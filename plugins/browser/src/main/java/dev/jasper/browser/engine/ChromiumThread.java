package dev.jasper.browser.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Work for Chromium's own UI thread, which is never Swing's (on macOS it is AppKit's main thread): CEF makes request
 * contexts and sets their preferences there only. Work given before Chromium's context has initialized waits, and runs
 * inside that initialization, on that thread, in the order given. Work given later is handed to the thread through the
 * hand-over Chromium gives once JCEF has initialized too; until then it waits as well. Every piece of work fails, rather
 * than runs, once Chromium cannot serve pages: work waiting or handed over then fails with the reason, and so does work
 * given after. Handing work over and running it happen under the one lock that Chromium's close takes as it begins
 * ({@link #closing}), so neither happens, from any thread, once the close has begun. Plain Java: {@link Chromium} makes
 * the JCEF calls. Any thread.
 */
final class ChromiumThread {
    private final Supplier<Optional<Throwable>> unavailable;
    // Guarded by this: the work waiting for the context, or for JCEF, to initialize, and the work handed to the thread
    // that has not run yet, each in the order given.
    private final List<Work> waiting = new ArrayList<>(), handed = new ArrayList<>();
    private boolean contextInitialized, closed;
    // Set once JCEF has initialized: hands a task to Chromium's thread, answering whether the thread took it.
    private Predicate<Runnable> hand;

    /**
     * Work for Chromium's thread that fails once {@code unavailable} gives a reason.
     *
     * @param unavailable why Chromium cannot serve pages, or empty while it can
     */
    ChromiumThread(Supplier<Optional<Throwable>> unavailable) {
        this.unavailable = Objects.requireNonNull(unavailable, "unavailable");
    }

    /**
     * Runs {@code task} on Chromium's thread, as the class says. Any thread.
     *
     * @param task the work; it must return quickly and must not wait for Swing's thread
     * @return completes once it has run, or fails with what it threw; fails, and it never runs, once Chromium cannot
     *     serve pages or has begun to close, and when the thread refuses it
     */
    CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task");
        var work = new Work(task);
        Throwable refused;
        synchronized (this) {
            // Asked under the lock that fail() and closing() take, so no work waits past them or is handed over after.
            refused = refusal();
            if (refused == null) {
                if (!contextInitialized || hand == null) {
                    waiting.add(work);
                    return work.done;
                }
                refused = handOver(hand, work);
            }
        }
        if (refused != null) work.done.completeExceptionally(refused);
        return work.done;
    }

    /** Chromium's context has initialized: the work waiting runs now, on Chromium's thread, which this is. Once. */
    void contextInitialized() {
        List<Work> now;
        synchronized (this) {
            if (contextInitialized) return;
            contextInitialized = true;
            now = List.copyOf(waiting);
            waiting.clear();
        }
        now.forEach(this::execute);
    }

    /**
     * JCEF has initialized: work from now on is handed to Chromium's thread with {@code hand}, which answers whether the
     * thread took it, once the context has initialized too; work that waits for that until now is handed over then. Once.
     *
     * @param hand hands a task to Chromium's thread
     */
    void handOverWith(Predicate<Runnable> hand) {
        Objects.requireNonNull(hand, "hand");
        var refused = new ArrayList<Work>();
        var reasons = new ArrayList<Throwable>();
        synchronized (this) {
            if (this.hand != null) return;
            this.hand = hand;
            if (!contextInitialized || refusal() != null) return; // the context's initialization, or fail(), takes what waits
            List<Work> now = List.copyOf(waiting);
            waiting.clear();
            for (Work work : now) {
                Throwable reason = handOver(hand, work);
                if (reason != null) {
                    refused.add(work);
                    reasons.add(reason);
                }
            }
        }
        for (int each = 0; each < refused.size(); each++) refused.get(each).done.completeExceptionally(reasons.get(each));
    }

    /**
     * Chromium begins to close: {@code closing} runs under the lock that hands work over and runs it, so none is handed
     * over or runs from now on, and work already under way has ended first. The work left fails as {@link #fail} is then
     * told.
     *
     * @param closing the close's own first step, which must not wait for Chromium's thread
     * @param <T> what it gives
     * @return what it gave
     */
    synchronized <T> T closing(Supplier<T> closing) {
        closed = true;
        return closing.get();
    }

    /**
     * Chromium cannot serve pages: the work waiting and the work handed over and not yet run fail with {@code cause}.
     *
     * @param cause why
     */
    void fail(Throwable cause) {
        List<Work> failing;
        synchronized (this) {
            failing = new ArrayList<>(waiting);
            failing.addAll(handed);
            waiting.clear();
            handed.clear();
        }
        for (Work work : failing) work.done.completeExceptionally(cause);
    }

    /**
     * Hands {@code work} to Chromium's thread, under this lock: why the thread refused it, or null once it took it, and
     * the work then counts as handed over.
     */
    private Throwable handOver(Predicate<Runnable> hand, Work work) {
        boolean taken;
        try {
            taken = hand.test(() -> execute(work));
        } catch (RuntimeException failure) {
            return failure;
        }
        if (!taken) return new IllegalStateException("Chromium's thread did not take the work");
        handed.add(work);
        return null;
    }

    /**
     * Runs {@code work}, on Chromium's thread, under this lock, unless it has failed already or Chromium can no longer
     * serve pages or has begun to close; its future completes after, outside the lock.
     */
    private void execute(Work work) {
        Throwable failed;
        synchronized (this) {
            handed.remove(work);
            if (work.done.isDone()) return;
            failed = refusal();
            if (failed == null) {
                try {
                    work.task.run();
                } catch (Throwable thrown) { // an Error too fails the work, or nobody hears of it
                    failed = thrown;
                }
            }
        }
        if (failed == null) work.done.complete(null);
        else work.done.completeExceptionally(failed);
    }

    /** Why no work may be handed over or run now; null while it may. Under this lock. */
    private Throwable refusal() {
        Optional<Throwable> refused = unavailable.get();
        if (refused.isPresent()) return refused.get();
        return closed ? new IllegalStateException(Chromium.ONCE) : null;
    }

    /** One piece of work and its future. */
    private static final class Work {
        final Runnable task;
        final CompletableFuture<Void> done = new CompletableFuture<>();

        Work(Runnable task) {
            this.task = task;
        }
    }
}
