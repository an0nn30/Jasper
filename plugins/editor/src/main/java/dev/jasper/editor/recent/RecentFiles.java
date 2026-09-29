package dev.jasper.editor.recent;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;

/**
 * The files the Editor opened, newest first. Entries not opened within the retention setting's days are forgotten
 * (0 days keeps none), and at most {@link #MAX} are kept. UI thread only; every change is saved on the background
 * executor, the last change winning.
 */
public final class RecentFiles {
    /** The most entries kept, whatever the retention. */
    public static final int MAX = 50;

    private final Path file;
    private final Executor background;
    private final IntSupplier days;
    private final List<RecentFile> entries = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long generation;
    private final Object writeLock = new Object();
    private long written;

    private RecentFiles(Path file, Executor background, IntSupplier days) {
        this.file = Objects.requireNonNull(file); this.background = Objects.requireNonNull(background); this.days = Objects.requireNonNull(days);
    }

    /**
     * Reads the file (blocking; call while the plugin starts), keeps the newest entry per key, and forgets expired ones.
     *
     * @param file {@code recent-files.toml}
     * @param background where saves run
     * @param days the retention setting, 0 to 365
     * @param now the current time
     * @return the list
     */
    public static RecentFiles load(Path file, Executor background, IntSupplier days, Instant now) {
        var recent = new RecentFiles(file, background, days);
        var seen = new HashSet<String>();
        RecentFilesFile.read(file).stream().sorted(Comparator.comparing(RecentFile::opened).reversed())
            .filter(entry -> seen.add(entry.key())).forEach(recent.entries::add);
        if (recent.dropExpired(now)) recent.save();
        return recent;
    }

    /**
     * Records a document as opened now, moving it to the top; does nothing while the retention is 0 days or when the
     * document has no usable key or name.
     *
     * @param document the document opened
     * @param now the current time
     */
    public void record(EditorDocument document, Instant now) {
        if (days.getAsInt() <= 0) return;
        RecentFile entry;
        try { entry = new RecentFile(document.key(), document.name(), document.location(), document.host(), now); }
        catch (RuntimeException unusable) { return; }
        entries.removeIf(existing -> existing.key().equals(entry.key()));
        entries.addFirst(entry);
        dropExpired(now);
        save();
        changed();
    }

    /** @param key the entry to forget */
    public void remove(String key) {
        if (entries.removeIf(entry -> entry.key().equals(key))) { save(); changed(); }
    }

    /**
     * @param now the current time
     * @return the entries opened within the retention, newest first
     */
    public List<RecentFile> entries(Instant now) {
        int kept = days.getAsInt();
        if (kept <= 0) return List.of();
        Instant cutoff = now.minus(Duration.ofDays(kept));
        return entries.stream().filter(entry -> !entry.opened().isBefore(cutoff)).toList();
    }

    /**
     * Forgets expired entries (all of them at 0 days), saving and telling listeners when any went.
     *
     * @param now the current time
     */
    public void prune(Instant now) {
        if (dropExpired(now)) { save(); changed(); }
    }

    /**
     * @param listener runs after the list changes
     * @return removes the listener
     */
    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }

    private boolean dropExpired(Instant now) {
        int kept = days.getAsInt();
        if (kept <= 0) {
            boolean any = !entries.isEmpty();
            entries.clear();
            return any;
        }
        Instant cutoff = now.minus(Duration.ofDays(kept));
        boolean changed = entries.removeIf(entry -> entry.opened().isBefore(cutoff));
        while (entries.size() > MAX) { entries.removeLast(); changed = true; }
        return changed;
    }

    private void save() {
        long mine = ++generation;
        List<RecentFile> snapshot = List.copyOf(entries);
        background.execute(() -> {
            synchronized (writeLock) {
                if (mine <= written) return;
                written = mine;
                try { RecentFilesFile.write(file, snapshot); }
                catch (IOException | RuntimeException failed) { /* the list stays in memory; the next change writes again */ }
            }
        });
    }

    private void changed() { listeners.forEach(Runnable::run); }
}
