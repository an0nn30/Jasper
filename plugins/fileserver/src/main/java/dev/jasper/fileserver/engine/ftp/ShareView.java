package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.Closeable;
import java.io.IOException;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 * One logged-in client's view of the share: its working directory and the one transfer it is running. A transfer
 * ends in {@link #finish}, which the ftplet calls with the command's reply. Only a 226 reply commits an upload; any
 * other reply, a new transfer, or the client leaving abandons it. Thread-safe.
 */
final class ShareView implements FileSystemView {
    private final ShareRoot share;
    private final ServerEvents events;
    private final String remote;
    private String workingDirectory = "/";
    private ServerEvents.Transfer transfer;
    private ShareRoot.Upload upload;
    private Closeable stream;
    private String transferPath;

    ShareView(ShareRoot share, ServerEvents events, String remote) { this.share = share; this.events = events; this.remote = remote; }

    ShareRoot share() { return share; }
    ServerEvents events() { return events; }
    String remote() { return remote; }

    @Override public FtpFile getHomeDirectory() { return new ShareFile(this, "/", null); }

    @Override public synchronized FtpFile getWorkingDirectory() { return new ShareFile(this, workingDirectory, null); }

    @Override public synchronized boolean changeWorkingDirectory(String directory) {
        try {
            String target = FtpPaths.absolute(workingDirectory, directory);
            if (!share.attributes(FtpPaths.relative(target)).directory()) return false;
            workingDirectory = target;
            return true;
        } catch (IOException missingOrRefused) {
            return false;
        }
    }

    @Override public synchronized FtpFile getFile(String file) throws FtpException {
        try { return new ShareFile(this, FtpPaths.absolute(workingDirectory, file), null); }
        catch (PathRefusedException refused) { throw new FtpException(refused.getMessage(), refused); }
    }

    @Override public boolean isRandomAccessible() { return true; }

    @Override public void dispose() { finish(false); }

    /** Starts tracking a transfer, abandoning any earlier one; {@code upload} is null for downloads and appends. */
    synchronized void track(ServerEvents.Transfer next, ShareRoot.Upload nextUpload, Closeable nextStream, String path) {
        finish(false);
        transfer = next;
        upload = nextUpload;
        stream = nextStream;
        transferPath = path;
    }

    /** Ends the tracked transfer, if any: commits its upload when {@code success}, otherwise abandons it. */
    synchronized void finish(boolean success) {
        if (transfer == null) return;
        ServerEvents.Transfer ending = transfer;
        ShareRoot.Upload pending = upload;
        Closeable open = stream;
        String path = transferPath;
        transfer = null;
        upload = null;
        stream = null;
        transferPath = null;
        boolean complete = success;
        try { open.close(); } catch (IOException ignored) { /* the transfer is over either way */ }
        if (pending != null) {
            if (success) {
                try { pending.commit(); }
                catch (IOException | RuntimeException failure) {
                    complete = false;
                    events.error(remote, "Could not save " + path + ": "
                        + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
                }
            } else {
                pending.close();
            }
        }
        ending.finish(complete);
    }
}
