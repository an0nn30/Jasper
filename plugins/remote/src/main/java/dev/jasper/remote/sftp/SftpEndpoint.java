package dev.jasper.remote.sftp;

import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.trust.KnownHosts;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpVersionSelector;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHPosixRenameExtension;
import org.apache.sshd.sftp.client.impl.DefaultSftpClient;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

/** One owned subsystem per worker; fixed request windows keep payloads bounded. No shell commands. */
public final class SftpEndpoint implements FileEndpoint {
    static final int CHUNK = 128 * 1024, REQUESTS = 16;
    private final SessionLease lease;
    private final DefaultSftpClient client;
    private final Duration timeout;
    private final String id;
    private volatile boolean closed;
    private volatile ChannelSubsystem channel;

    public SftpEndpoint(SessionLease lease, Duration timeout) throws IOException { this(lease, timeout, endpoint -> {}); }

    SftpEndpoint(SessionLease lease, Duration timeout, Consumer<SftpEndpoint> created) throws IOException {
        this.lease = Objects.requireNonNull(lease); this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required");
        id = "ssh:" + KnownHosts.fingerprint(lease.session().getServerKey()) + ":" + lease.session().getUsername();
        try {
            created.accept(this); check();
            client = new DefaultSftpClient(lease.session(), SftpVersionSelector.fixedVersionSelector(3), null) {
                @Override protected ChannelSubsystem createSftpChannelSubsystem(ClientSession session) {
                    if (closed) throw new java.util.concurrent.CancellationException("SFTP open cancelled");
                    var channel = super.createSftpChannelSubsystem(session);
                    SftpEndpoint.this.channel = channel;
                    if (closed) throw new java.util.concurrent.CancellationException("SFTP open cancelled");
                    CoreModuleProperties.WINDOW_SIZE.set(channel, (long) CHUNK * REQUESTS);
                    SFTP_CLIENT_CMD_TIMEOUT.set(channel, timeout);
                    return channel;
                }
                @Override public Buffer receive(int request) throws IOException {
                    Buffer result = super.receive(request, timeout);
                    if (result == null) throw new SocketTimeoutException("Timed out waiting for SFTP response");
                    return result;
                }
            };
            check();
        } catch (IOException | RuntimeException failure) { abort(); throw failure; }
    }
    @Override public String id() { return id; }
    private void check() throws IOException { if (closed || Thread.currentThread().isInterrupted()) throw new IOException("SFTP operation cancelled"); }
    private String path(String text) throws IOException { check(); return FilePaths.absolute(text); }
    private SftpClient.Attributes attributes(String path) throws IOException {
        try { return client.lstat(path); }
        catch (SftpException e) { if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE || e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_PATH) throw new NoSuchFileException(path); throw e; }
    }
    private void directory(String path) throws IOException {
        if (!attributes(path).isDirectory()) throw new IOException("Directory changed or is a symbolic link: " + path);
    }
    private void ancestors(String text) throws IOException {
        String at = "/"; directory(at);
        for (String part : text.split("/")) if (!part.isEmpty()) { at = FilePaths.child(at, part); directory(at); }
    }
    private FileEntry entry(String path, SftpClient.Attributes a) throws IOException {
        return new FileEntry(FilePaths.name(path), a.isSymbolicLink() ? FileEntry.Kind.LINK : a.isDirectory() ? FileEntry.Kind.DIRECTORY
            : a.isRegularFile() ? FileEntry.Kind.FILE : FileEntry.Kind.SPECIAL, a.getSize(),
            a.getModifyTime() == null ? -1 : a.getModifyTime().toMillis(), a.getFlags().contains(SftpClient.Attribute.Perms) ? a.getPermissions() & 0777 : -1,
            a.isSymbolicLink() ? client.readLink(path) : "");
    }
    @Override public FileEntry stat(String text) throws IOException { String path = path(text); ancestors(FilePaths.parent(path)); return entry(path, attributes(path)); }
    @Override public void list(String text, Consumer<FileEntry> visitor) throws IOException {
        String path = path(text); ancestors(path);
        try (var handle = client.openDir(path)) {
            int emptyPages = 0;
            for (;;) {
                check(); List<SftpClient.DirEntry> page = client.readDir(handle);
                if (page == null) return;
                if (page.isEmpty()) { if (++emptyPages > 2) throw new IOException("Server returned repeated empty directory pages"); }
                else emptyPages = 0;
                for (var child : page) {
                    check(); if (child.getFilename().equals(".") || child.getFilename().equals("..")) continue;
                    visitor.accept(entry(FilePaths.child(path, child.getFilename()), child.getAttributes()));
                }
            }
        }
    }
    private SftpClient.CloseableHandle file(String text, SftpClient.OpenMode... modes) throws IOException {
        String path = path(text); ancestors(FilePaths.parent(path));
        boolean exclusive = java.util.Arrays.asList(modes).contains(SftpClient.OpenMode.Exclusive);
        if (!exclusive && !attributes(path).isRegularFile()) throw new IOException("Expected a regular file: " + path);
        return client.open(path, modes);
    }
    private record Pending(int id, long offset, int length) { long end() { return offset + length; } }
    private Buffer response(Pending pending, String operation) throws IOException {
        check(); Buffer result = client.receive(pending.id(), timeout);
        if (result == null) throw new SocketTimeoutException("Timed out waiting for SFTP " + operation + " acknowledgement");
        if (result.available() < 9) throw new IOException("Truncated SFTP response");
        int length = result.getInt(); int type = result.getUByte(); int id = result.getInt();
        if (length != result.available() + 5 || id != pending.id()) throw new IOException("Invalid SFTP response identity");
        if (type != SftpConstants.SSH_FXP_STATUS && type != SftpConstants.SSH_FXP_DATA) throw new IOException("Unexpected SFTP response type");
        // Keep type in the buffer's first byte so both bounded stream adapters can validate it.
        result.rpos(result.rpos() - 5); return result;
    }
    private int type(Buffer response) { int type = response.getUByte(); response.getInt(); return type; }
    private int status(Buffer response) throws IOException {
        if (response.available() < 4) throw new IOException("Truncated SFTP status");
        return response.getInt();
    }
    @Override public InputStream read(String text, long offset) throws IOException {
        if (offset < 0) throw new IOException("Negative offset");
        String path = path(text); long size = attributes(path).getSize();
        var handle = file(path, SftpClient.OpenMode.Read);
        return new InputStream() {
            final ArrayDeque<Pending> pending = new ArrayDeque<>();
            long requested = offset;
            Buffer current;
            boolean ended, stopped;
            private Pending request(long at, int length) throws IOException {
                var message = new ByteArrayBuffer(); message.putBytes(handle.getIdentifier()); message.putLong(at); message.putInt(length);
                return new Pending(client.send(SftpConstants.SSH_FXP_READ, message), at, length);
            }
            private void fill() throws IOException {
                while (pending.size() < REQUESTS && requested < size) {
                    int count = (int) Math.min(CHUNK, size - requested);
                    pending.addLast(request(requested, count)); requested += count;
                }
            }
            @Override public int read() throws IOException { var one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 255; }
            @Override public int read(byte[] data, int off, int length) throws IOException {
                Objects.checkFromIndexSize(off, length, data.length); if (length == 0) return 0;
                if (stopped) throw new IOException("Stream closed"); check();
                try {
                    if (current == null || current.available() == 0) {
                        if (ended) return -1;
                        fill();
                        if (pending.isEmpty()) { ended = true; return -1; }
                        var next = pending.removeFirst(); Buffer reply = response(next, "read"); int type = type(reply);
                        if (type == SftpConstants.SSH_FXP_STATUS) {
                            int code = status(reply);
                            if (code == SftpConstants.SSH_FX_EOF) throw new IOException("Source shortened during read");
                            throw new SftpException(code, "SFTP read failed");
                        }
                        if (reply.available() < 4) throw new IOException("Truncated SFTP data");
                        int count = reply.getInt();
                        if (count <= 0 || count > next.length() || count > reply.available()) throw new IOException("Invalid SFTP data length");
                        reply.wpos(reply.rpos() + count); current = reply;
                        if (count < next.length()) pending.addFirst(request(next.offset() + count, next.length() - count));
                    }
                    int count = Math.min(length, current.available()); current.getRawBytes(data, off, count); return count;
                } catch (IOException | RuntimeException failure) { abort(); throw failure; }
            }
            @Override public void close() throws IOException {
                if (stopped) return; stopped = true;
                if (!closed) {
                    // Drain bounded outstanding reads before releasing the handle, preserving other streams on this subsystem.
                    try { while (!pending.isEmpty()) response(pending.removeFirst(), "read"); }
                    finally { handle.close(); }
                }
                current = null; pending.clear();
            }
        };
    }
    @Override public WriteHandle write(String text, long offset, boolean exclusive) throws IOException {
        if (offset < 0) throw new IOException("Negative offset");
        var handle = exclusive ? file(text, SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Exclusive)
            : file(text, SftpClient.OpenMode.Write);
        return new WriteHandle() {
            final ArrayDeque<Pending> pending = new ArrayDeque<>();
            long next = offset, confirmed = offset;
            boolean stopped;
            IOException failure;
            private void checkWriter() throws IOException { if (failure != null) throw failure; if (stopped) throw new IOException("Writer closed"); check(); }
            private void acknowledged() throws IOException {
                var request = pending.removeFirst(); var reply = response(request, "write");
                if (type(reply) != SftpConstants.SSH_FXP_STATUS) throw new IOException("Expected a write acknowledgement");
                int code = status(reply); if (code != SftpConstants.SSH_FX_OK) throw new SftpException(code, "SFTP write acknowledgement failed");
                if (request.offset() != confirmed) throw new IOException("Noncontiguous write acknowledgement");
                confirmed = request.end();
            }
            private IOException failed(IOException problem) { failure = problem; SftpEndpoint.this.abort(); return problem; }
            @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}); }
            @Override public void write(byte[] data, int off, int length) throws IOException {
                Objects.checkFromIndexSize(off, length, data.length); checkWriter();
                try {
                    while (length > 0) {
                        if (pending.size() == REQUESTS) acknowledged();
                        int count = Math.min(CHUNK, length);
                        var message = new ByteArrayBuffer(count + 64, false);
                        message.putBytes(handle.getIdentifier()); message.putLong(next); message.putBytes(data, off, count);
                        if (next > Long.MAX_VALUE - count) throw new IOException("File offset overflow");
                        pending.addLast(new Pending(client.send(SftpConstants.SSH_FXP_WRITE, message), next, count));
                        next += count; off += count; length -= count;
                    }
                } catch (IOException problem) { throw failed(problem); }
            }
            @Override public long checkpoint() throws IOException {
                checkWriter(); try { while (!pending.isEmpty()) acknowledged(); return confirmed; }
                catch (IOException problem) { throw failed(problem); }
            }
            @Override public void flush() throws IOException { checkpoint(); }
            @Override public void abort() { SftpEndpoint.this.abort(); }
            @Override public void close() throws IOException {
                if (failure != null) throw failure;
                if (stopped) return;
                try { checkpoint(); handle.close(); } catch (IOException problem) { throw failed(problem); }
                finally { stopped = true; pending.clear(); }
            }
        };
    }
    @Override public void truncate(String text, long size) throws IOException {
        if (size < 0) throw new IOException("Negative size");
        try (var handle = file(text, SftpClient.OpenMode.Write)) { client.setStat(handle, new SftpClient.Attributes().size(size)); }
    }
    @Override public void mkdir(String text) throws IOException { String path = path(text); ancestors(FilePaths.parent(path)); client.mkdir(path); }
    @Override public void symlink(String text, String target) throws IOException { String path = path(text); ancestors(FilePaths.parent(path)); client.symLink(path, target); }
    @Override public void remove(String text, boolean directory) throws IOException {
        String path = path(text); ancestors(FilePaths.parent(path)); if (directory) { directory(path); client.rmdir(path); } else client.remove(path);
    }
    @Override public void publish(String temporary, String target, boolean replace) throws IOException {
        String from = path(temporary), to = path(target); ancestors(FilePaths.parent(from)); ancestors(FilePaths.parent(to));
        if (!replace) client.rename(from, to);
        else {
            var extension = client.getExtension(OpenSSHPosixRenameExtension.class);
            if (extension == null || !extension.isSupported()) throw new IOException("Server does not support atomic replacement");
            extension.posixRename(from, to);
        }
    }
    @Override public void metadata(String text, long modified, int permissions) throws IOException {
        String path = path(text); ancestors(FilePaths.parent(path));
        var prior = attributes(path);
        if (prior.isSymbolicLink()) throw new IOException("Link metadata unsupported");
        var attributes = new SftpClient.Attributes();
        // SFTP v3 encodes access and modification times together; preserve the existing access time.
        if (modified >= 0) attributes.accessTime(prior.getAccessTime() == null ? FileTime.fromMillis(modified) : prior.getAccessTime())
            .modifyTime(FileTime.fromMillis(modified));
        if (permissions >= 0) attributes.perms(permissions & 0777);
        client.setStat(path, attributes);
    }
    @Override public String canonical(String text) throws IOException {
        String path = path(text); if (path.equals("/")) return client.canonicalPath(path);
        return FilePaths.child(client.canonicalPath(FilePaths.parent(path)), FilePaths.name(path));
    }
    @Override public String resolveDirectory(String text) throws IOException {
        String requested=client.canonicalPath(path(text));int links=0;
        // Some SFTP servers normalize REALPATH without dereferencing symbolic links.
        // Resolve each component deliberately here; recursive file operations never call this.
        while(true) {
            String resolved="/";var parts=FilePaths.absolute(requested).substring(1).split("/");boolean again=false;
            for(int index=0;index<parts.length;index++) {
                if(parts[index].isEmpty()) continue;
                String next=FilePaths.child(resolved,parts[index]);var info=attributes(next);
                if(info.isSymbolicLink()) {
                    if(++links>40) throw new IOException("Too many directory links");
                    String link=client.readLink(next);StringBuilder expanded=new StringBuilder(link.startsWith("/")?link:resolved+"/"+link);
                    for(int rest=index+1;rest<parts.length;rest++) expanded.append('/').append(parts[rest]);
                    requested=FilePaths.absolute(expanded.toString());again=true;break;
                }
                if(!info.isDirectory()) throw new IOException("Path is not a directory");
                resolved=next;
            }
            if(!again) return resolved;
        }
    }
    @Override public String home() throws IOException { check(); return client.canonicalPath("."); }
    @Override public void abort() { closed = true; var owned = channel; if (owned != null) owned.close(true); lease.close(); }
    @Override public void close() { abort(); }
}
