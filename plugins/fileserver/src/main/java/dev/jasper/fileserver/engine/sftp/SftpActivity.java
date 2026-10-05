package dev.jasper.fileserver.engine.sftp;

import java.util.Set;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.server.session.ServerSession;

/**
 * Watches every SFTP request. Only harmless extensions run: the others (copy-file, md5-hash, space-available, …)
 * would reach the disk without the jail's checks. A normal CLOSE marks an upload for commit; a dropped connection
 * never does.
 */
final class SftpActivity implements SftpEventListener {
    static final Set<String> EXTENSIONS = Set.of("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");

    @Override public void receivedExtension(ServerSession session, String extension, int id) {
        if (!EXTENSIONS.contains(extension)) throw new UnsupportedOperationException(extension + " is not supported");
    }

    @Override public void closing(ServerSession session, String remoteHandle, Handle localHandle) {
        if (localHandle instanceof FileHandle file && file.getFileChannel() instanceof UploadChannel upload) upload.complete();
    }
}
