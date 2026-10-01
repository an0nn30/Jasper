package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareEntry;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Optional;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 * One path in a share as FtpServer sees it. Every operation goes through {@link ShareRoot}, which refuses escapes and
 * writes to a read-only share. Listings show every shareable name, as HTTP does. A new or replaced file arrives
 * through {@link ShareRoot#upload}. A non-zero offset is accepted only at the current end of the file, as an append;
 * an upload resumed part-way through is refused.
 */
final class ShareFile implements FtpFile {
    private final ShareView view;
    private final String path;
    private final ShareEntry known;

    /** {@code known} is the entry a listing already read, or null to read it when asked. */
    ShareFile(ShareView view, String path, ShareEntry known) { this.view = view; this.path = path; this.known = known; }

    private String relative() { return FtpPaths.relative(path); }

    private Optional<ShareEntry> entry() {
        if (known != null) return Optional.of(known);
        try { return Optional.of(view.share().attributes(relative())); }
        catch (IOException missingOrRefused) { return Optional.empty(); }
    }

    @Override public String getAbsolutePath() { return path; }
    @Override public String getName() { return FtpPaths.name(path); }
    @Override public boolean isHidden() { return false; }
    @Override public boolean isDirectory() { return entry().map(ShareEntry::directory).orElse(false); }
    @Override public boolean isFile() { return entry().map(entry -> !entry.directory()).orElse(false); }
    @Override public boolean doesExist() { return entry().isPresent(); }
    @Override public boolean isReadable() { return doesExist(); }
    @Override public boolean isWritable() { return !view.share().readOnly() && !path.equals("/"); }
    @Override public boolean isRemovable() { return isWritable() && doesExist(); }
    @Override public String getOwnerName() { return "user"; }
    @Override public String getGroupName() { return "group"; }
    @Override public int getLinkCount() { return isDirectory() ? 3 : 1; }
    @Override public long getLastModified() { return entry().map(entry -> entry.modified().toEpochMilli()).orElse(0L); }
    @Override public boolean setLastModified(long time) { return false; }
    @Override public long getSize() { return entry().map(ShareEntry::size).orElse(0L); }
    /** Never the real file: nothing outside the share's checks may touch the disk. */
    @Override public Object getPhysicalFile() { return null; }

    @Override public boolean mkdir() {
        try { view.share().mkdir(relative()); }
        catch (IOException refused) { return false; }
        view.events().changed(view.remote(), "created " + path);
        return true;
    }

    @Override public boolean delete() {
        try { view.share().delete(relative()); }
        catch (IOException refused) { return false; }
        view.events().changed(view.remote(), "deleted " + path);
        return true;
    }

    @Override public boolean move(FtpFile destination) {
        if (!(destination instanceof ShareFile target)) return false;
        try { view.share().rename(relative(), target.relative()); }
        catch (IOException refused) { return false; }
        view.events().changed(view.remote(), "renamed " + path + " to " + target.path);
        return true;
    }

    @Override public List<? extends FtpFile> listFiles() {
        String prefix = path.equals("/") ? "/" : path + "/";
        try { return view.share().list(relative()).stream().map(entry -> new ShareFile(view, prefix + entry.name(), entry)).toList(); }
        catch (IOException notAFolder) { return null; }
    }

    @Override public InputStream createInputStream(long offset) throws IOException {
        SeekableByteChannel channel = view.share().openRead(relative());
        long total;
        try {
            total = Math.max(0, channel.size() - offset);
            channel.position(offset);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
        ServerEvents.Transfer transfer = view.events().transfer(view.remote(), ServerEvent.Direction.DOWNLOAD, path, total, () -> closeQuietly(channel));
        var in = new CountingInput(Channels.newInputStream(channel), transfer);
        view.track(transfer, null, in, path);
        return in;
    }

    @Override public OutputStream createOutputStream(long offset) throws IOException {
        if (offset == 0) {
            ShareRoot.Upload upload = view.share().upload(relative());
            OutputStream raw = upload.stream();
            ServerEvents.Transfer transfer = view.events().transfer(view.remote(), ServerEvent.Direction.UPLOAD, path, -1, () -> closeQuietly(raw));
            var out = new CountingOutput(raw, transfer);
            view.track(transfer, upload, out, path);
            return out;
        }
        if (!isFile() || offset != getSize()) throw new IOException("Resuming an upload part-way through a file is not supported");
        OutputStream raw = view.share().openWrite(relative(), true);
        ServerEvents.Transfer transfer = view.events().transfer(view.remote(), ServerEvent.Direction.UPLOAD, path, -1, () -> closeQuietly(raw));
        var out = new CountingOutput(raw, transfer);
        view.track(transfer, null, out, path);
        return out;
    }

    private static void closeQuietly(Closeable closeable) {
        try { closeable.close(); } catch (IOException ignored) { /* cancelling; the transfer fails next */ }
    }

    /** Counts what a download sends. */
    private static final class CountingInput extends FilterInputStream {
        private final ServerEvents.Transfer transfer;

        CountingInput(InputStream in, ServerEvents.Transfer transfer) { super(in); this.transfer = transfer; }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) transfer.add(1);
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) transfer.add(count);
            return count;
        }
    }

    /** Counts what an upload receives; closing twice is harmless (FtpServer's STOR closes the stream twice). */
    private static final class CountingOutput extends FilterOutputStream {
        private final ServerEvents.Transfer transfer;

        CountingOutput(OutputStream out, ServerEvents.Transfer transfer) { super(out); this.transfer = transfer; }

        @Override public void write(int value) throws IOException {
            out.write(value);
            transfer.add(1);
        }

        @Override public void write(byte[] buffer, int offset, int length) throws IOException {
            out.write(buffer, offset, length);
            transfer.add(length);
        }
    }
}
