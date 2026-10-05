package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;

/**
 * Watches every SFTP request.
 * <ul>
 * <li>Only harmless extensions run. The others (copy-file, md5-hash, space-available, …) would reach the disk
 *     without the jail's checks.</li>
 * <li>Each file handle is one transfer. A normal CLOSE marks an upload for commit; a dropped connection never
 *     does. A failed write fails the upload, so the CLOSE that follows commits nothing and the transfer is
 *     incomplete.</li>
 * <li>Changes are reported as FTP reports them.</li>
 * </ul>
 */
final class SftpActivity implements SftpEventListener {
    static final Set<String> EXTENSIONS = Set.of("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");
    private final ShareRoot share;
    private final ServerEvents events;
    private final Map<Handle, Track> transfers = new ConcurrentHashMap<>();

    private record Track(ServerEvents.Transfer transfer, boolean upload, long total) { }

    SftpActivity(ShareRoot share, ServerEvents events) { this.share = share; this.events = events; }

    @Override public void receivedExtension(ServerSession session, String extension, int id) {
        if (!EXTENSIONS.contains(extension)) throw new UnsupportedOperationException(extension + " is not supported");
    }

    @Override public void open(ServerSession session, String remoteHandle, Handle localHandle) {
        if (!(localHandle instanceof FileHandle file)) return;
        Set<StandardOpenOption> options = file.getOpenOptions();
        boolean upload = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND);
        long total = -1;
        if (!upload) {
            try { total = file.getFileChannel().size(); }
            catch (IOException unknown) { total = -1; }
        }
        var transfer = events.transfer(SftpLogins.remote(session), upload ? ServerEvent.Direction.UPLOAD : ServerEvent.Direction.DOWNLOAD,
            display(file.getFile()), total, () -> session.close(true));
        transfers.put(localHandle, new Track(transfer, upload, total));
    }

    @Override public void read(ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data, int dataOffset,
                               int dataLen, int readLen, Throwable thrown) {
        Track track = transfers.get(localHandle);
        if (track != null && readLen > 0) track.transfer().add(readLen);
    }

    @Override public void written(ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data, int dataOffset,
                                  int dataLen, Throwable thrown) {
        if (thrown != null && localHandle.getFileChannel() instanceof UploadChannel upload) upload.fail(); // sshd keeps the handle open
        Track track = transfers.get(localHandle);
        if (track != null && thrown == null) track.transfer().add(dataLen);
    }

    @Override public void closing(ServerSession session, String remoteHandle, Handle localHandle) {
        if (localHandle instanceof FileHandle file && file.getFileChannel() instanceof UploadChannel upload) upload.complete();
    }

    /** After a normal CLOSE, whose close committed any upload whose writes all succeeded. */
    @Override public void closed(ServerSession session, String remoteHandle, Handle localHandle, Throwable thrown) {
        Track track = transfers.remove(localHandle);
        if (track == null) return;
        boolean complete = track.upload() ? !failedUpload(localHandle) : track.transfer().bytes() >= track.total();
        track.transfer().finish(thrown == null && complete);
    }

    private static boolean failedUpload(Handle handle) {
        return handle instanceof FileHandle file && file.getFileChannel() instanceof UploadChannel upload && upload.failed();
    }

    /** A handle the client left open when its connection ended: the transfer did not finish. */
    @Override public void exiting(ServerSession session, Handle handle) {
        Track track = transfers.remove(handle);
        if (track != null) track.transfer().finish(false);
    }

    @Override public void created(ServerSession session, Path path, Map<String, ?> attrs, Throwable thrown) {
        if (thrown == null) events.changed(SftpLogins.remote(session), "created " + display(path));
    }

    @Override public void removed(ServerSession session, Path path, boolean isDirectory, Throwable thrown) {
        if (thrown == null) events.changed(SftpLogins.remote(session), "deleted " + display(path));
    }

    @Override public void moved(ServerSession session, Path srcPath, Path dstPath, Collection<CopyOption> opts, Throwable thrown) {
        if (thrown == null) events.changed(SftpLogins.remote(session), "renamed " + display(srcPath) + " to " + display(dstPath));
    }

    /** The client path, "/docs/a.txt"; never a host path. */
    private String display(Path path) {
        try { return "/" + share.clientPath(path); }
        catch (PathRefusedException outside) { return path.getFileName() == null ? "/" : path.getFileName().toString(); }
    }
}
