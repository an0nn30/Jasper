package dev.jasper.remote.transfer.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where sqlite-jdbc extracts its native library. Each class loader's first connection lists that folder, writes a fresh
 * copy of the library there and loads it. In the shared temporary folder, which can hold hundreds of thousands of
 * entries, loading the copy took about 2 s on macOS and held the process's dynamic-linker lock the whole time, so the
 * first window's own native libraries waited for it; in a folder of Jasper's own it takes about 0.2 s.
 */
public final class SqliteNatives {
    private static final String PROPERTY = "org.sqlite.tmpdir";

    private SqliteNatives() { }

    /** {@code jasper-sqlite} in {@code java.io.tmpdir}. */
    public static Path folder() {
        return Path.of(System.getProperty("java.io.tmpdir"), "jasper-sqlite");
    }

    /**
     * Points sqlite-jdbc at {@code folder}, made if it is missing, unless another folder was chosen with the
     * {@code org.sqlite.tmpdir} system property. Before the plugin's first connection.
     *
     * @param folder the folder the library is extracted into
     * @throws IOException if the folder cannot be made
     */
    public static void extractInto(Path folder) throws IOException {
        String chosen = System.getProperty(PROPERTY);
        if (chosen != null && !chosen.equals(folder.toString())) return;
        Files.createDirectories(folder);
        System.setProperty(PROPERTY, folder.toString());
    }
}
