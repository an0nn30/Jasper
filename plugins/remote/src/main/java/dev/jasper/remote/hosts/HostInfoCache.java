package dev.jasper.remote.hosts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Optional local host facts. Disk methods run on a worker; UI lookups read a synchronized snapshot. */
public final class HostInfoCache {
    private final Path file;
    private final Properties entries = new Properties();
    private final Object disk = new Object();
    public HostInfoCache(Path file) { this.file = file; }
    public void load() {
        synchronized (disk) {
            var loaded = new Properties();
            try (var in = Files.newInputStream(file)) { loaded.load(in); }
            catch (IOException | IllegalArgumentException unavailable) { loaded.clear(); }
            synchronized (entries) { entries.clear(); entries.putAll(loaded); }
        }
    }
    public HostInfo get(RemoteHost host) {
        synchronized (entries) {
            String key = host.id().toString();
            if (!endpoint(host).equals(entries.getProperty(key + ".endpoint"))) return HostInfo.EMPTY;
            return new HostInfo(entries.getProperty(key + ".os", ""), entries.getProperty(key + ".address", ""));
        }
    }
    public void put(RemoteHost host, HostInfo info) throws IOException {
        synchronized (disk) {
            var snapshot = new Properties();
            synchronized (entries) {
                String key = host.id().toString();
                HostInfo prior = get(host);
                entries.setProperty(key + ".endpoint", endpoint(host));
                entries.setProperty(key + ".os", info.os().isEmpty() ? prior.os() : info.os());
                entries.setProperty(key + ".address", info.address().isEmpty() ? prior.address() : info.address());
                snapshot.putAll(entries);
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(temporary)) { snapshot.store(out, "Jasper Remote cached host information; safe to delete."); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        }
    }
    private static String endpoint(RemoteHost host) { return host.hostname() + ":" + host.port() + "/" + host.jump().map(Object::toString).orElse(""); }
}
