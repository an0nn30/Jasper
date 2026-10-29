package dev.jasper.sdk.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * A properties file that keeps one {@link ColumnState} per key, for a plugin whose tables remember their sort and
 * column widths across runs.
 *
 * @since 0.25.0
 */
public final class ColumnStateFile {
    private final Path file;

    /**
     * A store over a file; nothing is read or created until {@link #get} or {@link #put}.
     *
     * @param file the properties file; its folder is created by {@link #put}
     */
    public ColumnStateFile(Path file) { this.file = file; }

    /**
     * The state stored under a key.
     *
     * @param key the key, for example a table's id
     * @return the state; {@link ColumnState#NONE} when the file or key is missing or unreadable
     */
    public synchronized ColumnState get(String key) {
        try { return ColumnState.decode(load().getProperty(key, "")); }
        catch (IOException unreadable) { return ColumnState.NONE; }
    }

    /**
     * Stores a state under a key and keeps the file's other keys. The file is written to a temporary file and moved
     * into place; a missing file starts empty, and a file that cannot be read is left as it is.
     *
     * @param key the key, for example a table's id
     * @param state the state to keep
     * @throws IOException when the file exists but cannot be read, or cannot be written
     */
    public synchronized void put(String key, ColumnState state) throws IOException {
        var properties = load();
        properties.setProperty(key, state.encode());
        var folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        var temp = Files.createTempFile(folder, file.getFileName().toString(), ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { properties.store(out, null); }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    /** The file's properties; none when it is missing. */
    private Properties load() throws IOException {
        var properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) { properties.load(in); }
        catch (NoSuchFileException missing) { return new Properties(); }
        catch (IllegalArgumentException malformed) { throw new IOException("Malformed column state file " + file, malformed); }
        return properties;
    }
}
