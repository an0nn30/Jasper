package dev.jasper.browser.downloads;

import dev.jasper.browser.downloads.Download.State;
import dev.jasper.browser.page.DownloadProgress;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The downloads the Downloads menu lists, in the order they started (spec section 10). Each download reports one
 * outcome, and its later updates are ignored. A completed download stays {@link State#RUNNING running} at 100% until
 * {@link #finish} says it has been marked. The list keeps the 20 newest downloads, and every running one however many;
 * an older one that is not running leaves it, as Remove from List would remove it, at each {@link #begin}, outcome and
 * finish (ruling F19), since a menu that long would not fit on the screen. UI thread.
 */
public final class DownloadList {
    /** How many of the newest downloads the list keeps by their place in it, running or not; an older one stays only while it runs. */
    static final int NEWEST = 20;
    /** Chromium's name for a download's file before its target is set (ruling D11). */
    private static final Pattern PLACEHOLDER = Pattern.compile("Unconfirmed [0-9]+\\.crdownload");
    private final List<Entry> entries = new ArrayList<>();
    private long serials;

    /** How a download ended, once. */
    public enum Outcome {
        /** The whole file arrived: it is marked, then {@linkplain #finish finished}. */
        COMPLETED,
        /** Chromium interrupted it. */
        FAILED,
        /** It was cancelled. */
        STOPPED
    }

    /** A download and whether its outcome has been told. */
    private static final class Entry {
        Download download;
        Outcome outcome;

        Entry(Download download) {
            this.download = download;
        }
    }

    /**
     * Lists a download at its first update, running, with nothing received.
     *
     * @param key the download's key
     * @param name its file's name
     * @param file where it goes
     * @param source the address it comes from
     * @return the download, numbered after every one listed before; the one already listed under {@code key}, if any
     */
    public Download begin(DownloadKey key, String name, Path file, URI source) {
        Optional<Entry> known = entry(key);
        if (known.isPresent()) return known.get().download;
        var download = new Download(++serials, key, name, file, source, State.RUNNING, 0, 0, false);
        entries.add(new Entry(download));
        trim();
        return download;
    }

    /**
     * Applies an update to the download under {@code key}. While it runs, its bytes change, and a path Chromium names
     * replaces its file and name (ruling F2), except an intermediate {@code <name>.crdownload}, which names it
     * {@code <name>} and leaves the file it goes to, and Chromium's {@code Unconfirmed <digits>.crdownload}, which
     * changes neither. The completed update's path sets the file and the name, and the download stays running at 100%
     * until {@link #finish}; an interrupted one fails, and a cancelled one stops.
     *
     * @param key the download's key
     * @param progress Chromium's update
     * @return the download's outcome, the first time it has one; empty for progress, for any update after the
     *     outcome, and for a key the list does not hold
     */
    public Optional<Outcome> update(DownloadKey key, DownloadProgress progress) {
        Objects.requireNonNull(progress, "progress");
        Entry entry = entry(key).orElse(null);
        if (entry == null || entry.outcome != null) return Optional.empty();
        Download d = entry.download;
        long received = Math.max(0, progress.received()), total = Math.max(0, progress.total());
        Optional<Path> named = path(progress.fullPath());
        if (progress.complete()) {
            total = Math.max(total, received);
            Path file = named.orElse(d.file());
            String name = named.map(DownloadList::fileName).orElse(d.name());
            return told(entry, copy(d, name, file, State.RUNNING, total, total, false), Outcome.COMPLETED);
        }
        if (progress.canceled()) return told(entry, copy(d, d.name(), d.file(), State.STOPPED, received, total, false), Outcome.STOPPED);
        if (!progress.inProgress()) return told(entry, copy(d, d.name(), d.file(), State.FAILED, received, total, false), Outcome.FAILED);
        String name = d.name();
        Path file = d.file();
        if (named.isPresent()) {
            String shown = fileName(named.get());
            if (!shown.endsWith(DownloadNames.INTERMEDIATE)) {
                name = shown;
                file = named.get();
            } else if (!PLACEHOLDER.matcher(shown).matches() && shown.length() > DownloadNames.INTERMEDIATE.length()) {
                name = shown.substring(0, shown.length() - DownloadNames.INTERMEDIATE.length());
            }
        }
        entry.download = copy(d, name, file, State.RUNNING, received, total, false);
        return Optional.empty();
    }

    /**
     * Finishes a completed download once it has been marked. A download that has not completed, one already finished
     * and a key the list does not hold are left as they are.
     *
     * @param key the download's key
     * @param openable whether Open is offered: false when the downloaded-file mark failed on macOS or Windows (ruling F6)
     */
    public void finish(DownloadKey key, boolean openable) {
        Entry entry = entry(key).orElse(null);
        if (entry == null || entry.outcome != Outcome.COMPLETED || entry.download.state() != State.RUNNING) return;
        Download d = entry.download;
        entry.download = copy(d, d.name(), d.file(), State.FINISHED, d.received(), d.total(), openable);
        trim();
    }

    /**
     * Remove from List.
     *
     * @param serial the download's serial
     * @return whether it was removed: false while it runs, and when the list does not hold it
     */
    public boolean remove(long serial) {
        return entries.removeIf(entry -> entry.download.serial() == serial && entry.download.state() != State.RUNNING);
    }

    /** Clear All: removes every download that is not running. */
    public void clearAll() {
        entries.removeIf(entry -> entry.download.state() != State.RUNNING);
    }

    /**
     * The downloads, in the order they started.
     *
     * @return a snapshot
     */
    public List<Download> all() {
        return entries.stream().map(entry -> entry.download).toList();
    }

    private Optional<Outcome> told(Entry entry, Download download, Outcome outcome) {
        entry.download = download;
        entry.outcome = outcome;
        trim();
        return Optional.of(outcome);
    }

    /** {@code d} with what an update or {@link #finish} changes; its serial, key and source stay. */
    private static Download copy(Download d, String name, Path file, State state, long received, long total, boolean openable) {
        return new Download(d.serial(), d.key(), name, file, d.source(), state, received, total, openable);
    }

    private Optional<Entry> entry(DownloadKey key) {
        Objects.requireNonNull(key, "key");
        return entries.stream().filter(entry -> entry.download.key().equals(key)).findFirst();
    }

    /** Removes each download older than the 20 newest that is not running. */
    private void trim() {
        int older = entries.size() - NEWEST;
        for (int i = older - 1; i >= 0; i--)
            if (entries.get(i).download.state() != State.RUNNING) entries.remove(i);
    }

    /** The file Chromium names: empty for none, and for a path this system cannot read. */
    private static Optional<Path> path(String fullPath) {
        if (fullPath.isEmpty()) return Optional.empty();
        try {
            Path path = Path.of(fullPath);
            return path.getFileName() == null ? Optional.empty() : Optional.of(path);
        } catch (InvalidPathException unreadable) {
            return Optional.empty();
        }
    }

    private static String fileName(Path path) {
        return path.getFileName().toString();
    }
}
