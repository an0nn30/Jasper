package dev.jasper.browser.item;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * The source pages being built (plan 4b's P47): at most so many at once, as each may come to some 41 million
 * characters ({@link SourcePage}). The rest wait their turn in the order they came, holding no thread, and one cancelled
 * while it waits is never built and leaves its turn, as when Chromium cancels the request. Any thread.
 */
final class SourceBuilds {
    private final int most;
    private final Executor executor;
    // Guarded by this: the builds under way and those waiting their turn.
    private int building;
    private final ArrayDeque<Build<?>> waiting = new ArrayDeque<>();

    /**
     * @param most how many pages are built at once
     * @param executor where they are built
     */
    SourceBuilds(int most, Executor executor) {
        if (most < 1) throw new IllegalArgumentException("At least one build at once: " + most);
        this.most = most;
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Builds a page with {@code work} once its turn comes.
     *
     * @param work the build
     * @param <T> what it builds
     * @return what it built, to come; it fails when {@code work} throws or the executor refuses it, and cancelling it
     *     before its turn means it is never built
     */
    <T> CompletableFuture<T> build(Supplier<T> work) {
        var build = new Build<>(Objects.requireNonNull(work, "work"));
        boolean now;
        synchronized (this) {
            now = building < most;
            if (now) building++;
            else waiting.add(build);
        }
        build.result.whenComplete((built, failure) -> {
            if (build.result.isCancelled()) synchronized (this) {
                waiting.remove(build);
            }
        });
        if (now) start(build);
        return build.result;
    }

    private void start(Build<?> build) {
        try {
            executor.execute(() -> {
                try {
                    build.run();
                } finally {
                    next();
                }
            });
        } catch (RejectedExecutionException refused) {
            build.result.completeExceptionally(refused);
            next();
        }
    }

    /** A build has ended: the next waiting one starts in its turn. */
    private void next() {
        Build<?> following;
        synchronized (this) {
            following = waiting.poll();
            if (following == null) building--;
        }
        if (following != null) start(following);
    }

    /** One build and its result. */
    private static final class Build<T> {
        final CompletableFuture<T> result = new CompletableFuture<>();
        private final Supplier<T> work;

        Build(Supplier<T> work) { this.work = work; }

        void run() {
            if (result.isDone()) return;
            try {
                result.complete(work.get());
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        }
    }
}
