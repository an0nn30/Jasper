package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.ClientPaths;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.File;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;

/**
 * The SFTP jail.
 *
 * <p>sshd resolves every remote path through {@link #resolveLocalFilePath} and then calls {@code Files} on the
 * result directly. So the result is always inside the share: the client path with {@code ..} stopped at the root,
 * located through {@link ShareRoot#locate}, which follows the folder's links only while they stay inside and never
 * follows the entry's own name.
 *
 * <p>Two paths reach sshd's own {@code Files} calls without that method. REALPATH's version 6 extra parts are joined
 * with {@code Path.resolve}, and its existence checks follow the entry's own name. Both pass through
 * {@link #resolveFileAccessLinkOptions} first, which refuses a path outside the share and, where links are followed,
 * an outward link. The engine also makes sshd fail a request whose attributes the jail refused
 * ({@code UnsupportedAttributePolicy.ThrowException}). sshd's default would answer with attributes it reads itself,
 * through the link.
 *
 * <p>Every content operation goes back through {@link ShareRoot} by client path. It refuses outward links and writes
 * to a read-only share. Links cannot be read or made, replies name client paths only, and the root's {@code ..}
 * describes the root itself. Owners, groups and ACLs never change. On a writable share, times and POSIX permissions
 * may.
 *
 * <p>A truncating, exclusive or creating open of a missing file uploads to a temporary file
 * ({@link UploadChannel}). Other write opens write in place. Refusals reach the client as "permission denied".
 */
final class ShareAccessor implements SftpFileSystemAccessor {
    private static final Set<String> TIMES = Set.of("lastModifiedTime", "lastAccessTime", "creationTime");
    private final ShareRoot share;
    private final Path root;

    ShareAccessor(ShareRoot share) { this.share = share; this.root = share.root(); }

    @Override public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws IOException {
        try { return share.locate(ClientPaths.relative(ClientPaths.absolute("/", remotePath))); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    /**
     * sshd asks here before it checks a path's existence or reads its attributes. That includes every REALPATH
     * extra part, which never passes {@link #resolveLocalFilePath}. So the path must name an entry whose folder is
     * inside the share, and, when links are followed, an entry that is inside too.
     */
    @Override public LinkOption[] resolveFileAccessLinkOptions(SftpSubsystemProxy subsystem, Path file, int cmd, String extension,
                                                          boolean followLinks) throws IOException {
        String path = client(file);
        try {
            if (followLinks) share.resolve(path);
            else share.locate(path);
        } catch (PathRefusedException refused) {
            throw denied(refused);
        }
        return SftpFileSystemAccessor.super.resolveFileAccessLinkOptions(subsystem, file, cmd, extension, followLinks);
    }

    @Override public SeekableByteChannel openFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
                                                Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        String path = client(file);
        try {
            if (!options.contains(StandardOpenOption.WRITE) && !options.contains(StandardOpenOption.APPEND)) return share.openRead(path);
            boolean exists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
            boolean creates = options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW);
            if (options.contains(StandardOpenOption.CREATE_NEW) && exists) throw new FileAlreadyExistsException("/" + path);
            if (!exists && !creates) throw new NoSuchFileException("/" + path);
            if (options.contains(StandardOpenOption.TRUNCATE_EXISTING) || !exists) return new UploadChannel(share.upload(path));
            return share.openWriteChannel(path, options);
        } catch (PathRefusedException | ReadOnlyShareException refused) {
            throw denied(refused);
        }
    }

    @Override public void closeFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel,
                                    Set<? extends OpenOption> options) throws IOException {
        channel.close(); // no fsync here: on a dropped connection the thread is interrupted, and fsync@openssh.com asks for it
    }

    @Override public void syncFileData(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel) throws IOException {
        if (channel instanceof FileChannel inPlace) inPlace.force(true);
        else if (channel instanceof UploadChannel upload) upload.force();
    }

    @Override public FileLock tryLock(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel,
                                      long position, long size, boolean shared) {
        throw new UnsupportedOperationException("Byte-range locks are not supported");
    }

    @Override public DirectoryStream<Path> openDirectory(SftpSubsystemProxy subsystem, DirectoryHandle dirHandle, Path dir, String handle,
                                                         LinkOption... linkOptions) throws IOException {
        List<Path> entries;
        try { entries = share.list(client(dir)).stream().map(entry -> dir.resolve(entry.name())).toList(); }
        catch (PathRefusedException refused) { throw denied(refused); }
        return new DirectoryStream<>() {
            private boolean iterated;
            @Override public Iterator<Path> iterator() {
                if (iterated) throw new IllegalStateException("A directory stream is read once");
                iterated = true;
                return entries.iterator();
            }
            @Override public void close() { }
        };
    }

    /** A listing's {@code ..} at the root describes the root; every other path goes back through the share. */
    @Override public Map<String, ?> readFileAttributes(SftpSubsystemProxy subsystem, Path file, String view, LinkOption... options) throws IOException {
        if (file.toAbsolutePath().normalize().equals(root.getParent())) return Files.readAttributes(root, view);
        String path = client(file);
        try {
            if (Arrays.asList(options).contains(LinkOption.NOFOLLOW_LINKS)) return Files.readAttributes(share.locate(path), view, LinkOption.NOFOLLOW_LINKS);
            return Files.readAttributes(share.resolve(path), view);
        } catch (PathRefusedException refused) {
            throw denied(refused);
        }
    }

    @Override public void setFileAttribute(SftpSubsystemProxy subsystem, Path file, String view, String attribute, Object value,
                                           LinkOption... options) throws IOException {
        if (!TIMES.contains(attribute)) throw new AccessDeniedException("/" + client(file), null, "Only times and permissions may change");
        Files.setAttribute(writableTarget(file), view + ":" + attribute, value);
    }

    @Override public void setFilePermissions(SftpSubsystemProxy subsystem, Path file, Set<PosixFilePermission> perms, LinkOption... options) throws IOException {
        Files.setPosixFilePermissions(writableTarget(file), perms);
    }

    @Override public void setFileOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException {
        throw new AccessDeniedException("/" + client(file), null, "Owners never change");
    }

    @Override public void setGroupOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException {
        throw new AccessDeniedException("/" + client(file), null, "Groups never change");
    }

    @Override public void setFileAccessControl(SftpSubsystemProxy subsystem, Path file, List<AclEntry> acl, LinkOption... options) throws IOException {
        throw new AccessDeniedException("/" + client(file), null, "ACLs never change");
    }

    @Override public void createDirectory(SftpSubsystemProxy subsystem, Path path) throws IOException {
        try { share.mkdir(client(path)); }
        catch (PathRefusedException | ReadOnlyShareException refused) { throw denied(refused); }
    }

    @Override public void removeFile(SftpSubsystemProxy subsystem, Path path, boolean isDirectory) throws IOException {
        try { share.delete(client(path)); }
        catch (PathRefusedException | ReadOnlyShareException refused) { throw denied(refused); }
    }

    @Override public void renameFile(SftpSubsystemProxy subsystem, Path oldPath, Path newPath, Collection<CopyOption> opts) throws IOException {
        try { share.rename(client(oldPath), client(newPath), opts.contains(StandardCopyOption.REPLACE_EXISTING)); }
        catch (PathRefusedException | ReadOnlyShareException refused) { throw denied(refused); }
    }

    @Override public void copyFile(SftpSubsystemProxy subsystem, Path src, Path dst, Collection<CopyOption> opts) {
        throw new UnsupportedOperationException("Copying on the server is not supported");
    }

    @Override public void createLink(SftpSubsystemProxy subsystem, Path link, Path existing, boolean symLink) throws IOException {
        throw new AccessDeniedException(link.getFileName() == null ? "/" : link.getFileName().toString(), null, "Links cannot be made");
    }

    @Override public String resolveLinkTarget(SftpSubsystemProxy subsystem, Path link) {
        throw new UnsupportedOperationException("Links cannot be read");
    }

    @Override public void putRemoteFileName(SftpSubsystemProxy subsystem, Path path, Buffer buf, String name, boolean shortName) throws IOException {
        buf.putString(remoteName(path, name, shortName));
    }

    /**
     * Replies name client paths. REALPATH would otherwise send the host path (with {@code /} for the separator), and
     * its version 3 long name for the root would end with the shared folder's own name. Listing names pass unchanged.
     */
    private String remoteName(Path path, String name, boolean shortName) throws IOException {
        if (path == null) return name;
        String host = path.toString();
        if (shortName) return name.equals(host) || name.equals(host.replace(File.separatorChar, '/')) ? "/" + client(path) : name;
        Path folder = root.getFileName();
        if (folder != null && path.toAbsolutePath().normalize().equals(root) && name.endsWith(" " + folder))
            return name.substring(0, name.length() - folder.toString().length()) + "/";
        return name;
    }

    /** The real file an attribute change applies to: inside the share, on a writable share. */
    private Path writableTarget(Path file) throws IOException {
        if (share.readOnly()) throw denied(new ReadOnlyShareException());
        try { return share.resolve(client(file)); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    private String client(Path path) throws IOException {
        try { return share.clientPath(path); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    private static AccessDeniedException denied(IOException cause) {
        var denied = new AccessDeniedException(cause.getMessage()); // the client sees only "permission denied"
        denied.initCause(cause);
        return denied;
    }
}
