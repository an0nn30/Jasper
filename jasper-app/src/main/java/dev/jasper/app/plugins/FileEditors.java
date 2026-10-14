package dev.jasper.app.plugins;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.FileEditor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editor a plugin provides ({@code Platform.editor}), which Jasper's own opens ask before the desktop. One at a
 * time: a bundled plugin's registration replaces a non-bundled one's; any other later registration, the same plugin's
 * included, throws. The editor runs on the UI thread inside its plugin's containment; a worker asks it through
 * {@link #editFromWorker} and waits a bounded time.
 */
final class FileEditors {
    private static final System.Logger LOG = System.getLogger(FileEditors.class.getName());
    /** How long a worker waits for the UI thread's answer before the desktop opens the file. */
    static final Duration WAIT = Duration.ofSeconds(30);

    /** A registration: the plugin, its editor, and its own handle for an open terminal window's id. */
    private record Holder(String pluginId, FileEditor editor, Function<UUID, Optional<WindowOwner>> windows) { }

    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final Containment containment;
    private final Duration wait;
    private volatile Set<String> bundled = Set.of();
    private volatile Holder holder;

    FileEditors(Consumer<Runnable> ui, BooleanSupplier onUi, Containment containment) { this(ui, onUi, containment, WAIT); }

    /** As above, waiting {@code wait} for the UI thread instead of 30 seconds; for tests. */
    FileEditors(Consumer<Runnable> ui, BooleanSupplier onUi, Containment containment, Duration wait) {
        this.ui = Objects.requireNonNull(ui, "ui");
        this.onUi = Objects.requireNonNull(onUi, "onUi");
        this.containment = Objects.requireNonNull(containment, "containment");
        this.wait = Objects.requireNonNull(wait, "wait");
    }

    /** The plugins that ship with Jasper; set before any plugin starts. */
    void bundled(Set<String> pluginIds) { bundled = Set.copyOf(pluginIds); }

    /**
     * Makes {@code pluginId}'s editor the one Jasper opens files with. UI thread.
     *
     * @param windows the registering plugin's own handle for an open terminal window's id
     * @return removes the editor, unless a bundled plugin's has replaced it since
     * @throws IllegalStateException when a plugin provides the editor already, unless this is a bundled plugin replacing
     *     a non-bundled one's
     */
    dev.jasper.app.lifecycle.Subscription register(String pluginId, FileEditor editor, Function<UUID, Optional<WindowOwner>> windows) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(windows, "windows");
        Holder current = holder;
        if (current != null) {
            if (!bundled.contains(pluginId) || bundled.contains(current.pluginId()))
                throw new IllegalStateException("The editor is already provided by " + current.pluginId());
            LOG.log(System.Logger.Level.INFO, "The bundled plugin " + pluginId + " provides the editor instead of " + current.pluginId());
        }
        var registered = new Holder(pluginId, editor, windows);
        holder = registered;
        return new dev.jasper.app.lifecycle.Subscription(() -> { if (holder == registered) holder = null; });
    }

    /**
     * Asks the provided editor to open {@code file}. UI thread.
     *
     * @param windowId the terminal window the open came from; the editor gets its plugin's handle while it is open
     * @return true when it opened the file; false when no plugin provides an editor, or it declined or threw
     */
    boolean edit(Path file, OptionalInt line, Optional<UUID> windowId) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(windowId, "windowId");
        Holder current = holder;
        if (current == null) return false;
        Optional<WindowOwner> window = windowId.flatMap(current.windows());
        var opened = new AtomicBoolean();
        Throwable failure = containment.attempt(current.pluginId(), "file editor", () -> {
            opened.set(current.editor().edit(file, line, window));
            return null;
        });
        return failure == null && opened.get();
    }

    /**
     * As {@link #edit}, from a worker: hops to the UI thread, reads {@code window} there, and waits at most 30 seconds.
     * A timeout or an interrupt gives false, with a log line, and a late turn on the UI thread then asks nothing.
     *
     * @throws IllegalStateException on the UI thread
     */
    boolean editFromWorker(Path file, OptionalInt line, Supplier<Optional<UUID>> window) {
        if (onUi.getAsBoolean()) throw new IllegalStateException("Opening a file for editing waits for the UI thread; ask from a worker");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(window, "window");
        if (holder == null) return false;
        var answer = new CompletableFuture<Boolean>();
        var abandoned = new AtomicBoolean();
        try {
            ui.accept(() -> {
                if (abandoned.get()) { answer.complete(false); return; }
                try { answer.complete(edit(file, line, window.get())); }
                catch (Throwable failure) { answer.completeExceptionally(failure); throw failure; }
            });
        } catch (RuntimeException rejected) {
            LOG.log(System.Logger.Level.WARNING, "Could not ask the plugin editor to open " + file, rejected);
            return false;
        }
        try {
            return answer.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            abandoned.set(true);
            LOG.log(System.Logger.Level.WARNING, "The plugin editor did not answer within " + wait.toSeconds() + " s; "
                + file + " opens with the desktop");
            return false;
        } catch (InterruptedException interrupted) {
            abandoned.set(true);
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING, "Interrupted while the plugin editor opened " + file + "; it opens with the desktop");
            return false;
        } catch (ExecutionException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not ask the plugin editor to open " + file, failure.getCause());
            return false;
        }
    }
}
