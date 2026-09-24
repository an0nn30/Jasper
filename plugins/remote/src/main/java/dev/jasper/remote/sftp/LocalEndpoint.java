package dev.jasper.remote.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Local adapter. Uses handle-relative no-follow opens/deletes on supporting providers. */
public final class LocalEndpoint implements FileEndpoint {
    private final Set<FileChannel> handles = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    @Override public String id() { return "local"; }
    @Override public String child(String directory, String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf(0) >= 0)
            throw new IOException("Invalid directory entry name");
        try {
            Path part = Path.of(name);
            if (part.isAbsolute() || part.getNameCount() != 1 || part.getRoot() != null) throw new IOException("Filename cannot be represented locally: " + name);
            return path(directory).resolve(part).toString();
        } catch (InvalidPathException bad) { throw new IOException("Filename cannot be represented locally: " + name, bad); }
    }
    @Override public String parent(String text) throws IOException { Path file = path(text); return (file.getParent() == null ? file : file.getParent()).toString(); }
    private void check() throws IOException { if (closed || Thread.currentThread().isInterrupted()) throw new IOException("File operation cancelled"); }
    private Path path(String text) throws IOException {
        check(); Path path;
        try { path = Path.of(text); } catch (InvalidPathException bad) { throw new IOException("Invalid local filename", bad); }
        if (!path.isAbsolute()) throw new IOException("An absolute local path is required");
        return path.normalize();
    }
    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static void directory(Path path) throws IOException {
        if (!attributes(path).isDirectory()) throw new IOException("Directory changed or is a symbolic link: " + path);
    }
    private static void ancestors(Path path) throws IOException {
        Path current = path.getRoot();
        directory(current);
        for (Path part : path) { current = current.resolve(part); directory(current); }
    }
    private final class Parent implements AutoCloseable {
        final Path file, directory, name;
        final SecureDirectoryStream<Path> secure;
        Parent(Path file) throws IOException {
            this.file = file; directory = file.getParent(); name = file.getFileName();
            if (directory == null) throw new IOException("Cannot mutate filesystem root");
            DirectoryStream<Path> stream = Files.newDirectoryStream(file.getRoot());
            SecureDirectoryStream<Path> found = null;
            try {
                if (stream instanceof SecureDirectoryStream<Path> current) {
                    found = current;
                    for (Path part : directory) {
                        var next = found.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
                        found.close(); found = next;
                    }
                } else { stream.close(); ancestors(directory); }
                secure = found;
            } catch (IOException | RuntimeException failure) { if (found != null) found.close(); else stream.close(); throw failure; }
        }
        FileChannel open(Set<OpenOption> options) throws IOException {
            var channel = secure == null ? Files.newByteChannel(file, options) : secure.newByteChannel(name, options);
            if (!(channel instanceof FileChannel result)) { channel.close(); throw new IOException("Provider cannot force transfer checkpoints"); }
            handles.add(result); if (closed) { result.close(); throw new IOException("Cancelled"); } return result;
        }
        @Override public void close() throws IOException { if (secure != null) secure.close(); }
    }
    @Override public FileEntry stat(String text) throws IOException {
        Path file = path(text); if (file.getParent() != null) ancestors(file.getParent());
        var a = attributes(file);
        int permissions = -1;
        try { permissions = permissions(Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).permissions()); }
        catch (UnsupportedOperationException ignored) { /* reported as unavailable */ }
        return new FileEntry(file.getFileName() == null ? "/" : file.getFileName().toString(),
            a.isSymbolicLink() ? FileEntry.Kind.LINK : a.isDirectory() ? FileEntry.Kind.DIRECTORY : a.isRegularFile() ? FileEntry.Kind.FILE : FileEntry.Kind.SPECIAL,
            a.size(), a.lastModifiedTime().toMillis(), permissions, a.isSymbolicLink() ? Files.readSymbolicLink(file).toString() : "",
            a.fileKey() == null ? "" : a.fileKey().toString());
    }
    @Override public void list(String text, Consumer<FileEntry> visitor) throws IOException {
        Path dir = path(text); ancestors(dir);
        try (var listing = Files.newDirectoryStream(dir)) {
            for (Path child : listing) { check(); visitor.accept(stat(child.toString())); }
        }
    }
    @Override public InputStream read(String text, long offset) throws IOException {
        if (offset < 0) throw new IOException("Negative offset");
        Path file = path(text);
        try (var parent = new Parent(file)) {
            var channel = parent.open(Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            try { channel.position(offset); }
            catch (IOException failure) { handles.remove(channel); channel.close(); throw failure; }
            return new java.io.FilterInputStream(Channels.newInputStream(channel)) {
                @Override public void close() throws IOException { try { super.close(); } finally { handles.remove(channel); } }
            };
        }
    }
    @Override public WriteHandle write(String text, long offset, boolean exclusive) throws IOException {
        if (offset < 0) throw new IOException("Negative offset");
        Path file = path(text);
        try (var parent = new Parent(file)) {
            var options = new HashSet<OpenOption>(Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
            if (exclusive) options.add(StandardOpenOption.CREATE_NEW);
            var channel = parent.open(options);
            try { channel.position(offset); }
            catch (IOException failure) { handles.remove(channel); channel.close(); throw failure; }
            return new WriteHandle() {
                @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}); }
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    check(); var buffer = ByteBuffer.wrap(b, off, len);
                    while (buffer.hasRemaining()) { check(); channel.write(buffer); }
                }
                @Override public long checkpoint() throws IOException { check(); channel.force(true); return channel.position(); }
                @Override public void flush() throws IOException { checkpoint(); }
                @Override public void abort() { try { channel.close(); } catch (IOException ignored) {} finally { handles.remove(channel); } }
                @Override public void close() throws IOException {
                    try { if (channel.isOpen() && !closed) channel.force(true); } finally { channel.close(); handles.remove(channel); }
                }
            };
        }
    }
    @Override public void truncate(String text, long size) throws IOException {
        if (size < 0) throw new IOException("Negative size");
        try (var parent = new Parent(path(text)); var channel = parent.open(Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
            try { channel.truncate(size); channel.force(true); } finally { handles.remove(channel); }
        }
    }
    @Override public void mkdir(String text) throws IOException { Path file = path(text); ancestors(file.getParent()); Files.createDirectory(file); }
    @Override public void symlink(String text, String target) throws IOException { Path file = path(text); ancestors(file.getParent()); Files.createSymbolicLink(file, Path.of(target)); }
    @Override public void remove(String text, boolean directory) throws IOException {
        try (var parent = new Parent(path(text))) {
            if (parent.secure == null) Files.delete(parent.file);
            else if (directory) parent.secure.deleteDirectory(parent.name); else parent.secure.deleteFile(parent.name);
        }
    }
    @Override public void publish(String temporary, String target, boolean replace) throws IOException {
        try (var from = new Parent(path(temporary)); var to = new Parent(path(target))) {
            if (replace) {
                if (from.secure != null && to.secure != null) from.secure.move(from.name, to.secure, to.name);
                else Files.move(from.file, to.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                ancestors(from.directory); ancestors(to.directory);
                // Some providers (including macOS) follow the source link in createLink.
                // Publish a symbolic link with exclusive creation of the exact link text instead.
                var source = attributes(from.file);
                if (source.isSymbolicLink()) Files.createSymbolicLink(to.file, Files.readSymbolicLink(from.file));
                else if (source.isRegularFile()) Files.createLink(to.file, from.file);
                else throw new IOException("Only regular files and links can be published");
                if (from.secure != null) from.secure.deleteFile(from.name); else Files.delete(from.file);
            }
        }
    }
    @Override public void metadata(String text, long modified, int mode) throws IOException {
        try (var parent = new Parent(path(text))) {
            var view = parent.secure == null ? Files.getFileAttributeView(parent.file, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                : parent.secure.getFileAttributeView(parent.name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view.readAttributes().isSymbolicLink()) throw new IOException("Link metadata is unsupported");
            if (modified >= 0) view.setTimes(FileTime.fromMillis(modified), null, null);
            if (mode >= 0) {
                var posix = parent.secure == null ? Files.getFileAttributeView(parent.file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                    : parent.secure.getFileAttributeView(parent.name, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (posix == null) throw new IOException("POSIX permissions unsupported by destination");
                var bits = java.util.EnumSet.noneOf(PosixFilePermission.class);
                for (var bit : PosixFilePermission.values()) if ((mode & (1 << (8 - bit.ordinal()))) != 0) bits.add(bit);
                posix.setPermissions(bits);
            }
        }
    }
    private static int permissions(Set<PosixFilePermission> bits) { int result = 0; for (var bit : bits) result |= 1 << (8 - bit.ordinal()); return result; }
    @Override public String canonical(String text) throws IOException {
        Path file = path(text); return (file.getParent() == null ? file.toRealPath() : file.getParent().toRealPath().resolve(file.getFileName())).toString();
    }
    @Override public String resolveDirectory(String text) throws IOException {
        check();String resolved=path(text).toRealPath().toString();
        if(stat(resolved).kind()!=FileEntry.Kind.DIRECTORY) throw new IOException("Path is not a directory");
        return resolved;
    }
    @Override public String home() throws IOException { check(); return Path.of(System.getProperty("user.home")).toRealPath().toString(); }
    @Override public void abort() {
        closed = true;
        for (var handle : handles) try { handle.close(); } catch (IOException ignored) {}
        handles.clear();
    }
    @Override public void close() { abort(); }
}
