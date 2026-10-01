package dev.jasper.fileserver.share;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
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
            if (c < 0x20 || c == 0x7f) throw new PathRefusedException("The name contains a control character");
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
        for (String segment : segments) candidate = candidate.resolve(segment);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path real;
            try { real = candidate.toRealPath(); }
            catch (NoSuchFileException dangling) { throw new PathRefusedException("The link points nowhere"); }
            return inside(real);
        }
        Path parent;
        try { parent = candidate.getParent().toRealPath(); }
        catch (NoSuchFileException missing) { throw new NoSuchFileException(clientPath); }
        return inside(parent).resolve(segments.getLast());
    }

    /** The entry itself: its parent resolved, its own name not followed. The root for an empty path. */
    private Path entry(String clientPath) throws IOException {
        List<String> segments = segments(clientPath);
        if (segments.isEmpty()) return root;
        Path parent = resolve(String.join("/", segments.subList(0, segments.size() - 1)));
        return parent.resolve(segments.getLast());
    }

    private Path inside(Path real) throws PathRefusedException {
        if (!real.startsWith(root)) throw new PathRefusedException("The path leaves the shared folder");
        return real;
    }

    public ShareEntry attributes(String clientPath) throws IOException {
        Path path = resolve(clientPath);
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return entry(path.equals(root) ? "" : path.getFileName().toString(), attributes);
    }

    /** A folder's entries, folders first, then by name ignoring case; unshareable names and outward links are left out. */
    public List<ShareEntry> list(String clientPath) throws IOException {
        Path directory = resolve(clientPath);
        var entries = new ArrayList<ShareEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
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

    public void rename(String from, String to) throws IOException {
        requireWritable();
        Path source = entry(from), target = entry(to);
        if (source.equals(root) || target.equals(root)) throw new PathRefusedException("The shared folder itself cannot be renamed");
        Files.move(source, target);
    }

    private void requireWritable() throws ReadOnlyShareException {
        if (readOnly) throw new ReadOnlyShareException();
    }

    private static ShareEntry entry(String name, BasicFileAttributes attributes) {
        return new ShareEntry(name, attributes.isDirectory(), attributes.isDirectory() ? 0 : attributes.size(),
            attributes.lastModifiedTime().toInstant());
    }
}
