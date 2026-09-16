package dev.jasper.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import javax.swing.SwingUtilities;

/**
 * Application-wide shell history. One serial worker reads files and merges live captures; immutable
 * snapshots are published on the EDT. Jasper never writes a history file.
 */
final class ShellHistoryIndex implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(ShellHistoryIndex.class.getName());
    private static final int MAX_READ = 16 * 1024 * 1024;
    private static final int FINGERPRINT_BYTES = 64;
    /** Short enough to feel immediate; a quiet tick is one stat per source, because readSource
     *  returns as soon as size and mtime are unchanged. */
    private static final int POLL_MILLIS = 1000;

    record SourceStats(long offset, int fullReads, int tailReads) {}

    private static final class FileState {
        long offset, size;
        FileTime modified;
        int fullReads, tailReads;
        List<ShellHistoryEntry> entries = List.of();
        /** The last (up to) 64 bytes of the file before {@code offset}, read back after each read; see {@link #fingerprintMatches}. */
        byte[] fingerprint = new byte[0];
    }

    private final List<ShellHistorySource> sources;
    private final ExecutorService worker;
    private final Executor deliver;
    // Worker-only state.
    private final Map<ShellHistorySource, FileState> states = new HashMap<>();
    private final Map<ShellHistorySource, ShellHistorySnapshot.Source> perSource = new LinkedHashMap<>();
    private final List<ShellHistoryEntry> live = new ArrayList<>();
    private final Set<ShellHistorySource> warned = new HashSet<>();
    // EDT-only state.
    private final List<Runnable> listeners = new ArrayList<>();
    private ShellHistorySnapshot snapshot = ShellHistorySnapshot.EMPTY;
    private javax.swing.Timer poll;
    private boolean refreshing, refreshQueued;
    private volatile boolean closed;

    ShellHistoryIndex(List<ShellHistorySource> sources) {
        this(sources, Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-shell-history").factory()), SwingUtilities::invokeLater);
    }

    ShellHistoryIndex(List<ShellHistorySource> sources, ExecutorService worker, Executor deliver) {
        this.sources = List.copyOf(sources);
        this.worker = Objects.requireNonNull(worker);
        this.deliver = Objects.requireNonNull(deliver);
    }

    /** Every shell history file this machine may have, discovered from the home directory and environment. */
    static ShellHistoryIndex discovered() {
        return new ShellHistoryIndex(ShellHistorySource.discover(Path.of(System.getProperty("user.home")),
            System.getenv(), System.getProperty("os.name")));
    }

    ShellHistorySnapshot snapshot() { CommandRegistry.requireEdt(); return snapshot; }

    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        listeners.add(listener);
        startPolling();
        return new CommandRegistry.Subscription(() -> {
            CommandRegistry.requireEdt();
            listeners.remove(listener);
            if (listeners.isEmpty() && poll != null) poll.stop();
        });
    }

    /**
     * History files change without anyone asking, so the index looks for itself rather than waiting for
     * the palette to be opened. Only while something is listening: a window with no palette pays nothing.
     */
    private void startPolling() {
        if (closed) return;
        if (poll == null) {
            poll = new javax.swing.Timer(POLL_MILLIS, event -> refresh());
            poll.setRepeats(true);
        }
        poll.start();
    }

    /** Test seam: the poll timer, or null before the first listener subscribes. */
    javax.swing.Timer pollTimer() { return poll; }

    /** Re-reads changed files on the worker; at most one refresh runs and one more waits. */
    void refresh() {
        CommandRegistry.requireEdt();
        if (closed) return;
        if (refreshing) { refreshQueued = true; return; }
        refreshing = true;
        worker.execute(this::scan);
    }

    /** A command the terminal saw run; safe from any thread. */
    void record(ShellHistoryEntry entry) {
        if (closed) return;
        try {
            worker.execute(() -> {
                live.add(entry);
                if (live.size() > ShellHistorySnapshot.MAX_ENTRIES) live.removeFirst();
                publish(false);
            });
        } catch (RejectedExecutionException ignored) {
            // close() raced with this call between the closed check above and worker.execute: nothing to record.
        }
    }

    /** Worker-only test seam: how many bytes the rewrite fingerprint currently covers. */
    int fingerprintLength(ShellHistorySource source) {
        FileState state = states.get(source);
        return state == null ? 0 : state.fingerprint.length;
    }

    /** Worker-only test seam. */
    SourceStats stats(ShellHistorySource source) {
        FileState state = states.get(source);
        return state == null ? new SourceStats(0, 0, 0) : new SourceStats(state.offset, state.fullReads, state.tailReads);
    }

    private void scan() {
        for (ShellHistorySource source : sources) {
            try {
                readSource(source);
            } catch (IOException | RuntimeException failure) {
                if (warned.add(source))
                    LOG.log(System.Logger.Level.WARNING, "Could not read " + source.shell().label() + " history at " + source.file(), failure);
            }
        }
        try {
            publish(true);
        } catch (RuntimeException failure) {
            // ShellHistorySnapshot.build ran (and threw) before publish reached deliver.execute, so the
            // usual "refreshing = false" delivery never happens. Without this, refreshing stays true
            // forever and every later refresh() is queued and never runs. Catch (not just try/finally)
            // so the normal success path is not double-delivered here as well as inside publish().
            LOG.log(System.Logger.Level.WARNING, "Shell history snapshot build failed", failure);
            deliver.execute(() -> {
                refreshing = false;
                if (closed) return;
                if (refreshQueued) { refreshQueued = false; refresh(); }
            });
        }
    }

    private void readSource(ShellHistorySource source) throws IOException {
        FileState state = states.computeIfAbsent(source, ignored -> new FileState());
        Path file = source.file();
        if (!Files.isRegularFile(file)) {
            if (!state.entries.isEmpty() || state.offset != 0) { states.remove(source); perSource.remove(source); }
            return;
        }
        long size = Files.size(file);
        FileTime modified = Files.getLastModifiedTime(file);
        if (size == state.size && modified.equals(state.modified) && state.offset > 0) return;
        boolean tail = state.offset > 0 && size >= state.size && fingerprintMatches(file, state);
        long from = tail ? state.offset : 0;
        boolean clipped = size - from > MAX_READ;
        if (clipped) from = size - MAX_READ;
        byte[] bytes = read(file, from, size);
        if (from > 0 && (!tail || clipped)) {
            int newline = 0;
            while (newline < bytes.length && bytes[newline] != '\n') newline++;
            int skip = Math.min(bytes.length, newline + 1);
            byte[] trimmed = new byte[bytes.length - skip];
            System.arraycopy(bytes, skip, trimmed, 0, trimmed.length);
            bytes = trimmed; from += skip;
        }
        var parsed = ShellHistoryParser.parse(source.shell(), bytes);
        if (tail) {
            state.tailReads++;
            var merged = new ArrayList<>(state.entries);
            merged.addAll(parsed.entries());
            while (merged.size() > ShellHistorySnapshot.MAX_ENTRIES) merged.removeFirst();
            state.entries = List.copyOf(merged);
        } else {
            state.fullReads++;
            List<ShellHistoryEntry> entries = parsed.entries();
            state.entries = entries.size() > ShellHistorySnapshot.MAX_ENTRIES
                ? List.copyOf(entries.subList(entries.size() - ShellHistorySnapshot.MAX_ENTRIES, entries.size())) : entries;
        }
        boolean progressed = parsed.consumed() > 0 || !tail;
        state.offset = from + parsed.consumed();
        state.size = size;
        state.modified = modified;
        // Always the last 64 bytes of the file before the offset, not just of this read: a small tail
        // read must not narrow the window, and a read that consumed nothing keeps the last good one.
        if (progressed) state.fingerprint = read(file, Math.max(0, state.offset - FINGERPRINT_BYTES), state.offset);
        perSource.put(source, new ShellHistorySnapshot.Source(state.entries,
            state.modified == null ? 0 : state.modified.to(TimeUnit.SECONDS)));
    }

    /**
     * bash without {@code histappend} overwrites its history file on shell exit, and zsh rewrites
     * {@code $HISTFILE} when trimming to {@code SAVEHIST}; either can leave the file the same size or
     * larger, so size/mtime alone cannot tell a rewrite from a genuine append. This re-reads the bytes
     * that immediately preceded the last consumed offset and compares them against what was read back
     * then: a mismatch means the file was rewritten underneath us, so the caller must do a full read
     * instead of a tail read starting mid-line in unrelated content.
     */
    private static boolean fingerprintMatches(Path file, FileState state) throws IOException {
        if (state.fingerprint.length == 0) return true;
        long start = state.offset - state.fingerprint.length;
        if (start < 0) return false;
        byte[] actual = read(file, start, start + state.fingerprint.length);
        return Arrays.equals(actual, state.fingerprint);
    }

    private static byte[] read(Path file, long from, long size) throws IOException {
        int length = (int) Math.max(0, Math.min(Integer.MAX_VALUE - 8, size - from));
        var buffer = ByteBuffer.allocate(length);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(from);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* fill */ }
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void publish(boolean fromScan) {
        ShellHistorySnapshot next = ShellHistorySnapshot.build(perSource.values(), live, ShellHistorySnapshot.MAX_ENTRIES);
        deliver.execute(() -> {
            if (fromScan) refreshing = false;
            if (closed) return;
            snapshot = next;
            for (Runnable listener : List.copyOf(listeners)) listener.run();
            if (fromScan && refreshQueued) { refreshQueued = false; refresh(); }
        });
    }

    @Override public void close() {
        closed = true;
        listeners.clear();
        if (poll != null) poll.stop();
        worker.shutdownNow();
    }
}
