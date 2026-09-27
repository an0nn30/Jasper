package dev.jasper.editor.local;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Reading, revisions and the write contract (spec 4.4) for files on this machine. Blocking: call off the UI thread. */
public final class LocalFiles {
    static final int MAX_LINKS = 40;

    private LocalFiles() { }

    /**
     * @param path a path that may be a symbolic link
     * @return the file the links name, which need not exist
     * @throws IOException after more than 40 links, or when a link cannot be read
     */
    public static Path resolve(Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        for (int hops = 0; Files.isSymbolicLink(current); hops++) {
            if (hops == MAX_LINKS) throw new FileSystemException(path.toString(), null, "Too many levels of symbolic links");
            Path target = Files.readSymbolicLink(current);
            Path parent = current.getParent();
            current = (parent == null ? target : parent.resolve(target)).toAbsolutePath().normalize();
        }
        return current;
    }

    /**
     * @param path a file
     * @param limitBytes the largest size accepted
     * @return its content, refused before reading when it is larger than the limit
     * @throws IOException for a folder, a missing or unreadable file, or {@link TooLargeException}
     */
    public static Content read(Path path, long limitBytes) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (attributes.isDirectory()) throw new FileSystemException(path.toString(), null, "Is a folder");
        if (attributes.size() > limitBytes) throw new TooLargeException(attributes.size(), limitBytes);
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > limitBytes) throw new TooLargeException(bytes.length, limitBytes);
        return new Content(bytes, new Revision(attributes.lastModifiedTime().toMillis(), bytes.length));
    }

    /**
     * @param path a file, following links
     * @return its revision, or empty when it does not exist
     * @throws IOException when it cannot be read
     */
    public static Optional<Revision> revision(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return Optional.of(new Revision(attributes.lastModifiedTime().toMillis(), attributes.size()));
        } catch (NoSuchFileException gone) {
            return Optional.empty();
        }
    }

    /**
     * Replaces a file's content following the write contract.
     *
     * @param path the file, possibly a symbolic link
     * @param content the new bytes
     * @return the new revision
     * @throws IOException when neither a safe write nor a write in place succeeds
     */
    public static Revision write(Path path, byte[] content) throws IOException {
        Path target = resolve(path);
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists && Files.isDirectory(target)) throw new FileSystemException(target.toString(), null, "Is a folder");
        if (exists && links(target) > 1) return inPlace(target, content);
        Path parent = target.getParent();
        if (parent == null) throw new FileSystemException(target.toString(), null, "Has no folder");
        Path sibling;
        try {
            sibling = Files.createFile(parent.resolve("." + target.getFileName() + ".jasper-"
                + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp"));
        } catch (IOException cannotCreate) {
            if (!exists) throw cannotCreate;
            return inPlace(target, content);
        }
        try {
            try (FileChannel channel = FileChannel.open(sibling, StandardOpenOption.WRITE)) {
                writeAll(channel, content);
                channel.force(true);
            }
            if (exists) {
                if (!sameOwner(sibling, target)) {
                    Files.deleteIfExists(sibling);
                    return inPlace(target, content);
                }
                copyPermissions(target, sibling);
            }
            Files.move(sibling, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(sibling); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            throw failure;
        }
        return revision(target).orElseThrow(() -> new NoSuchFileException(target.toString()));
    }

    /** Keeps the owner, group, hard links and extended attributes; not atomic. */
    private static Revision inPlace(Path target, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            writeAll(channel, content);
            channel.truncate(content.length);
            channel.force(true);
        }
        return revision(target).orElseThrow(() -> new NoSuchFileException(target.toString()));
    }

    private static void writeAll(FileChannel channel, byte[] content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) channel.write(buffer, buffer.position());
    }

    private static int links(Path path) {
        try { return ((Number) Files.getAttribute(path, "unix:nlink")).intValue(); }
        catch (UnsupportedOperationException | IllegalArgumentException | IOException unknown) { return 1; }
    }

    private static boolean sameOwner(Path first, Path second) {
        try {
            return Files.getAttribute(first, "unix:uid").equals(Files.getAttribute(second, "unix:uid"))
                && Files.getAttribute(first, "unix:gid").equals(Files.getAttribute(second, "unix:gid"));
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException unknown) {
            return true;
        }
    }

    private static void copyPermissions(Path from, Path to) throws IOException {
        if (Files.getFileAttributeView(from, PosixFileAttributeView.class) == null) return;
        Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
    }
}
