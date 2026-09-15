package dev.jasper.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
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
import javax.swing.SwingUtilities;

/**
 * Application-wide shell history. One serial worker reads files and merges live captures; immutable
 * snapshots are published on the EDT. Jasper never writes a history file.
 */
final class ShellHistoryIndex implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(ShellHistoryIndex.class.getName());
    private static final int MAX_READ = 16 * 1024 * 1024;

    record SourceStats(long offset, int fullReads, int tailReads) {}

    private static final class FileState {
        long offset, size;
        FileTime modified;
        int fullReads, tailReads;
        List<ShellHistoryEntry> entries = List.of();
    }

    private final List<ShellHistorySource> sources;
    private final ExecutorService worker;
    private final Executor deliver;
    // Worker-only state.
    private final Map<ShellHistorySource, FileState> states = new HashMap<>();
    private final Map<ShellHistorySource, List<ShellHistoryEntry>> perSource = new LinkedHashMap<>();
    private final List<ShellHistoryEntry> live = new ArrayList<>();
    private final Set<ShellHistorySource> warned = new HashSet<>();
    // EDT-only state.
    private final List<Runnable> listeners = new ArrayList<>();
    private ShellHistorySnapshot snapshot = ShellHistorySnapshot.EMPTY;
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

    ShellHistorySnapshot snapshot() { CommandRegistry.requireEdt(); return snapshot; }

    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        listeners.add(listener);
        return new CommandRegistry.Subscription(() -> { CommandRegistry.requireEdt(); listeners.remove(listener); });
    }

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
        worker.execute(() -> {
            live.add(entry);
            if (live.size() > ShellHistorySnapshot.MAX_ENTRIES) live.removeFirst();
            publish(false);
        });
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
        publish(true);
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
        boolean tail = state.offset > 0 && size >= state.size;
        long from = tail ? state.offset : 0;
        if (size - from > MAX_READ) from = size - MAX_READ;
        byte[] bytes = read(file, from, size);
        if (from > 0 && !tail) {
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
        state.offset = from + parsed.consumed();
        state.size = size;
        state.modified = modified;
        perSource.put(source, state.entries);
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
        worker.shutdownNow();
    }
}
