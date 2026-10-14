package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.cli.CommandCall;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A subcommand call {@link FakePluginHost#call} made: what the handler wrote, its exit code, and cancelling it as when the
 * caller goes away.
 *
 * @since 0.17.0
 */
public final class FakeCommandCall {
    private final List<String> arguments;
    private final Path workingDirectory;
    private final StringBuilder out = new StringBuilder();
    private final StringBuilder err = new StringBuilder();
    private final List<Runnable> cancellation = new ArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private Optional<WindowOwner> window = Optional.empty();
    private boolean cancelled;

    FakeCommandCall(List<String> arguments, Path workingDirectory) {
        this.arguments = List.copyOf(arguments);
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
    }

    /**
     * The exit code, once the call completes.
     *
     * @return completes with the exit code
     */
    public CompletableFuture<Integer> exit() { return exit; }

    /**
     * The standard output so far.
     *
     * @return what the handler wrote to standard output
     */
    public synchronized String out() { return out.toString(); }

    /**
     * The standard error so far.
     *
     * @return what the handler and the host wrote to standard error
     */
    public synchronized String err() { return err.toString(); }

    /**
     * Whether the caller went away.
     *
     * @return whether {@link #cancel} ran before the call completed
     */
    public synchronized boolean cancelled() { return cancelled; }

    /** As if the caller went away: the handler's cancellation listeners run, and later output is dropped. */
    public void cancel() {
        List<Runnable> listeners;
        synchronized (this) {
            if (cancelled || exit.isDone()) return;
            cancelled = true;
            listeners = List.copyOf(cancellation);
            cancellation.clear();
        }
        listeners.forEach(Runnable::run);
    }

    void window(Optional<WindowOwner> value) { window = value; }

    void hostError(String text) { write(err, text); }

    void complete(int code) { exit.complete(code); }

    /** Its plugin stopped before it completed: exit code 1; {@link #cancel} then does nothing, so its listeners never run. */
    void stopped(String name) {
        hostError("jasper: " + name + " stopped\n");
        complete(1);
    }

    void fail(String name, Throwable failure) {
        hostError("jasper: " + name + " failed: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()) + "\n");
        complete(1);
    }

    private synchronized void write(StringBuilder stream, String text) {
        Objects.requireNonNull(text, "text");
        if (!cancelled && !exit.isDone()) stream.append(text);
    }

    /** What the handler gets. */
    CommandCall view() {
        return new CommandCall() {
            @Override public List<String> arguments() { return arguments; }
            @Override public Path workingDirectory() { return workingDirectory; }
            @Override public Optional<WindowOwner> window() { return window; }
            @Override public void out(String text) { write(out, text); }
            @Override public void err(String text) { write(err, text); }
            @Override public Subscription onCancelled(Runnable listener) {
                Objects.requireNonNull(listener, "listener");
                var active = new AtomicBoolean(true);
                Runnable guarded = () -> { if (active.get()) listener.run(); };
                boolean now;
                synchronized (FakeCommandCall.this) {
                    now = cancelled;
                    if (!now) cancellation.add(guarded);
                }
                if (now) guarded.run();
                return () -> active.set(false);
            }
        };
    }
}
