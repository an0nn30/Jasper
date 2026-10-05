package dev.jasper.fileserver.share;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One shared folder; every engine reaches the disk only through it. Client paths are {@code /}-separated (a
 * backslash separates too) and relative to the share. They never leave it, through {@code ..} or a symbolic link,
 * and every write is refused when the share is read-only. Reads follow links that stay inside; writes, deletes and
 * renames act on the named entry itself and never through a final link. Holds no mutable state.
 *
 * <p>Out of scope: a local user who can already write inside the share swapping a checked folder for a link between
 * the check and the open.
 */
public final class ShareRoot {
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final String UPLOAD_PREFIX = ".jasper-upload-";
    private static final SecureRandom UPLOAD_NAMES = new SecureRandom();
    private final Path root;
    private final boolean readOnly;

    public ShareRoot(Path folder, boolean readOnly) throws IOException {
        Path real = folder.toRealPath();
        if (!Files.isDirectory(real)) throw new NotDirectoryException(folder.toString());
        this.root = real;
        this.readOnly = readOnly;
    }

    public Path root() { return root; }
    public boolean readOnly() { return readOnly; }

    /**
     * The names a client path walks through after {@code .} and {@code ..} are applied.
     *
     * @throws PathRefusedException when the path climbs above the share or a name cannot be shared on every platform
     */
    public static List<String> segments(String clientPath) throws PathRefusedException {
        if (clientPath.startsWith("\\\\")) throw new PathRefusedException("Network paths are not allowed");
        var result = new ArrayList<String>();
        for (String segment : clientPath.split("[/\\\\]", -1)) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (result.isEmpty()) throw new PathRefusedException("The path leaves the shared folder");
                result.removeLast();
                continue;
            }
            checkName(segment);
            result.add(segment);
        }
        return List.copyOf(result);
    }

    static void checkName(String name) throws PathRefusedException {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c)) throw new PathRefusedException("The name contains a control character");
            if (c == ':') throw new PathRefusedException("The name contains a colon");
        }
        char last = name.charAt(name.length() - 1);
        if (last == '.' || last == ' ') throw new PathRefusedException("A name cannot end with a dot or a space");
        int dot = name.indexOf('.');
        String stem = (dot < 0 ? name : name.substring(0, dot)).stripTrailing().toUpperCase(Locale.ROOT);
        if (RESERVED.contains(stem)) throw new PathRefusedException(name + " is a reserved device name");
    }

    /** The real path a client path names, following links that stay inside; a missing target's parent must exist inside. */
    public Path resolve(String clientPath) throws IOException {
        List<String> segments = segments(clientPath);
        if (segments.isEmpty()) return root;
        Path candidate = root;
        for (String segment : segments) candidate = child(candidate, segment);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path real;
            try { real = candidate.toRealPath(); }
            catch (NoSuchFileException dangling) { throw new PathRefusedException("The link points nowhere"); }
            return inside(real);
        }
        Path parent;
        try { parent = candidate.getParent().toRealPath(); }
        catch (NoSuchFileException missing) { throw new NoSuchFileException(clientPath); }
        return child(inside(parent), segments.getLast());
    }

    /**
     * The entry a client path names: its folder resolved through links that stay inside, its own name not followed.
     * The root for an empty path. SFTP works on these paths, and every content operation still comes back here.
     */
    public Path locate(String clientPath) throws IOException { return entry(clientPath); }

    /** The client path ({@code "a/b"}, empty for the root) of a path inside the share; refuses any other path. */
    public String clientPath(Path path) throws PathRefusedException {
        Path normal = path.toAbsolutePath().normalize();
        if (!normal.startsWith(root)) throw new PathRefusedException("The path leaves the shared folder");
        if (normal.equals(root)) return "";
        var names = new ArrayList<String>();
        for (Path name : root.relativize(normal)) names.add(name.toString());
        return String.join("/", names);
    }

    /** The entry itself: its parent resolved, its own name not followed. The root for an empty path. */
    private Path entry(String clientPath) throws IOException {
        List<String> segments = segments(clientPath);
        if (segments.isEmpty()) return root;
        Path parent = resolve(String.join("/", segments.subList(0, segments.size() - 1)));
        return child(parent, segments.getLast());
    }

    /** A child path, refusing a name this computer's file system cannot represent. */
    private static Path child(Path parent, String name) throws PathRefusedException {
        try { return parent.resolve(name); }
        catch (InvalidPathException unusable) { throw new PathRefusedException("The name cannot be used on this computer"); }
    }

    private Path inside(Path real) throws PathRefusedException {
        if (!real.startsWith(root)) throw new PathRefusedException("The path leaves the shared folder");
        return real;
    }

    public ShareEntry attributes(String clientPath) throws IOException {
        Path path = resolve(clientPath);
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        List<String> segments = segments(clientPath);
        return entry(segments.isEmpty() ? "" : segments.getLast(), attributes);
    }

    /** A folder's entries, folders first, then by name ignoring case; unshareable names and outward links are left out. */
    public List<ShareEntry> list(String clientPath) throws IOException {
        Path directory = resolve(clientPath);
        var entries = new ArrayList<ShareEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (name.startsWith(UPLOAD_PREFIX)) continue; // an upload in progress
                try { checkName(name); } catch (PathRefusedException unshareable) { continue; }
                Path real;
                try { real = child.toRealPath(); } catch (IOException dangling) { continue; }
                if (!real.startsWith(root)) continue;
                try { entries.add(entry(name, Files.readAttributes(real, BasicFileAttributes.class))); }
                catch (IOException vanished) { /* removed while listing */ }
            }
        }
        entries.sort(Comparator.comparing((ShareEntry e) -> !e.directory())
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)).thenComparing(ShareEntry::name));
        return List.copyOf(entries);
    }

    public SeekableByteChannel openRead(String clientPath) throws IOException {
        Path path = resolve(clientPath);
        if (Files.isDirectory(path)) throw new IsFolderException(clientPath);
        return Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    }

    public OutputStream openWrite(String clientPath, boolean append) throws IOException {
        requireWritable();
        Path path = entry(clientPath);
        if (path.equals(root) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IsFolderException(clientPath);
        return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Opens a file for writing in place at any offset, for SFTP's resumed, appended and read-write opens. The same
     * refusals as {@link #openWrite}; a final link is never written through. {@code options} must contain WRITE; READ,
     * CREATE, CREATE_NEW and TRUNCATE_EXISTING are honoured.
     */
    public FileChannel openWriteChannel(String clientPath, Set<? extends OpenOption> options) throws IOException {
        if (!options.contains(StandardOpenOption.WRITE)) throw new IllegalArgumentException("A write channel needs WRITE");
        requireWritable();
        Path path = entry(clientPath);
        if (path.equals(root) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IsFolderException(clientPath);
        var all = new HashSet<OpenOption>(options);
        all.add(LinkOption.NOFOLLOW_LINKS);
        try { return FileChannel.open(path, all); }
        catch (FileSystemException refused) { throw refused; }
        catch (IOException failure) {
            // FileChannel.open reports a refused final link as a plain IOException on some platforms.
            if (Files.isSymbolicLink(path)) throw new FileSystemException(path.toString(), null, failure.getMessage());
            throw failure;
        }
    }

    /**
     * Starts replacing or creating a file without touching the target until {@link Upload#commit()}: the bytes go to
     * a hidden temporary file in the target's folder, which then moves onto the target in one step. Closing the
     * upload without a commit removes the temporary file and leaves the target as it was. The same refusals as
     * {@link #openWrite}.
     */
    public Upload upload(String clientPath) throws IOException {
        requireWritable();
        Path target = entry(clientPath);
        if (target.equals(root) || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new IsFolderException(clientPath);
        byte[] random = new byte[8];
        UPLOAD_NAMES.nextBytes(random);
        Path temp = target.resolveSibling(UPLOAD_PREFIX + HexFormat.of().formatHex(random) + ".part");
        FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
        return new Upload(temp, target, channel);
    }

    /** A file being received; see {@link ShareRoot#upload}. */
    public static final class Upload implements AutoCloseable {
        private final Path temp, target;
        private final FileChannel channel;
        private final OutputStream out;
        private boolean finished;

        private Upload(Path temp, Path target, FileChannel channel) {
            this.temp = temp; this.target = target; this.channel = channel; this.out = Channels.newOutputStream(channel);
        }

        /** The stream to write the new content to, in order. */
        public OutputStream stream() { return out; }

        /** The temporary file's channel (readable and writable), for writes at any offset; use commit or close, not channel().close(). */
        public FileChannel channel() { return channel; }

        /** Closes the file and moves it onto the target, replacing it. */
        public void commit() throws IOException {
            if (finished) throw new IllegalStateException("The upload is finished");
            finished = true;
            try {
                channel.close();
                keepPermissions();
                try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException | RuntimeException failure) {
                discard();
                throw failure;
            }
        }

        /** A replaced file keeps its permissions; the temporary file was created with the process's defaults. Best effort. */
        private void keepPermissions() {
            try {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS));
            } catch (UnsupportedOperationException | IOException notPosix) {
                // Windows, or a file system without POSIX permissions: the new file keeps its defaults.
            }
        }

        /** Abandons the upload unless it was committed. */
        @Override public void close() {
            if (finished) return;
            finished = true;
            discard();
        }

        private void discard() {
            try { channel.close(); } catch (IOException ignored) { /* the file is removed next */ }
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { /* nothing more to do */ }
        }
    }

    public void mkdir(String clientPath) throws IOException {
        requireWritable();
        Files.createDirectory(entry(clientPath));
    }

    public void delete(String clientPath) throws IOException {
        requireWritable();
        Path path = entry(clientPath);
        if (path.equals(root)) throw new PathRefusedException("The shared folder itself cannot be deleted");
        Files.delete(path);
    }

    public void rename(String from, String to) throws IOException { rename(from, to, false); }

    /** Renames an entry; {@code replace} replaces an existing target, as SFTP's posix-rename asks. */
    public void rename(String from, String to, boolean replace) throws IOException {
        requireWritable();
        Path source = entry(from), target = entry(to);
        if (source.equals(root) || target.equals(root)) throw new PathRefusedException("The shared folder itself cannot be renamed");
        if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        else Files.move(source, target);
    }

    private void requireWritable() throws ReadOnlyShareException {
        if (readOnly) throw new ReadOnlyShareException();
    }

    private static ShareEntry entry(String name, BasicFileAttributes attributes) {
        return new ShareEntry(name, attributes.isDirectory(), attributes.isDirectory() ? 0 : attributes.size(),
            attributes.lastModifiedTime().toInstant());
    }
}
