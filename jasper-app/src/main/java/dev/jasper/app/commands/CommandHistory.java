package dev.jasper.app.commands;

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

/**
 * EDT owner of command recency and palette uses, with asynchronous persistence. The application closes it and waits for
 * its flush future during shutdown.
 */
public final class CommandHistory implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(CommandHistory.class.getName());

    private final Path file;
    private final ExecutorService worker;
    private final Executor deliver;
    private final List<Runnable> listeners = new ArrayList<>();
    /** The most palette uses kept; the least recently used go first. */
    static final int MAX_USES = 50;
    private record Snapshot(List<String> recent, List<PaletteUse> uses) { }
    private final AtomicReference<Snapshot> pending = new AtomicReference<>();
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private List<String> recent = List.of();
    private List<PaletteUse> uses = List.of();
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
            CommandHistoryFile.Saved saved;
            try {
                saved = CommandHistoryFile.readAll(file);
            } catch (java.io.IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not read command history", failure);
                saved = new CommandHistoryFile.Saved(List.of(), List.of());
            }
            CommandHistoryFile.Saved result = saved;
            deliver.execute(() -> loaded(result));
        });
    }

    static List<String> merge(List<String> newest, List<String> older) {
        var ordered = new LinkedHashSet<String>();
        ordered.addAll(newest);
        ordered.addAll(older);
        return ordered.stream().limit(3).toList();
    }

    /** Palette uses, most recent first. */
    public List<PaletteUse> uses() {
        CommandRegistry.requireEdt();
        return uses;
    }

    /**
     * Counts one run of a palette row at {@code now} (epoch milliseconds). An id or title the file cannot hold is ignored:
     * recording must never stop the row from running.
     */
    public void recordUse(String scopeId, String rowId, String title, long now) {
        CommandRegistry.requireEdt();
        if (closed || !PaletteUse.valid(scopeId, rowId, title)) return;
        var next = new ArrayList<PaletteUse>();
        int count = 1;
        for (PaletteUse use : uses) {
            if (use.scopeId().equals(scopeId) && use.rowId().equals(rowId)) count = use.count() + 1;
            else next.add(use);
        }
        next.addFirst(new PaletteUse(scopeId, rowId, title, count, now));
        uses = List.copyOf(next.subList(0, Math.min(MAX_USES, next.size())));
        changed();
    }

    /** Newest first; a use in both lists sums its counts and keeps the later time. */
    static List<PaletteUse> mergeUses(List<PaletteUse> newest, List<PaletteUse> older) {
        var merged = new ArrayList<>(newest);
        for (PaletteUse saved : older) {
            int index = -1;
            for (int i = 0; i < merged.size(); i++) if (merged.get(i).same(saved)) { index = i; break; }
            if (index < 0) { merged.add(saved); continue; }
            PaletteUse current = merged.get(index);
            merged.set(index, new PaletteUse(current.scopeId(), current.rowId(), current.title(), current.count() + saved.count(),
                Math.max(current.lastUsed(), saved.lastUsed())));
        }
        merged.sort(java.util.Comparator.comparingLong(PaletteUse::lastUsed).reversed());
        return List.copyOf(merged.subList(0, Math.min(MAX_USES, merged.size())));
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
        changed();
    }

    private void changed() {
        if (!loaded) {
            dirtyDuringLoad = true;
        } else if (worker != null) {
            pending.set(new Snapshot(recent, uses));
        }
        notifyListeners();
        pump();
    }

    private void loaded(CommandHistoryFile.Saved saved) {
        CommandRegistry.requireEdt();
        recent = merge(recent, saved.recent());
        uses = mergeUses(uses, saved.uses());
        loaded = true;
        if (dirtyDuringLoad) pending.set(new Snapshot(recent, uses));
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
                for (Snapshot snapshot; (snapshot = pending.getAndSet(null)) != null;) {
                    try {
                        CommandHistoryFile.write(file, snapshot.recent(), snapshot.uses());
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
