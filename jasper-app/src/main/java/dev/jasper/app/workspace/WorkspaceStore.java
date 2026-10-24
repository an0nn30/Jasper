package dev.jasper.app.workspace;

import dev.jasper.app.persistence.WorkspaceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Where Jasper's windows are saved (workspace tabs spec 13): {@code workspace.toml} in the Jasper home. The first change
 * after a save is saved {@link #DELAY} later, with every change made meanwhile, so a crash loses at most that; a window
 * closing saves at once. Each save takes its snapshot on the EDT and writes it on {@code writer}, off the EDT, after the
 * writes before it, whatever happened to them. Closing the main window, Quit and going to the background save, then hold
 * every save ({@link #hold()}) until the windows are restored ({@link #load()}). A snapshot or a write that fails, or a
 * write the writer refuses, is logged and costs only that save: the next save writes the windows, changed or not. EDT
 * only, except {@link #written()}.
 */
public final class WorkspaceStore {
    /** The longest a change waits to be saved (spec 13: "within one second of each layout change"). */
    public static final Duration DELAY = Duration.ofSeconds(1);
    private static final System.Logger LOG = System.getLogger(WorkspaceStore.class.getName());

    private final Path file;
    private final Supplier<Optional<WorkspaceFile.Snapshot>> snapshot;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Executor writer;
    private final Consumer<Path> setAside;
    /** Cancels the save a change scheduled; null while none is pending. */
    private Runnable pending;
    private boolean held;
    /** What this process saved last: what reopening the main window restores. */
    private WorkspaceFile.Snapshot saved;
    /**
     * What the file holds, or the latest write asked for will put there: an equal snapshot is not written again. A write
     * that fails or that the writer refuses takes it back, so the next save writes the same windows again. Any thread.
     */
    private final AtomicReference<WorkspaceFile.Snapshot> lastWritten = new AtomicReference<>();
    /** Whether the reader kept the file {@link #load()} read last aside already ({@code Loaded.Restore.keptAside}). */
    private boolean keptOnLoad;
    private volatile CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

    /**
     * The store of {@code file}.
     *
     * @param file {@code workspace.toml}
     * @param snapshot what to save now; empty while there is nothing to save (no main window open)
     * @param schedule runs a task on the EDT after a delay and returns how to cancel it
     * @param writer where writes run, off the EDT
     * @param setAside told where saved windows that could not be restored were kept ({@code workspace.toml.bad}): an
     *     unreadable file {@link #load()} read, a file whose reader dropped a window, a tab or an item entry, or a
     *     workspace {@link #setAside(WorkspaceFile.Snapshot)} keeps
     */
    public WorkspaceStore(Path file, Supplier<Optional<WorkspaceFile.Snapshot>> snapshot, BiFunction<Duration, Runnable, Runnable> schedule,
                          Executor writer, Consumer<Path> setAside) {
        this.file = Objects.requireNonNull(file, "file");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.setAside = Objects.requireNonNull(setAside, "setAside");
    }

    /**
     * What the main window restores, and saving starts again: what this process saved last, else the file. A file that
     * cannot be read was set aside ({@code workspace.toml.bad}) and {@code setAside} hears where; so does a file whose
     * reader dropped a window, a tab or an item entry, which the reader copied there (workspace plan 2).
     *
     * @return the windows to restore, or that the main window opens fresh
     */
    public WorkspaceFile.Loaded load() {
        requireEdt();
        held = false;
        keptOnLoad = false;
        if (saved != null) return new WorkspaceFile.Loaded.Restore(saved, Optional.empty());
        WorkspaceFile.Loaded loaded = WorkspaceFile.read(file);
        if (loaded instanceof WorkspaceFile.Loaded.Fresh fresh) fresh.setAside().ifPresent(setAside);
        if (loaded instanceof WorkspaceFile.Loaded.Restore restore && restore.keptAside().isPresent()) {
            keptOnLoad = true;
            setAside.accept(restore.keptAside().get());
        }
        return loaded;
    }

    /**
     * Keeps {@code windows}, which {@link #load()} gave and which did not all come back, as {@code workspace.toml.bad}
     * (spec 15's set-aside: the next save would otherwise overwrite them), and {@code setAside} hears where. When the
     * reader kept the file they came from aside already, that copy, which holds them and what the reader dropped, stays
     * and nothing more is said. The next save writes the windows as they are then.
     *
     * @param windows the workspace as loaded
     */
    public void setAside(WorkspaceFile.Snapshot windows) {
        requireEdt();
        Objects.requireNonNull(windows, "windows");
        saved = null;
        lastWritten.set(null);
        if (keptOnLoad) return;
        // Named as WorkspaceFile names an unreadable file it sets aside.
        Path kept = file.resolveSibling(file.getFileName() + ".bad");
        writeLater(kept, windows);
        setAside.accept(kept);
    }

    /** Something that is saved changed: it is saved within {@link #DELAY}, with whatever else changes meanwhile. */
    public void changed() {
        requireEdt();
        if (held || pending != null) return;
        pending = schedule.apply(DELAY, this::due);
    }

    private void due() {
        pending = null;
        save();
    }

    /**
     * Saves now, as a closing window does; a save a change scheduled is not needed any more once this one has the
     * windows. With nothing to save yet (no main window open, or it opens), or a snapshot that fails, the scheduled save
     * stays.
     */
    public void save() {
        requireEdt();
        if (held) return;
        Optional<WorkspaceFile.Snapshot> taken;
        try {
            taken = snapshot.get();
        } catch (RuntimeException | Error failure) {
            // A window that cannot say what it holds costs this save, never the close or quit that asked for it.
            LOG.log(System.Logger.Level.WARNING, "Could not take the workspace's snapshot to save", failure);
            return;
        }
        if (taken.isEmpty()) return;
        if (pending != null) {
            Runnable cancel = pending;
            pending = null;
            cancel.run();
        }
        if (taken.get().equals(lastWritten.get())) return;
        WorkspaceFile.Snapshot writing = taken.get();
        saved = writing;
        lastWritten.set(writing);
        writeLater(file, writing);
    }

    /**
     * The main window and its child windows close together (closing the main window, Quit, going to the background): saves
     * them as they are now, then nothing until {@link #load()} restores them, so closing their tabs is not saved. It holds
     * even when that save fails.
     */
    public void hold() {
        requireEdt();
        try {
            save();
        } finally {
            held = true;
        }
    }

    /** @return whether saving waits for the windows to be restored */
    public boolean held() { return held; }

    /** @return completes, never exceptionally, once every write asked for so far has finished, written or not; any thread */
    public CompletableFuture<Void> written() { return writes.handle((done, failure) -> (Void) null); }

    /** Writes after every write asked for before, however that one ended: a failed write, or a refused one, ends no chain. */
    private void writeLater(Path target, WorkspaceFile.Snapshot writing) {
        writes = writes.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(target, writing), writer)
            .whenComplete((done, refused) -> {
                if (refused == null) return;
                // The writer refused it (it is shutting down, say): a write that never ran, logged as one that failed.
                LOG.log(System.Logger.Level.WARNING, "Could not save the workspace to " + target, refused);
                unwritten(target, writing);
            });
    }

    private void write(Path target, WorkspaceFile.Snapshot writing) {
        try {
            WorkspaceFile.write(target, writing);
        } catch (IOException | RuntimeException | Error failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the workspace to " + target, failure);
            unwritten(target, writing);
        }
    }

    /** {@code writing} did not reach {@code target}: when it was the file's latest, the next save writes those windows again. */
    private void unwritten(Path target, WorkspaceFile.Snapshot writing) {
        if (target.equals(file)) lastWritten.compareAndSet(writing, null);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The workspace store belongs to the EDT");
    }
}
