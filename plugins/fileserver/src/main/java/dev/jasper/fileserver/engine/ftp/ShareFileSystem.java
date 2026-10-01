package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.User;

/** Gives each login its own view of the share, reporting under the address it logged in from. */
final class ShareFileSystem implements FileSystemFactory {
    private final ShareRoot share;
    private final ServerEvents events;

    ShareFileSystem(ShareRoot share, ServerEvents events) { this.share = share; this.events = events; }

    @Override public FileSystemView createFileSystemView(User user) {
        return new ShareView(share, events, user instanceof ShareUser known ? known.remote() : "");
    }
}
