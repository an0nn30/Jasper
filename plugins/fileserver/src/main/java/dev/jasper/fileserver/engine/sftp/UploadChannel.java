package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * An upload's temporary file as an SFTP handle's channel. Closing it commits the upload only after the client closed
 * the handle normally ({@link #complete()}, from the CLOSE request) and no write failed; otherwise, on a dropped
 * connection, a server stop or a failed write, closing abandons it. A client sends CLOSE even after a failed write,
 * and committing then would replace a good file with a partial one.
 */
final class UploadChannel implements SeekableByteChannel {
    private final ShareRoot.Upload upload;
    private final FileChannel channel;
    private final boolean replace;
    private volatile boolean complete, failed;
    private boolean closed;

    /** {@code replace} is false for an exclusive create: the commit then fails if the target appeared meanwhile. */
    UploadChannel(ShareRoot.Upload upload, boolean replace) { this.upload = upload; this.channel = upload.channel(); this.replace = replace; }

    /** The client closed the handle normally: closing commits, unless a write failed. */
    void complete() { complete = true; }

    /** A write failed: closing abandons the upload, even after {@link #complete()}. */
    void fail() { failed = true; }

    /** Whether a write failed. */
    boolean failed() { return failed; }

    /** Forces the temporary file to disk, for {@code fsync@openssh.com}. */
    void force() throws IOException { channel.force(true); }

    @Override public int read(ByteBuffer target) throws IOException { return channel.read(target); }

    /** Writes every byte: sshd ignores a short write. A failure fails the upload. */
    @Override public int write(ByteBuffer source) throws IOException {
        try {
            int written = 0;
            while (source.hasRemaining()) written += channel.write(source);
            return written;
        } catch (IOException | RuntimeException failure) {
            fail();
            throw failure;
        }
    }

    @Override public long position() throws IOException { return channel.position(); }
    @Override public SeekableByteChannel position(long position) throws IOException { channel.position(position); return this; }
    @Override public long size() throws IOException { return channel.size(); }
    @Override public SeekableByteChannel truncate(long size) throws IOException { channel.truncate(size); return this; }
    @Override public boolean isOpen() { return channel.isOpen(); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        if (complete && !failed) upload.commit(replace);
        else upload.close();
    }
}
