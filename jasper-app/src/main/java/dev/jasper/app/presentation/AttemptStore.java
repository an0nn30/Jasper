package dev.jasper.app.presentation;

import dev.jasper.app.persistence.TomlStateFile;
import dev.jasper.app.settings.TomlLiterals;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * {@code <home>/presentation-attempt.toml} (spec 4.6): per provider, the look last tried (its version, digest and the
 * build that tried it), whether it finished starting, and the digest whose problem was last announced. More than one
 * Jasper process can write it, so every change is a locked read-modify-write under {@code presentation-attempt.lock}; a
 * busy lock or an unwritable file is logged and the write skipped. Content that cannot be read counts as absent. Any thread.
 */
public final class AttemptStore {
    /** Whether a tried look finished starting. */
    public enum State { PENDING, PROVEN, FAILED }

    /**
     * One provider's record; empty strings and an empty state mean "not recorded".
     *
     * @param version the provider's version when tried
     * @param digest the look's digest when tried
     * @param build the application's code location and modification time when tried
     * @param state whether it finished starting
     * @param announced the digest whose problem notice was last shown
     */
    public record Attempt(String version, String digest, String build, Optional<State> state, String announced) {
        /** Nothing recorded. */
        public static final Attempt NONE = new Attempt("", "", "", Optional.empty(), "");

        /** Every part is required. */
        public Attempt {
            Objects.requireNonNull(version, "version"); Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(build, "build");
            Objects.requireNonNull(state, "state"); Objects.requireNonNull(announced, "announced");
        }

        /** Whether this record skips the look {@code digest} of {@code build}: it is pending or failed for exactly it. */
        public boolean blocks(String digest, String build) {
            return state.filter(value -> value != State.PROVEN).isPresent() && this.digest.equals(digest) && this.build.equals(build);
        }

        /** This record for a look tried now. */
        public Attempt tried(String version, String digest, String build, State state) {
            return new Attempt(version, digest, build, Optional.of(state), announced);
        }

        /** This record without its state, so the next start tries afresh. */
        public Attempt cleared() { return new Attempt(version, digest, build, Optional.empty(), announced); }

        /** This record with {@code digest} announced. */
        public Attempt announcing(String digest) { return new Attempt(version, this.digest, build, state, digest); }

        private boolean empty() { return state.isEmpty() && announced.isEmpty(); }
    }

    /**
     * The whole record.
     *
     * @param providers each provider's attempt by id
     */
    public record Record(Map<String, Attempt> providers) {
        /** Nothing recorded. */
        public static final Record EMPTY = new Record(Map.of());

        /** Copies the map. */
        public Record { providers = Map.copyOf(providers); }

        /** {@code id}'s attempt, or {@link Attempt#NONE}. */
        public Attempt of(String id) { return providers.getOrDefault(id, Attempt.NONE); }
    }

    /** How long a write on the EDT waits for the lock. */
    public static final Duration EDT_WAIT = Duration.ofMillis(250);
    /** How long a write off the EDT waits for the lock. */
    public static final Duration WAIT = Duration.ofSeconds(2);
    private static final int MAX_BYTES = 64 * 1024;
    /** File locks are per process; this serializes writers inside one. */
    private static final ReentrantLock IN_PROCESS = new ReentrantLock();
    private static final System.Logger LOG = System.getLogger(AttemptStore.class.getName());
    private final Path file;
    private final Path lock;

    /** The record in {@code home}. */
    public AttemptStore(Path home) {
        file = home.resolve("presentation-attempt.toml");
        lock = home.resolve("presentation-attempt.lock");
    }

    /** The record's file. */
    public Path file() { return file; }

    /** The record now; lock-free, because the file is only replaced atomically. Never throws. */
    public Record read() {
        try { return parse(TomlStateFile.readBounded(file, MAX_BYTES, "Presentation attempts")); }
        catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "The presentation attempt record is unreadable; every look gets a fresh try", unreadable);
            return Record.EMPTY;
        }
    }

    /**
     * Applies {@code edit} to {@code id}'s attempt under the lock; an attempt that ends empty leaves the file. Never throws.
     *
     * @return false when the lock stayed busy for {@code wait} or the file could not be written (both logged)
     */
    public boolean update(String id, UnaryOperator<Attempt> edit, Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        boolean held;
        try { held = IN_PROCESS.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); held = false; }
        if (!held) {
            LOG.log(System.Logger.Level.WARNING, "The presentation attempt lock is busy; this launch goes on without a record");
            return false;
        }
        try {
            Files.createDirectories(lock.toAbsolutePath().getParent());
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock locked = acquire(channel, deadline);
                if (locked == null) {
                    LOG.log(System.Logger.Level.WARNING, "The presentation attempt lock is busy; this launch goes on without a record");
                    return false;
                }
                try (locked) {
                    var next = new TreeMap<>(read().providers());
                    Attempt changed = Objects.requireNonNull(edit.apply(next.getOrDefault(id, Attempt.NONE)));
                    if (changed.empty()) next.remove(id); else next.put(id, changed);
                    TomlStateFile.writeAtomically(file, ".presentation-attempt-", render(next));
                    return true;
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The presentation attempt record could not be written; this launch goes on without it", failure);
            return false;
        } finally { IN_PROCESS.unlock(); }
    }

    private static FileLock acquire(FileChannel channel, long deadline) throws IOException {
        while (true) {
            try {
                FileLock held = channel.tryLock();
                if (held != null) return held;
            } catch (OverlappingFileLockException heldInThisProcess) {
                // Another channel in this JVM holds it: treated like another process.
            }
            if (System.nanoTime() >= deadline) return null;
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
        }
    }

    private static Record parse(Optional<String> text) {
        if (text.isEmpty()) return Record.EMPTY;
        TomlParseResult toml = Toml.parse(text.get());
        if (toml.hasErrors()) throw new IllegalStateException("not valid TOML");
        if (!Long.valueOf(1).equals(toml.get(List.of("version")))) throw new IllegalStateException("not version 1");
        Object providers = toml.get(List.of("providers"));
        if (providers == null) return Record.EMPTY;
        if (!(providers instanceof TomlTable table)) throw new IllegalStateException("providers is not a table");
        var result = new TreeMap<String, Attempt>();
        for (String id : table.keySet()) {
            if (!(table.get(List.of(id)) instanceof TomlTable entry)) throw new IllegalStateException(id + " is not a table");
            String state = text(entry, "state");
            result.put(id, new Attempt(text(entry, "version"), text(entry, "digest"), text(entry, "build"),
                state.isEmpty() ? Optional.empty() : Optional.of(State.valueOf(state.toUpperCase(Locale.ROOT))), text(entry, "announced")));
        }
        return new Record(result);
    }

    private static String text(TomlTable table, String key) {
        Object value = table.get(List.of(key));
        if (value == null) return "";
        if (!(value instanceof String text)) throw new IllegalStateException(key + " is not a string");
        return text;
    }

    private static String render(Map<String, Attempt> providers) {
        var text = new StringBuilder("version = 1\n");
        for (var item : providers.entrySet()) {
            Attempt attempt = item.getValue();
            text.append("\n[providers.").append(TomlLiterals.string(item.getKey())).append("]\n");
            field(text, "version", attempt.version());
            field(text, "digest", attempt.digest());
            field(text, "build", attempt.build());
            attempt.state().ifPresent(state -> field(text, "state", state.name().toLowerCase(Locale.ROOT)));
            field(text, "announced", attempt.announced());
        }
        return text.toString();
    }

    private static void field(StringBuilder text, String key, String value) {
        if (!value.isEmpty()) text.append(key).append(" = ").append(TomlLiterals.string(value)).append('\n');
    }
}
