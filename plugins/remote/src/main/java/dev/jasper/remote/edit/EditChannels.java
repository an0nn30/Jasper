package dev.jasper.remote.edit;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs the editor's file operations for each connection identity one at a time, on one SFTP channel opened for
 * the first queued operation and closed when the queue empties: every channel counts against OpenSSH's 10
 * sessions per connection, beside the user's shells. UI thread only; operations run on the background executor.
 * A failed or cancelled operation discards its channel, and the next operation opens a fresh one.
 */
public final class EditChannels implements AutoCloseable {
    /**
     * One operation on an endpoint, run on the background executor.
     *
     * @param <T> what it produces
     */
    @FunctionalInterface public interface Work<T> { T run(FileEndpoint endpoint) throws Exception; }

    private final BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener;
    private final Predicate<ConnectionIdentity> authenticated;
    private final Executor background, ui;
    private final Map<ConnectionIdentity, Channel> channels = new HashMap<>();
    private boolean closed;

    /**
     * @param opener opens an endpoint, on the UI thread; the window, when present, owns sign-in prompts
     * @param authenticated whether an authenticated connection exists, so opening needs no prompt
     * @param background where operations run
     * @param ui the UI thread
     */
    public EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener,
                        Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui) {
        this.opener = Objects.requireNonNull(opener); this.authenticated = Objects.requireNonNull(authenticated);
        this.background = Objects.requireNonNull(background); this.ui = Objects.requireNonNull(ui);
    }

    /**
     * Queues {@code work} behind the identity's earlier operations. Cancelling the result withdraws queued work,
     * or aborts the channel under running work.
     *
     * @param owner the window for sign-in prompts, asked when a channel must be opened
     * @return the work's result, completed on the UI thread
     */
    public <T> CompletableFuture<T> submit(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, Work<T> work) {
        var job = new Job<T>(Objects.requireNonNull(owner), Objects.requireNonNull(work));
        if (closed) {
            job.result.completeExceptionally(new IOException("Remote has stopped"));
            return job.result;
        }
        Channel channel = channels.computeIfAbsent(identity, Channel::new);
        channel.queue.add(job);
        job.result.whenComplete((value, failure) -> { if (job.result.isCancelled()) ui.execute(() -> cancelled(channel, job)); });
        next(channel);
        return job.result;
    }

    /** @return whether {@code identity} has a channel open, opening or queued, for tests */
    boolean active(ConnectionIdentity identity) { return channels.containsKey(identity); }

    private void next(Channel channel) {
        if (closed || channel.running != null || channel.opening != null) return;
        while (!channel.queue.isEmpty() && channel.queue.peek().result.isDone()) channel.queue.poll();
        Job<?> job = channel.queue.peek();
        if (job == null) {
            channel.discard();
            channels.remove(channel.identity, channel);
            return;
        }
        if (channel.endpoint == null) {
            open(channel, job);
            return;
        }
        channel.queue.poll();
        run(channel, job);
    }

    private void open(Channel channel, Job<?> job) {
        Optional<WindowHandle> owner = job.owner.get();
        if (owner.isEmpty() && !authenticated.test(channel.identity)) {
            // Remote's sign-in prompts are cancelled without an owner window.
            channel.queue.poll();
            job.result.completeExceptionally(new IOException("Reconnecting to " + channel.identity.host().name()
                + " needs an open terminal window for the sign-in prompt."));
            next(channel);
            return;
        }
        CompletableFuture<FileEndpoint> opening;
        try { opening = opener.apply(channel.identity, owner); }
        catch (RuntimeException failure) { opening = CompletableFuture.failedFuture(failure); }
        channel.opening = opening;
        var requested = opening;
        opening.whenComplete((endpoint, failure) -> ui.execute(() -> opened(channel, requested, endpoint, failure)));
    }

    private void opened(Channel channel, CompletableFuture<FileEndpoint> requested, FileEndpoint endpoint, Throwable failure) {
        if (channel.opening != requested) {
            if (endpoint != null) endpoint.abort();
            return;
        }
        channel.opening = null;
        if (closed) {
            if (endpoint != null) endpoint.abort();
            return;
        }
        if (failure != null) {
            // Every queued operation needed this connection: fail them together rather than prompting once each.
            for (Job<?> waiting : List.copyOf(channel.queue)) waiting.result.completeExceptionally(failure);
            channel.queue.clear();
        } else {
            channel.endpoint = endpoint;
        }
        next(channel);
    }

    private <T> void run(Channel channel, Job<T> job) {
        channel.running = job;
        FileEndpoint endpoint = channel.endpoint;
        try {
            background.execute(() -> {
                T value = null;
                Throwable failure = null;
                // Anything thrown must reach finished(), or the queue would stop for good.
                try { value = job.work.run(endpoint); } catch (Throwable problem) { failure = problem; }
                T result = value;
                Throwable problem = failure;
                ui.execute(() -> finished(channel, job, result, problem));
            });
        } catch (RuntimeException rejected) {
            finished(channel, job, null, rejected);
        }
    }

    private <T> void finished(Channel channel, Job<T> job, T value, Throwable failure) {
        if (channel.running == job) channel.running = null;
        if (failure != null) channel.discard();
        // Bookkeeping first, so a caller woken by the result finds the queue settled.
        next(channel);
        if (failure == null) job.result.complete(value);
        else job.result.completeExceptionally(closed ? new IOException("Remote has stopped", failure) : failure);
    }

    private void cancelled(Channel channel, Job<?> job) {
        if (channel.running == job) {
            channel.discard();
            return;
        }
        if (channel.opening != null && channel.queue.stream().allMatch(waiting -> waiting.result.isDone())) {
            var opening = channel.opening;
            channel.opening = null;
            opening.cancel(true);
            next(channel);
        }
    }

    /** Fails queued work, cancels opening channels and aborts open ones; later submissions fail. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        for (Channel channel : List.copyOf(channels.values())) {
            for (Job<?> waiting : channel.queue) waiting.result.completeExceptionally(new IOException("Remote has stopped"));
            channel.queue.clear();
            if (channel.opening != null) channel.opening.cancel(true);
            channel.opening = null;
            channel.discard();
        }
        channels.clear();
    }

    private static final class Channel {
        final ConnectionIdentity identity;
        final ArrayDeque<Job<?>> queue = new ArrayDeque<>();
        FileEndpoint endpoint;
        CompletableFuture<FileEndpoint> opening;
        Job<?> running;

        Channel(ConnectionIdentity identity) { this.identity = identity; }

        void discard() {
            var owned = endpoint;
            endpoint = null;
            if (owned != null) owned.abort();
        }
    }

    private static final class Job<T> {
        final Supplier<Optional<WindowHandle>> owner;
        final Work<T> work;
        final CompletableFuture<T> result = new CompletableFuture<>();

        Job(Supplier<Optional<WindowHandle>> owner, Work<T> work) { this.owner = owner; this.work = work; }
    }
}
