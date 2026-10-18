package dev.jasper.app.residency;

import dev.jasper.app.platform.OwnerOnlyFiles;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One process per Jasper home (workspace-tabs spec 5.1): an exclusive lock on {@code <home>/daemon/instance.lock},
 * taken before AWT starts and held for the life of the process. The operating system releases it however the process
 * ends, so it is never stale. The process that holds it is the home's owner, and records its process id beside it in
 * {@code instance.pid}.
 */
public final class InstanceLock implements AutoCloseable {
    /** Every lock this process holds, strongly reachable: a collected channel would release its lock. */
    private static final Set<InstanceLock> HELD = ConcurrentHashMap.newKeySet();

    private final Path file;
    private final Path key;
    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(Path file, Path key, FileChannel channel, FileLock lock) {
        this.file = file;
        this.key = key;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Takes the lock, creating the {@code daemon} folder owner-only, and records this process's id beside it. When this
     * process already holds the file, by any spelling of its path, it returns empty without opening it again: closing a
     * second channel on the file would release the process's lock on POSIX systems.
     *
     * @param file the home's lock file
     * @return the lock, held until {@link #close} or the end of the process; empty when another process holds it, or
     *     this one does
     * @throws IOException when the file cannot be opened or locked for any other reason
     */
    public static synchronized Optional<InstanceLock> tryAcquire(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path given = file.toAbsolutePath().normalize();
        Files.createDirectories(given.getParent());
        Path key = given.getParent().toRealPath().resolve(given.getFileName());
        for (InstanceLock held : HELD) if (held.key.equals(key)) return Optional.empty();
        OwnerOnlyFiles.restrict(given.getParent(), "rwx------");
        FileChannel channel = FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        var held = new InstanceLock(given, key, channel, lock);
        HELD.add(held);
        try {
            OwnerOnlyFiles.writeProcessId(pidFile(given));
        } catch (IOException unwritten) {
            // Only a hint for the not-responding message; the lock is what counts.
        }
        return Optional.of(held);
    }

    /**
     * The process id the home's owner recorded beside its lock right after taking it. A hint, not proof: an owner that
     * died leaves its id behind until the next owner replaces it.
     *
     * @param file the home's lock file
     * @return the id, or -1 when there is none or it cannot be read
     */
    public static long ownerPid(Path file) {
        try {
            long pid = Long.parseLong(Files.readString(pidFile(file), StandardCharsets.UTF_8).strip());
            return pid > 0 ? pid : -1;
        } catch (IOException | RuntimeException unreadable) {
            return -1;
        }
    }

    private static Path pidFile(Path file) { return file.resolveSibling("instance.pid"); }

    /**
     * The lock file, as it was asked for.
     *
     * @return its absolute path
     */
    public Path file() { return file; }

    /**
     * Releases the lock and removes the process id beside it: tests and {@code start}'s own rollback; an owner keeps it
     * until its process ends. Idempotent.
     */
    @Override public void close() {
        if (!HELD.remove(this)) return;
        try { Files.deleteIfExists(pidFile(file)); } catch (IOException ignored) { }
        try { lock.release(); } catch (IOException ignored) { }
        try { channel.close(); } catch (IOException ignored) { }
    }
}
