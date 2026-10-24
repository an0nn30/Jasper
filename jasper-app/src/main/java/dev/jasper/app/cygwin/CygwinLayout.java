package dev.jasper.app.cygwin;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** Stable user-owned environment, independent of app version and configuration home. */
public record CygwinLayout(Path base) {
    public CygwinLayout { base = base.toAbsolutePath().normalize(); }
    public static CygwinLayout at(Path path) { return new CygwinLayout(path); }
    public static boolean windows() { return System.getProperty("os.name", "").startsWith("Windows"); }
    public static CygwinLayout system() {
        if (!windows()) throw new IllegalStateException("Cygwin requires Windows");
        return at(Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData)).resolve("Jasper/cygwin"));
    }
    public Path root() { return base.resolve("root"); }
    public Path manager() { return base.resolve("manager"); }
    public Path logs() { return base.resolve("logs"); }
    public static String identity() {
        return windows() ? Advapi32Util.getAccountByName(Advapi32Util.getUserName()).sidString : System.getProperty("user.name");
    }
    public static void plainAncestry(Path path) throws IOException {
        for (Path p = path.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) continue;
            // macOS /var is a system link; fixture roots must be canonicalized by the caller there.
            var attr = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attr.isSymbolicLink() || attr.isOther()) throw new IOException("Unexpected link in Cygwin control path: " + p);
            if (windows()) {
                int flags = Kernel32.INSTANCE.GetFileAttributes(p.toString());
                if (flags == WinBase.INVALID_FILE_ATTRIBUTES || (flags & WinNT.FILE_ATTRIBUTE_REPARSE_POINT) != 0)
                    throw new IOException("Cannot safely inspect Cygwin control path: " + p);
            }
        }
    }
    /** Publishes complete control records; a sibling lock serializes first-time installers. */
    public void initialize() throws IOException {
        plainAncestry(base);
        Files.createDirectories(base.getParent());
        Path initializationLock = base.resolveSibling("." + base.getFileName() + ".initialization.lock");
        plainAncestry(initializationLock);
        try (var channel = java.nio.channels.FileChannel.open(initializationLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock guard;
            try { guard = channel.tryLock(); }
            catch (java.nio.channels.OverlappingFileLockException busy) { throw new IOException("Cygwin initialization is active", busy); }
            if (guard == null) throw new IOException("Cygwin initialization is active");
            try (guard) {
                if (Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
                    requireOwned();
                    if (Files.exists(base.resolve("state.json"), LinkOption.NOFOLLOW_LINKS)) return;
                    if (!emptyInitialization()) throw new IOException("Incomplete Cygwin control records with existing user data");
                    initializeContents(base); // Recover only an owned empty layout from an interrupted older initialization.
                    return;
                }
                Path staging = Files.createTempDirectory(base.getParent(), ".cygwin-initializing-");
                try {
                    write(staging.resolve("owner.json"), Map.of("layout", 1, "product", "Jasper Cygwin", "user", identity()));
                    initializeContents(staging);
                    // Same-volume rename without REPLACE_EXISTING: never adopt a directory created by somebody else.
                    Files.move(staging, base);
                } finally {
                    if (Files.exists(staging)) try (var paths = Files.walk(staging)) {
                        for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                    }
                }
            }
        }
    }
    private static void initializeContents(Path directory) throws IOException {
        for (String child : new String[]{"root", "manager", "logs", "cache", "leases"}) Files.createDirectories(directory.resolve(child));
        write(directory.resolve("state.json"), Map.of("phase", "INITIALIZING", "seed", "unselected"));
    }
    boolean emptyInitialization() throws IOException {
        for (String child : new String[]{"root", "manager", "logs", "cache", "leases"}) {
            Path path = base.resolve(child); plainAncestry(path);
            if (Files.exists(path)) {
                if (!Files.isDirectory(path)) return false;
                try (var children = Files.list(path)) { if (children.findAny().isPresent()) return false; }
            }
        }
        return true;
    }
    public static Map<String, Object> read(Path path) throws IOException {
        plainAncestry(path);
        if (Files.size(path) > 8 * 1024 * 1024) throw new IOException("Oversized Cygwin record");
        return CygwinJson.object(CygwinJson.parse(Files.readString(path)));
    }
    public static void write(Path path, Map<String, ?> value) throws IOException {
        plainAncestry(path);
        Path temporary = Files.createTempFile(path.getParent(), ".record-", ".tmp");
        try {
            Files.writeString(temporary, CygwinJson.write(value));
            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public void requireOwned() throws IOException {
        plainAncestry(base);
        var owner = read(base.resolve("owner.json"));
        if (CygwinJson.number(owner, "layout") != 1 || !CygwinJson.string(owner, "product").equals("Jasper Cygwin")
                || !CygwinJson.string(owner, "user").equals(identity())) throw new IOException("Foreign Cygwin environment: " + base);
        for (String name : new String[]{"root", "manager", "logs", "cache", "leases"}) plainAncestry(base.resolve(name));
    }
}
