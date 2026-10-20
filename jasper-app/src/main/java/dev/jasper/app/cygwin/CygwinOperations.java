package dev.jasper.app.cygwin;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Cross-process launch reservations; management retains the exclusive lock for its whole lifetime. */
public final class CygwinOperations implements AutoCloseable {
    private final CygwinLayout layout;
    private final Path lease;
    private final FileChannel channel;
    private final FileLock lock;
    private CygwinOperations(CygwinLayout layout, Path lease, FileChannel channel, FileLock lock) {
        this.layout = layout; this.lease = lease; this.channel = channel; this.lock = lock;
    }
    private static CygwinOperations lock(CygwinLayout layout) throws IOException {
        layout.requireOwned(); Path path = layout.base().resolve("operation.lock"); CygwinLayout.plainAncestry(path);
        var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            var lock = channel.tryLock();
            if (lock == null) throw new IOException("Cygwin package management is active; wait for it to finish");
            return new CygwinOperations(layout, null, channel, lock);
        } catch (IOException | OverlappingFileLockException failure) {
            channel.close(); throw new IOException("Cygwin operation is already active", failure);
        }
    }
    public static CygwinOperations acquireInstallation(CygwinLayout layout) throws IOException { return lock(layout); }
    @SuppressWarnings("try")
    public static CygwinOperations registerLaunch(CygwinLayout layout) throws IOException {
        try (var guard = lock(layout)) {
            supervisors(layout, true);
            Path lease = layout.base().resolve("leases/" + UUID.randomUUID() + ".json");
            ProcessHandle owner = ProcessHandle.current();
            Instant start = owner.info().startInstant().orElseThrow(() -> new IOException("Cannot identify launch owner"));
            CygwinLayout.write(lease, Map.of("pid", owner.pid(), "start", start.toString()));
            return new CygwinOperations(layout, lease, null, null);
        }
    }
    public static CygwinOperations acquireManagement(CygwinLayout layout) throws IOException {
        var operation = lock(layout);
        try {
            try (var leases = Files.list(layout.base().resolve("leases"))) {
                for (Path lease : leases.toList()) {
                    var data = CygwinLayout.read(lease);
                    long pid = CygwinJson.number(data, "pid");
                    Instant start = Instant.parse(CygwinJson.string(data, "start"));
                    var owner = ProcessHandle.of(pid);
                    if (owner.isPresent() && owner.get().isAlive()) {
                        var actual = owner.get().info().startInstant();
                        if (actual.isEmpty() || actual.get().equals(start)) throw new IOException("Close Jasper Cygwin sessions before managing packages");
                    }
                    Files.delete(lease);
                }
            }
            CygwinProcesses.assertIdle(layout);
            supervisors(layout, false);
            return operation;
        } catch (Exception failure) {
            operation.close();
            if (failure instanceof IOException io) throw io;
            throw new IOException("Cannot inspect Cygwin launch reservations", failure);
        }
    }
    private static void supervisors(CygwinLayout layout, boolean launch) throws IOException {
        Path directory = layout.base().resolve("operations"); CygwinLayout.plainAncestry(directory);
        if (!Files.isDirectory(directory)) return;
        try (var operations = Files.list(directory)) {
            for (Path operation : operations.toList()) {
                if (!Files.exists(operation.resolve("request.json")) || Files.exists(operation.resolve("result.json"))) continue;
                var request = CygwinLayout.read(operation.resolve("request.json"));
                if (!CygwinJson.string(request, "root").equals(layout.base().toString())) throw new IOException("Unrecognized Cygwin operation");
                Path ownerFile = operation.resolve("owner.json");
                if (!Files.isRegularFile(ownerFile)) {
                    boolean starterAlive = false;
                    if (request.containsKey("starterPid")) {
                        long starterPid = CygwinJson.number(request, "starterPid");
                        Instant starterStart = Instant.parse(CygwinJson.string(request, "starterStart"));
                        starterAlive = alive(starterPid, starterStart);
                    }
                    if (starterAlive || launch) throw new IOException("Cygwin manager is starting or was interrupted; reopen Manage Cygwin packages");
                    CygwinLayout.write(operation.resolve("result.json"), Map.of("failed", true, "message", "Package-manager startup was interrupted"));
                    continue;
                }
                var owner = CygwinLayout.read(ownerFile);
                long pid = CygwinJson.number(owner, "pid");
                Instant start = Instant.parse(CygwinJson.string(owner, "start"));
                if (!launch && pid == ProcessHandle.current().pid() && ProcessHandle.current().info().startInstant().map(start::equals).orElse(false)) continue;
                var process = ProcessHandle.of(pid);
                boolean live = process.isPresent() && process.get().isAlive() && process.get().info().startInstant().map(start::equals).orElse(true);
                if (live || launch) throw new IOException(live ? "Cygwin package management is active" : "Cygwin management was interrupted; reopen Manage Cygwin packages before starting Bash");
                CygwinLayout.write(operation.resolve("result.json"), Map.of("failed", true, "message", "Previous Cygwin management was interrupted"));
            }
        }
    }
    static boolean alive(long pid, Instant start) {
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
            .map(process -> process.info().startInstant().map(start::equals).orElse(true)).orElse(false);
    }
    @Override public void close() throws IOException {
        if (lease != null) { layout.requireOwned(); Files.deleteIfExists(lease); }
        if (lock != null && lock.isValid()) lock.release();
        if (channel != null && channel.isOpen()) channel.close();
    }
}
