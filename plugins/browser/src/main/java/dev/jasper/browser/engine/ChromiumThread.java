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
 * given after. Plain Java: {@link Chromium} makes the JCEF calls. Any thread.
 */
final class ChromiumThread {
    private final Supplier<Optional<Throwable>> unavailable;
    // Guarded by this: the work waiting for the context, or for JCEF, to initialize, and the work handed to the thread
    // that has not run yet, each in the order given.
    private final List<Work> waiting = new ArrayList<>(), handed = new ArrayList<>();
    private boolean contextInitialized;
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
     * @param task the work
     * @return completes once it has run, or fails with what it threw; fails, and it never runs, once Chromium cannot
     *     serve pages, and when the thread refuses it
     */
    CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task");
        var work = new Work(task);
        Predicate<Runnable> now;
        synchronized (this) {
            // Asked under the lock that fail() takes after Chromium's state has changed, so no work waits past it.
            Optional<Throwable> refused = unavailable.get();
            if (refused.isPresent()) return CompletableFuture.failedFuture(refused.get());
            if (!contextInitialized || hand == null) {
                waiting.add(work);
                return work.done;
            }
            handed.add(work);
            now = hand;
        }
        handOver(now, work);
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
        List<Work> now;
        synchronized (this) {
            if (this.hand != null) return;
            this.hand = hand;
            if (!contextInitialized) return; // the context's initialization runs what waits
            now = List.copyOf(waiting);
            waiting.clear();
            handed.addAll(now);
        }
        for (Work work : now) handOver(hand, work);
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

    /** Hands {@code work} to Chromium's thread; work the thread refuses fails. */
    private void handOver(Predicate<Runnable> hand, Work work) {
        boolean taken;
        try {
            taken = hand.test(() -> execute(work));
        } catch (RuntimeException failure) {
            forget(work);
            work.done.completeExceptionally(failure);
            return;
        }
        if (!taken) {
            forget(work);
            work.done.completeExceptionally(new IllegalStateException("Chromium's thread did not take the work"));
        }
    }

    /** Runs {@code work}, on Chromium's thread, unless it has failed already or Chromium can no longer serve pages. */
    private void execute(Work work) {
        forget(work);
        if (work.done.isDone()) return;
        Optional<Throwable> refused = unavailable.get();
        if (refused.isPresent()) {
            work.done.completeExceptionally(refused.get());
            return;
        }
        try {
            work.task.run();
        } catch (Throwable failure) { // an Error too fails the work, or nobody hears of it
            work.done.completeExceptionally(failure);
            return;
        }
        work.done.complete(null);
    }

    private synchronized void forget(Work work) {
        handed.remove(work);
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
