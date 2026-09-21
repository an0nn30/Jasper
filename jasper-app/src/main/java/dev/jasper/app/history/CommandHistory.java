package dev.jasper.app.history;

import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.lifecycle.Subscription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class CommandHistory implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(CommandHistory.class.getName());

    private final Path file;
    private final ExecutorService worker;
    private final Executor deliver;
    private final List<Runnable> listeners = new ArrayList<>();
    private final AtomicReference<List<String>> pending = new AtomicReference<>();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private List<String> recent = List.of();
    private boolean loaded;
    private boolean dirtyDuringLoad;
    private boolean writing;
    private boolean closed;

    public CommandHistory() {
        file = null;
        worker = null;
        deliver = SwingUtilities::invokeLater;
        loaded = true;
    }

    public CommandHistory(Path file) {
        this(file, Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-command-history").factory()),
            SwingUtilities::invokeLater);
    }

    public CommandHistory(Path file, ExecutorService worker, Executor deliver) {
        CommandRegistry.requireEdt();
        this.file = Objects.requireNonNull(file);
        this.worker = Objects.requireNonNull(worker);
        this.deliver = Objects.requireNonNull(deliver);
        worker.execute(() -> {
            List<String> saved;
            try {
                saved = CommandHistoryFile.read(file);
            } catch (java.io.IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not read command history", failure);
                saved = List.of();
            }
            List<String> result = saved;
            deliver.execute(() -> loaded(result));
        });
    }

    static List<String> merge(List<String> newest, List<String> older) {
        var ordered = new LinkedHashSet<String>();
        ordered.addAll(newest);
        ordered.addAll(older);
        return ordered.stream().limit(3).toList();
    }

    public List<String> recent() {
        CommandRegistry.requireEdt();
        return recent;
    }

    public Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        if (closed) throw new IllegalStateException("History is closed");
        listeners.add(listener);
        return new Subscription(() -> {
            CommandRegistry.requireEdt();
            listeners.remove(listener);
        });
    }

    public void record(String id) {
        CommandRegistry.requireEdt();
        if (closed) return;
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Invalid command ID");
        }
        recent = merge(List.of(id), recent);
        if (!loaded) {
            dirtyDuringLoad = true;
        } else if (worker != null) {
            pending.set(recent);
        }
        notifyListeners();
        pump();
    }

    private void loaded(List<String> saved) {
        CommandRegistry.requireEdt();
        recent = merge(recent, saved);
        loaded = true;
        if (dirtyDuringLoad) pending.set(recent);
        notifyListeners();
        pump();
    }

    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            if (closed) return;
            listener.run();
        }
    }

    private void pump() {
        CommandRegistry.requireEdt();
        if (worker == null || !loaded || writing) return;
        if (pending.get() == null) {
            if (closed) {
                worker.shutdown();
                finished.complete(null);
            }
            return;
        }
        writing = true;
        worker.execute(() -> {
            try {
                for (List<String> snapshot; (snapshot = pending.getAndSet(null)) != null;) {
                    try {
                        CommandHistoryFile.write(file, snapshot);
                    } catch (java.io.IOException failure) {
                        LOG.log(System.Logger.Level.WARNING, "Could not save command history", failure);
                    }
                }
            } finally {
                deliver.execute(() -> {
                    writing = false;
                    pump();
                });
            }
        });
    }

    public CompletableFuture<Void> closedFuture() {
        return finished;
    }

    @Override public void close() {
        CommandRegistry.requireEdt();
        if (closed) return;
        closed = true;
        listeners.clear();
        if (worker == null) {
            finished.complete(null);
            return;
        }
        pump();
        Thread.ofPlatform().name("jasper-command-history-shutdown").start(() -> {
            try {
                finished.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException failure) {
                LOG.log(System.Logger.Level.WARNING,
                    "Command history shutdown did not finish within two seconds", failure);
            }
        });
    }
}
