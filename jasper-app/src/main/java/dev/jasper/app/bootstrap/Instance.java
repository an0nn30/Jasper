package dev.jasper.app.bootstrap;

import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.CommandListener;
import dev.jasper.app.commandline.Invocation;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.LaunchRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * The home's owner (workspace-tabs spec 5.1 and 5.3): its instance lock, and the handoff endpoint and the command socket
 * it binds right after taking it, before AWT and whatever its residency. Requests wait in {@link EarlyRequests} until
 * {@link #serve} names the application. A handoff that arrives before then is answered on receipt and held; once the
 * application is ready, it is answered only when the application's event thread has taken it, within {@link #TAKE}, and
 * an application that is quitting takes none, so the launch tries again and takes the lock once this process has gone.
 * A {@code jasper} call is answered once it has run. The lock stays while the process goes on: the operating system
 * releases it when the process ends, and only {@link #close} (tests, and a start that fails before AWT) releases it sooner.
 */
final class Instance implements AutoCloseable {
    /** How long a ready owner's event thread may take to accept a handoff before the launch hears PROTOCOL (spec 5.3). */
    static final Duration TAKE = Duration.ofSeconds(1);

    private final InstanceLock lock;
    private final EarlyRequests early = new EarlyRequests();
    private final HandoffSocket handoff;
    private final CommandListener commands;
    private volatile BiConsumer<LaunchRequest, Boolean> handoffs;
    private volatile BooleanSupplier accepting;
    private volatile Executor edt;
    private volatile CommandDispatcher dispatcher;

    private Instance(InstanceLock lock, AppDirs dirs, Path source, long modified) {
        this.lock = Objects.requireNonNull(lock, "lock");
        // Either may be null (logged by its bind): an owner whose endpoint cannot bind runs unreachable (plan Ruling 15).
        this.handoff = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(),
            HandoffSocket.answering(this::take, source, modified));
        this.commands = CommandListener.bind(dirs.commandSocket(), this::dispatch);
    }

    /**
     * Binds the owner's endpoints.
     *
     * @param lock the home's instance lock, which this owner now holds
     * @param dirs the home
     * @param source this build's code location, or null when unknown
     * @param modified its modification time
     * @return the owner
     */
    static Instance open(InstanceLock lock, AppDirs dirs, Path source, long modified) {
        return new Instance(lock, dirs, source, modified);
    }

    /** Whether this owner took a handoff. On the endpoint's accept thread, which waits here at most {@link #TAKE}. */
    private boolean take(LaunchRequest request, boolean updated) {
        var taken = new CompletableFuture<Boolean>();
        return switch (early.submit(() -> edt.execute(() -> handoffs.accept(request, updated)),
                () -> handOver(request, updated, taken))) {
            case HELD -> true;
            case DECLINED -> false;
            case RAN -> awaitTaken(taken);
        };
    }

    /** Offers a request to the ready application's event thread, which takes it unless the application is quitting. */
    private void handOver(LaunchRequest request, boolean updated, CompletableFuture<Boolean> taken) {
        edt.execute(() -> {
            if (!accepting.getAsBoolean()) taken.complete(false);
            else if (taken.complete(true)) handoffs.accept(request, updated);
        });
    }

    /** True once the event thread took the request; false when it declined it or did not get to it in time. */
    private static boolean awaitTaken(CompletableFuture<Boolean> taken) {
        try {
            return taken.get(TAKE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            return withdraw(taken);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return withdraw(taken);
        } catch (ExecutionException impossible) {
            return false;
        }
    }

    /** Withdraws an offer: false, unless the event thread took it at this very moment. */
    private static boolean withdraw(CompletableFuture<Boolean> taken) {
        return !taken.complete(false) && taken.getNow(false);
    }

    private CompletionStage<Integer> dispatch(Invocation invocation) {
        var result = new CompletableFuture<Integer>();
        EarlyRequests.Outcome outcome = early.submit(() -> {
            CompletionStage<Integer> stage;
            try {
                stage = dispatcher.dispatch(invocation);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
            stage.whenComplete((code, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
                else result.complete(code);
            });
        });
        if (outcome == EarlyRequests.Outcome.DECLINED)
            result.completeExceptionally(new IllegalStateException("Jasper is quitting; run the command again"));
        return result;
    }

    /**
     * The application can take requests: those held since the lock was taken run in arrival order, later ones as they
     * come. Once.
     *
     * @param handoffs carries out a handoff, given whether it came from another build; called on {@code edt}
     * @param accepting whether the application still takes handoffs, false once it quits; called on {@code edt}
     * @param edt the application's event thread
     * @param commands runs the {@code jasper} command's calls
     */
    void serve(BiConsumer<LaunchRequest, Boolean> handoffs, BooleanSupplier accepting, Executor edt, CommandDispatcher commands) {
        this.handoffs = Objects.requireNonNull(handoffs, "handoffs");
        this.accepting = Objects.requireNonNull(accepting, "accepting");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.dispatcher = Objects.requireNonNull(commands, "commands");
        early.open();
    }

    /** Whether requests are still held: before {@link #serve}, and while the held ones are passed on. */
    boolean holding() { return early.holding(); }

    /**
     * Turns every later request away when none is held and {@link #serve} was never called: a {@code --background}
     * start that will not stay can then exit without dropping a request it already answered (plan Ruling 20).
     *
     * @return whether it did; false when a request is held or the application is served
     */
    boolean declineIfIdle() { return early.declineIfIdle(); }

    /** The command socket this owner serves, which its shells name; null when it could not bind. */
    Path commandSocket() { return commands == null ? null : commands.socket(); }

    /** The command listener, for a windowless launch that quits once its calls are served; null when unbound. */
    CommandListener commands() { return commands; }

    /** Closes both endpoints; the lock stays until the process ends. Any thread; idempotent. */
    void closeEndpoints() {
        if (handoff != null) handoff.close();
        if (commands != null) commands.close();
    }

    /**
     * Closes the endpoints and releases the lock: tests, and {@code start}'s rollback in a process that goes on. A start
     * that fails in this process closes only the endpoints and exits (plan Ruling 24). Idempotent.
     */
    @Override public void close() {
        try { closeEndpoints(); }
        finally { lock.close(); }
    }
}
