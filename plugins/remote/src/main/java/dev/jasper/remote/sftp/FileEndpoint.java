package dev.jasper.remote.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/** Worker-confined file operations; abort is the sole cross-thread operation. Two implementations: local and SFTP. */
public interface FileEndpoint extends AutoCloseable {
    String id();
    /** Joins one untrusted directory-entry name without allowing path traversal. */
    default String child(String directory, String name) throws IOException { return FilePaths.child(directory, name); }
    /** Parent in this endpoint's path syntax. */
    default String parent(String path) throws IOException { return FilePaths.parent(path); }
    FileEntry stat(String path) throws IOException;
    void list(String directory, Consumer<FileEntry> visitor) throws IOException;
    InputStream read(String path, long offset) throws IOException;
    WriteHandle write(String path, long offset, boolean createExclusive) throws IOException;
    void truncate(String path, long size) throws IOException;
    void mkdir(String path) throws IOException;
    void symlink(String path, String target) throws IOException;
    void remove(String path, boolean directory) throws IOException;
    void publish(String temporary, String target, boolean replace) throws IOException;
    void metadata(String path, long modifiedMillis, int ordinaryPermissions) throws IOException;
    String canonical(String path) throws IOException;
    String home() throws IOException;
    void abort();
    @Override void close() throws IOException;
}
