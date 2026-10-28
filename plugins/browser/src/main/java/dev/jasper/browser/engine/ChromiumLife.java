package dev.jasper.browser.engine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The decisions of Chromium's one life, made under one lock and free of JCEF. Each transition answers with the steps
 * {@link Chromium} then takes outside the lock. Exactly one transition answers {@link Step.Dispose}, and only when the
 * start reached JCEF and Chromium initialized: the close when initialization came first, or the initialization when
 * the close came first.
 *
 * @param <C> the client's type
 */
final class ChromiumLife<C> {
    private final StartOnce process;
    private final Object lock = new Object();
    // Guarded by lock. entered: the start reached JCEF; threw: it failed before Chromium had an app.
    private Chromium.State state = Chromium.State.NOT_STARTED;
    private CompletableFuture<C> client;
    private IllegalStateException refusal;
    private boolean entered, threw, initialized, ended, told;

    ChromiumLife(StartOnce process) { this.process = process; }

    /** What Chromium takes on from a transition, in order. */
    sealed interface Step<C> {
        /** Hand the start to the worker and arm the start's time limit. */
        record Start<C>() implements Step<C> { }
        /** Make the client on the UI thread. */
        record CreateClient<C>() implements Step<C> { }
        /** Hand the made client to its future. */
        record Complete<C>(CompletableFuture<C> client, C value) implements Step<C> { }
        /** Fail a future that waits for the client. */
        record Fail<C>(CompletableFuture<C> client, Throwable cause) implements Step<C> { }
        /** Tell the plugin why Chromium cannot serve pages; a life tells once. */
        record Tell<C>(Throwable cause) implements Step<C> { }
        /** Dispose Chromium's app on the UI thread. */
        record Dispose<C>() implements Step<C> { }
        /** Chromium has ended, or nothing ever reached JCEF: waiters stop waiting. */
        record End<C>() implements Step<C> { }
        /** The first close of a Chromium still ending: on the UI thread, wait for its end on a thread of its own. */
        record Await<C>() implements Step<C> { }
    }

    /**
     * {@link Chromium#client()}'s answer.
     *
     * @param client the future the caller gets
     * @param steps what Chromium takes on first
     */
    record Asked<C>(CompletableFuture<C> client, List<Step<C>> steps) { }

    /** A page asks for the client: the first ask starts Chromium, unless it is closed or another one started in this process. */
    Asked<C> ask() {
        synchronized (lock) {
            if (state == Chromium.State.STOPPED) return new Asked<>(client, tell(refusal()));
            if (client != null) return new Asked<>(client, List.of());
            if (!process.claim()) {
                state = Chromium.State.FAILED;
                client = CompletableFuture.failedFuture(refusal());
                return new Asked<>(client, tell(refusal()));
            }
            state = Chromium.State.STARTING;
            client = new CompletableFuture<>();
            return new Asked<>(client, List.of(new Step.Start<>()));
        }
    }

    /** The worker begins the start: whether it may call JCEF, which it may only while the start is under way. */
    boolean enter() {
        synchronized (lock) {
            if (state != Chromium.State.STARTING) return false;
            entered = true;
            return true;
        }
    }

    /** The start threw before Chromium had an app: it fails, or, after a close, nothing is left to end. */
    List<Step<C>> threw(Throwable cause) {
        synchronized (lock) {
            threw = true;
            return state == Chromium.State.STOPPED ? end() : fail(cause);
        }
    }

    /** Chromium initialized: the client is made, or, after a close, the app disposed. After a timeout, nothing. */
    List<Step<C>> initialized() {
        synchronized (lock) {
            initialized = true;
            if (state == Chromium.State.STOPPED) return List.of(new Step.Dispose<>());
            if (state == Chromium.State.STARTING) return List.of(new Step.CreateClient<>());
            return List.of();
        }
    }

    /** Whether the client may still be made: the start is under way. */
    boolean mayCreateClient() {
        synchronized (lock) { return state == Chromium.State.STARTING; }
    }

    /** The client is made: Chromium runs. After a close or a failure the app's dispose takes the client with it. */
    List<Step<C>> clientCreated(C value) {
        synchronized (lock) {
            if (state != Chromium.State.STARTING) return List.of();
            state = Chromium.State.RUNNING;
            return List.of(new Step.Complete<>(client, value));
        }
    }

    /** The start failed: the worker refused it, or the client could not be made. Nothing once the start has ended. */
    List<Step<C>> failed(Throwable cause) {
        synchronized (lock) { return fail(cause); }
    }

    /** The start's time limit passed: it fails unless Chromium has initialized. */
    List<Step<C>> timedOut(Throwable cause) {
        synchronized (lock) { return initialized ? List.of() : fail(cause); }
    }

    /**
     * Closes this life, once; a later close answers nothing. A start that never reached JCEF, or threw before Chromium
     * had an app, ends at once. An initialized Chromium is disposed now; one still starting, when it initializes.
     */
    List<Step<C>> close() {
        synchronized (lock) {
            if (state == Chromium.State.STOPPED) return List.of();
            state = Chromium.State.STOPPED;
            List<Step<C>> steps = new java.util.ArrayList<>();
            if (client != null && !client.isDone()) steps.add(new Step.Fail<>(client, refusal()));
            client = CompletableFuture.failedFuture(refusal());
            if (!entered || threw) steps.addAll(end());
            else if (initialized) steps.add(new Step.Dispose<>());
            if (!ended) steps.add(new Step.Await<>());
            return steps;
        }
    }

    /** Chromium has ended, or its dispose failed. */
    List<Step<C>> terminated() {
        synchronized (lock) { return end(); }
    }

    /** Where the life stands. */
    Chromium.State state() {
        synchronized (lock) { return state; }
    }

    private List<Step<C>> end() {
        ended = true;
        return List.of(new Step.End<>());
    }

    private List<Step<C>> fail(Throwable cause) {
        if (state != Chromium.State.STARTING) return List.of();
        state = Chromium.State.FAILED;
        Step<C> failing = new Step.Fail<>(client, cause);
        List<Step<C>> tell = tell(cause);
        return tell.isEmpty() ? List.of(failing) : List.of(failing, tell.getFirst());
    }

    private List<Step<C>> tell(Throwable cause) {
        if (told) return List.of();
        told = true;
        return List.of(new Step.Tell<>(cause));
    }

    /** The one refusal this life answers with once it is closed or cannot start. */
    private IllegalStateException refusal() {
        if (refusal == null) refusal = new IllegalStateException(Chromium.ONCE);
        return refusal;
    }
}
