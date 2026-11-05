package dev.jasper.browser.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Where sqlite-jdbc extracts its native library. Each class loader's first connection lists that folder, writes a fresh
 * copy of the library there and loads it. In the shared temporary folder, which can hold hundreds of thousands of
 * entries, loading the copy took about 2 s on macOS and held the process's dynamic-linker lock the whole time, so the
 * first window's own native libraries waited for it; in a folder of Jasper's own it takes about 0.2 s.
 *
 * <p>A copy of Remote's {@code dev.jasper.remote.transfer.store.SqliteNatives}: plugins share no code, and each loads its
 * own sqlite-jdbc. This copy gives each user a folder of their own on Linux, whose {@code /tmp} every user shares, and
 * lets only its owner use it.
 */
final class SqliteNatives {
    private static final String PROPERTY = "org.sqlite.tmpdir";

    private SqliteNatives() { }

    /** {@code jasper-sqlite} in {@code java.io.tmpdir}, or on Linux {@code jasper-sqlite-<user name>}. */
    static Path folder() {
        return folder(System.getProperty("os.name", ""), Path.of(System.getProperty("java.io.tmpdir")), System.getProperty("user.name", ""));
    }

    /** The folder for a system called {@code osName}, whose temporary folder is {@code temp}, and the user {@code user}. */
    static Path folder(String osName, Path temp, String user) {
        if (!osName.startsWith("Linux")) return temp.resolve("jasper-sqlite");
        return temp.resolve("jasper-sqlite-" + user.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    /**
     * Points sqlite-jdbc at {@code folder}, made if it is missing and, where the file system has POSIX permissions, open
     * to its owner alone, unless another folder was chosen with the {@code org.sqlite.tmpdir} system property. Before
     * the plugin's first connection.
     *
     * @param folder the folder the library is extracted into
     * @throws IOException if the folder cannot be made, or is another user's
     */
    static void extractInto(Path folder) throws IOException {
        String chosen = System.getProperty(PROPERTY);
        if (chosen != null && !chosen.equals(folder.toString())) return;
        Files.createDirectories(folder);
        // Only the folder's owner may change its permissions, so another user's folder fails here and is not used.
        if (Files.getFileStore(folder).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
        System.setProperty(PROPERTY, folder.toString());
    }
}
