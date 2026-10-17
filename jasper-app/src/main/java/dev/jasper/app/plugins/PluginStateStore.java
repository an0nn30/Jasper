package dev.jasper.app.plugins;

import dev.jasper.app.persistence.TomlStateFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Enabled flags, consented capability sets and pending removals. More than one Jasper process can edit
 * the file, so every change is a locked read-modify-write; nothing writes it outside {@link #transact},
 * in particular not at shutdown. Callers choose the thread: never the EDT for {@code transact}.
 */
final class PluginStateStore {
    /**
     * One plugin's saved state. {@code optIn} records that the user turned on a bundled plugin that is off by default
     * ({@code opt_in = true}); only enabling such a plugin writes it, and any write that disables clears it.
     */
    record Entry(boolean enabled, Set<String> consented, boolean remove, boolean optIn) {
        static final Entry DEFAULT = new Entry(true, Set.of(), false, false);
        Entry { consented = Set.copyOf(consented); }
        Entry(boolean enabled, Set<String> consented, boolean remove) { this(enabled, consented, remove, false); }
    }

    /**
     * The one effective-enabled rule (spec 4.3): enabled unless the entry says disabled, or there is no entry and the
     * candidate is a bundled plugin whose descriptor says {@code enabled_by_default = false}.
     */
    static boolean effective(Entry entryOrNull, PluginCandidate candidate) {
        if (entryOrNull != null) return entryOrNull.enabled();
        return candidate.origin() != PluginCandidate.Origin.BUNDLED || candidate.descriptor().enabledByDefault();
    }

    /**
     * The entry once maintenance has carried out a pending removal: the entry itself when it is not marked, the bundled
     * plugin's kept opt-in when it is marked and opted in, otherwise none. Maintenance and the Plugins page's prediction
     * both use it (spec 4.4).
     */
    static java.util.Optional<Entry> afterRemoval(Entry entry) {
        if (!entry.remove()) return java.util.Optional.of(entry);
        return entry.optIn() ? java.util.Optional.of(new Entry(true, Set.of(), false, true)) : java.util.Optional.empty();
    }

    private static final int MAX_BYTES = 256 * 1024;
    /** File locks are per process; this serializes editors inside one. */
    private static final ReentrantLock IN_PROCESS = new ReentrantLock();
    private final Path file;
    private final Path lock;
    private final Duration wait;

    PluginStateStore(Path file, Path lock, Duration wait) {
        this.file = Objects.requireNonNull(file); this.lock = Objects.requireNonNull(lock); this.wait = Objects.requireNonNull(wait);
    }

    /** Lock-free: the file is only ever replaced atomically. */
    Map<String, Entry> read() throws IOException {
        var text = TomlStateFile.readBounded(file, MAX_BYTES, "Plugin state");
        if (text.isEmpty()) return new TreeMap<>();
        TomlParseResult toml = Toml.parse(text.get());
        if (toml.hasErrors()) throw new IOException("Plugin state is not valid TOML: " + file);
        Map<String, Entry> result = new TreeMap<>();
        Object plugins = toml.get(List.of("plugins"));
        if (plugins == null) return result;
        if (!(plugins instanceof TomlTable table)) throw new IOException("Plugin state has a malformed plugins table: " + file);
        for (String id : table.keySet()) {
            if (!(table.get(List.of(id)) instanceof TomlTable entry)) throw new IOException("Plugin state entry is not a table: " + id);
            result.put(id, new Entry(flag(entry, "enabled", true, id), strings(entry, id), flag(entry, "remove", false, id),
                flag(entry, "opt_in", false, id)));
        }
        return result;
    }

    void transact(UnaryOperator<Map<String, Entry>> edit) throws IOException {
        long deadline = System.nanoTime() + wait.toNanos();
        boolean inProcess;
        try { inProcess = IN_PROCESS.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted waiting for the plugin state lock"); }
        if (!inProcess) throw new IOException("Timed out waiting for the plugin state lock");
        try {
            Files.createDirectories(lock.toAbsolutePath().getParent());
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock held = acquire(channel, deadline)) {
                Objects.requireNonNull(held);
                Map<String, Entry> next = edit.apply(read());
                TomlStateFile.writeAtomically(file, ".plugins-", render(next));
            }
        } finally { IN_PROCESS.unlock(); }
    }

    /** Enables or disables, keeping consent and a pending removal; disabling clears an opt-in. */
    static UnaryOperator<Map<String, Entry>> enabling(String id, boolean enabled) { return enabling(id, enabled, false); }

    /** As {@link #enabling(String, boolean)}; {@code optIn} records that this enables a bundled plugin that is off by default. */
    static UnaryOperator<Map<String, Entry>> enabling(String id, boolean enabled, boolean optIn) {
        return state -> {
            Entry now = state.getOrDefault(id, Entry.DEFAULT);
            state.put(id, new Entry(enabled, now.consented(), now.remove(), enabled && (optIn || now.optIn())));
            return state;
        };
    }

    /** Enables the presentation provider {@code id} and disables every provider in {@code others}, in one transaction (spec 4.4). */
    static UnaryOperator<Map<String, Entry>> enablingPresentation(String id, boolean optIn, Set<String> others) {
        return state -> {
            enabling(id, true, optIn).apply(state);
            for (String other : others) enabling(other, false).apply(state);
            return state;
        };
    }

    /** Records consent to the presentation provider {@code id} and disables every provider in {@code others}, in one transaction. */
    static UnaryOperator<Map<String, Entry>> consentingPresentation(String id, Set<String> capabilities, Set<String> others) {
        return state -> {
            consenting(id, capabilities).apply(state);
            for (String other : others) enabling(other, false).apply(state);
            return state;
        };
    }

    /** The user reviewed exactly these capabilities: that enables the plugin and cancels a pending removal. */
    static UnaryOperator<Map<String, Entry>> consenting(String id, Set<String> capabilities) {
        return state -> { state.put(id, new Entry(true, capabilities, false)); return state; };
    }

    /**
     * Marks or unmarks a removal. An entry means the user reviewed the plugin, so unmarking never
     * leaves a default entry behind: it would let an unreviewed plugin without capabilities load.
     */
    static UnaryOperator<Map<String, Entry>> removing(String id, boolean remove) {
        return state -> {
            Entry now = state.getOrDefault(id, Entry.DEFAULT);
            Entry next = new Entry(now.enabled(), now.consented(), remove, now.optIn());
            if (next.equals(Entry.DEFAULT)) state.remove(id); else state.put(id, next);
            return state;
        };
    }

    private static FileLock acquire(FileChannel channel, long deadline) throws IOException {
        while (true) {
            try {
                FileLock held = channel.tryLock();
                if (held != null) return held;
            } catch (OverlappingFileLockException heldInThisProcess) {
                // Another channel in this JVM holds it; treat exactly like another process.
            }
            if (System.nanoTime() >= deadline) throw new IOException("Timed out waiting for the plugin state lock");
            try { Thread.sleep(25); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted waiting for the plugin state lock"); }
        }
    }

    private static boolean flag(TomlTable entry, String key, boolean fallback, String id) throws IOException {
        Object value = entry.get(List.of(key));
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new IOException("Plugin state " + id + "." + key + " must be a boolean");
        return flag;
    }

    private static Set<String> strings(TomlTable entry, String id) throws IOException {
        Object value = entry.get(List.of("consented"));
        if (value == null) return Set.of();
        if (!(value instanceof TomlArray array)) throw new IOException("Plugin state " + id + ".consented must be an array");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IOException("Plugin state " + id + ".consented must hold strings");
            result.add(text);
        }
        return result;
    }

    /** Ids and capabilities are restricted to characters that need no TOML escaping. */
    private static String render(Map<String, Entry> state) {
        var text = new StringBuilder("version = 1\n");
        for (var item : new TreeMap<>(state).entrySet()) {
            Entry entry = item.getValue();
            text.append("\n[plugins.\"").append(item.getKey()).append("\"]\n")
                .append("enabled = ").append(entry.enabled()).append('\n')
                .append("consented = [").append(new TreeSet<>(entry.consented()).stream()
                    .map(capability -> "\"" + capability + "\"").collect(Collectors.joining(", "))).append("]\n")
                .append("remove = ").append(entry.remove()).append('\n');
            if (entry.optIn()) text.append("opt_in = true\n");
        }
        return text.toString();
    }
}
