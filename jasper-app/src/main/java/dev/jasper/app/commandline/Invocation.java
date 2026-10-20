package dev.jasper.app.commandline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One call of the {@code jasper} command as a dispatcher sees it. Safe from any thread: output is written in the order
 * it is given, and nothing is written once the call has finished or the caller has gone away.
 */
public final class Invocation {
    private static final System.Logger LOG = System.getLogger(Invocation.class.getName());
    private final List<String> arguments;
    private final Path workingDirectory;
    private final Optional<UUID> pane;
    private final Consumer<String> out;
    private final Consumer<String> err;
    private final List<Runnable> cancellation = new ArrayList<>();
    private boolean finished;
    private boolean cancelled;

    /**
     * @param arguments everything after {@code jasper}
     * @param workingDirectory the caller's working directory
     * @param pane the Jasper pane the caller ran in
     * @param out writes to the caller's standard output
     * @param err writes to the caller's standard error
     */
    public Invocation(List<String> arguments, Path workingDirectory, Optional<UUID> pane, Consumer<String> out,
                      Consumer<String> err) {
        this.arguments = List.copyOf(arguments);
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
        this.pane = Objects.requireNonNull(pane, "pane");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    public List<String> arguments() { return arguments; }

    public Path workingDirectory() { return workingDirectory; }

    public Optional<UUID> pane() { return pane; }

    /** Writes to the caller's standard output. */
    public void out(String text) { write(out, text); }

    /** Writes to the caller's standard error. */
    public void err(String text) { write(err, text); }

    private void write(Consumer<String> stream, String text) {
        Objects.requireNonNull(text, "text");
        synchronized (this) {
            if (finished || cancelled) return;
            stream.accept(text);
        }
    }

    /** Runs {@code listener} once when the caller goes away before the call finishes; at once when it already has. */
    public void onCancelled(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            if (!cancelled) {
                if (!finished) cancellation.add(listener);
                return;
            }
        }
        notify(listener);
    }

    public synchronized boolean cancelled() { return cancelled; }

    /** The caller went away: later output is dropped and the cancellation listeners run, once. */
    public void cancel() {
        List<Runnable> listeners;
        synchronized (this) {
            if (finished || cancelled) return;
            cancelled = true;
            listeners = List.copyOf(cancellation);
            cancellation.clear();
        }
        listeners.forEach(Invocation::notify);
    }

    /** The call completed: later output is dropped and {@link #cancel} does nothing. */
    public synchronized void finish() {
        finished = true;
        cancellation.clear();
    }

    private static void notify(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A cancellation listener of a jasper command call failed", failure);
        }
    }
}
