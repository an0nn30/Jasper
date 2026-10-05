package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * An upload's temporary file as an SFTP handle's channel. Closing it commits the upload only after the client closed
 * the handle normally ({@link #complete()}, from the CLOSE request); otherwise, on a dropped connection or a server
 * stop, closing abandons it.
 */
final class UploadChannel implements SeekableByteChannel {
    private final ShareRoot.Upload upload;
    private final FileChannel channel;
    private volatile boolean complete;
    private boolean closed;

    UploadChannel(ShareRoot.Upload upload) { this.upload = upload; this.channel = upload.channel(); }

    /** The client closed the handle normally: closing commits. */
    void complete() { complete = true; }

    /** Forces the temporary file to disk, for {@code fsync@openssh.com}. */
    void force() throws IOException { channel.force(true); }

    @Override public int read(ByteBuffer target) throws IOException { return channel.read(target); }

    /** Writes every byte: sshd ignores a short write. */
    @Override public int write(ByteBuffer source) throws IOException {
        int written = 0;
        while (source.hasRemaining()) written += channel.write(source);
        return written;
    }

    @Override public long position() throws IOException { return channel.position(); }
    @Override public SeekableByteChannel position(long position) throws IOException { channel.position(position); return this; }
    @Override public long size() throws IOException { return channel.size(); }
    @Override public SeekableByteChannel truncate(long size) throws IOException { channel.truncate(size); return this; }
    @Override public boolean isOpen() { return channel.isOpen(); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        if (complete) upload.commit();
        else upload.close();
    }
}
