package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.engine.ClientPaths;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.IsFolderException;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One TFTP read or write, on its own socket. Every packet is resent after the timeout, up to the retry count, and a
 * packet from anyone but the client gets error 5, unless it is itself an error, which is never answered. A duplicate
 * acknowledgement is ignored, never answered with a resend. Refusals are error packets with fixed texts, never host
 * paths.
 */
final class TftpTransfer {
    static final TftpPacket.Error BUSY = new TftpPacket.Error(0, "Server busy");
    static final TftpPacket.Error FAILED = new TftpPacket.Error(0, "Transfer failed");
    static final TftpPacket.Error SIZE_MISMATCH = new TftpPacket.Error(0, "Transfer size mismatch");
    static final TftpPacket.Error NOT_FOUND = new TftpPacket.Error(1, "File not found");
    static final TftpPacket.Error ACCESS = new TftpPacket.Error(2, "Access violation");
    static final TftpPacket.Error DISK_FULL = new TftpPacket.Error(3, "Disk full or allocation exceeded");
    static final TftpPacket.Error ILLEGAL = new TftpPacket.Error(4, "Illegal TFTP operation");
    static final TftpPacket.Error UNKNOWN_ID = new TftpPacket.Error(5, "Unknown transfer ID");

    private final ShareRoot share;
    private final ServerEvents events;
    private final InetSocketAddress client;
    private final TftpPacket.Request request;
    private final Duration timeout;
    private final int retries;
    private final String remote;
    private final byte[] buffer = new byte[TftpPacket.MAX_PACKET];
    private DatagramSocket socket;
    private int blockSize = TftpPacket.DEFAULT_BLOCK;

    TftpTransfer(ShareRoot share, ServerEvents events, InetSocketAddress client, TftpPacket.Request request, Duration timeout, int retries) {
        this.share = share; this.events = events; this.client = client; this.request = request; this.timeout = timeout; this.retries = retries;
        this.remote = ServerAddresses.hostText(client.getAddress());
    }

    /** Runs the transfer on {@code transferSocket} and returns when it ends, however it ends. */
    void run(DatagramSocket transferSocket) {
        socket = transferSocket;
        try {
            boolean netascii = request.mode().equals("netascii");
            if (!netascii && !request.mode().equals("octet")) {
                send(ILLEGAL);
                return;
            }
            String path;
            try { path = ClientPaths.absolute("/", request.filename()); }
            catch (PathRefusedException refused) { refuse(refused, request.filename()); return; }
            if (request.write()) write(path, netascii);
            else read(path, netascii);
        } catch (IOException failure) {
            if (!socket.isClosed()) events.error(remote, "TFTP transfer failed: " + failure.getMessage());
        }
    }

    private void read(String path, boolean netascii) throws IOException {
        SeekableByteChannel channel;
        try { channel = share.openRead(ClientPaths.relative(path)); }
        catch (IOException refused) { refuse(refused, path); return; }
        try (channel; InputStream raw = Channels.newInputStream(channel);
             InputStream in = netascii ? NetAscii.encoding(new BufferedInputStream(raw)) : raw) {
            long size = channel.size();
            Map<String, String> accepted = negotiate(netascii ? -1 : size);
            var transfer = events.transfer(remote, ServerEvent.Direction.DOWNLOAD, path, netascii ? -1 : size, socket::close);
            boolean complete = false;
            try {
                if (!accepted.isEmpty() && !awaitAck(new TftpPacket.OptionAck(accepted).encode(), 0)) return;
                int block = 1;
                while (true) {
                    byte[] data = in.readNBytes(blockSize);
                    if (!awaitAck(new TftpPacket.Data(block, data).encode(), block)) return;
                    transfer.add(data.length);
                    if (data.length < blockSize) {
                        complete = true;
                        return;
                    }
                    block = (block + 1) & 0xFFFF;
                }
            } catch (IOException failure) {
                if (!socket.isClosed()) reply(client, FAILED); // best effort: the client learns why the transfer ended
                throw failure;
            } finally {
                transfer.finish(complete);
            }
        }
    }

    private void write(String path, boolean netascii) throws IOException {
        if (share.readOnly()) {
            refuse(new ReadOnlyShareException(), path);
            return;
        }
        long announced = announcedSize();
        if (announced > usableSpace()) {
            send(DISK_FULL);
            events.error(remote, DISK_FULL.message() + ": " + path);
            return;
        }
        ShareRoot.Upload upload;
        try { upload = share.upload(ClientPaths.relative(path)); }
        catch (IOException refused) { refuse(refused, path); return; }
        Map<String, String> accepted = negotiate(netascii ? -1 : announced);
        var transfer = events.transfer(remote, ServerEvent.Direction.UPLOAD, path, announced, socket::close);
        boolean complete = false;
        try (upload) {
            OutputStream out = netascii ? NetAscii.decoding(new BufferedOutputStream(upload.stream())) : upload.stream();
            byte[] lastSent = (accepted.isEmpty() ? new TftpPacket.Ack(0) : new TftpPacket.OptionAck(accepted)).encode();
            int block = 1;
            for (TftpPacket.Data data = awaitData(lastSent, block); data != null; data = awaitData(lastSent, block)) {
                if (data.bytes().length > blockSize) {
                    send(ILLEGAL);
                    events.error(remote, ILLEGAL.message() + ": " + path);
                    return;
                }
                try { out.write(data.bytes()); }
                catch (IOException failed) { failWrite(failed); return; }
                transfer.add(data.bytes().length);
                lastSent = new TftpPacket.Ack(block).encode();
                if (data.bytes().length < blockSize) {
                    if (!netascii && announced >= 0 && transfer.bytes() != announced) {
                        send(SIZE_MISMATCH);
                        events.error(remote, SIZE_MISMATCH.message() + ": " + path);
                        return;
                    }
                    try {
                        out.close();
                        if (socket.isClosed()) return; // the server stopped: abandon, never commit
                        upload.commit();
                    } catch (IOException failed) {
                        failWrite(failed);
                        return;
                    }
                    complete = true;
                    send(lastSent);
                    dally(lastSent, block);
                    return;
                }
                block = (block + 1) & 0xFFFF;
            }
        } finally {
            transfer.finish(complete);
        }
    }

    /** Logs a failed write as the read path does, then tells the client. */
    private void failWrite(IOException failure) throws IOException {
        events.error(remote, "TFTP transfer failed: " + failure.getMessage());
        send(error(failure));
    }

    /** Sends {@code lastSent} until block {@code expected} arrives; a repeat of the previous block is answered at once. Null when it gives up. */
    private TftpPacket.Data awaitData(byte[] lastSent, int expected) throws IOException {
        int previous = (expected - 1) & 0xFFFF;
        for (int attempt = 0; attempt <= retries; attempt++) {
            send(lastSent);
            long deadline = System.nanoTime() + timeout.toNanos();
            for (TftpPacket reply = receive(deadline); reply != null; reply = receive(deadline)) {
                if (reply instanceof TftpPacket.Data data && data.block() == expected) return data;
                if (reply instanceof TftpPacket.Data data && data.block() == previous) send(lastSent);
                if (reply instanceof TftpPacket.Error) return null;
            }
        }
        return null;
    }

    /** After the final ACK, answers a repeated final block for one timeout, for a client whose ACK was lost. */
    private void dally(byte[] finalAck, int block) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (TftpPacket reply = receive(deadline); reply != null; reply = receive(deadline))
            if (reply instanceof TftpPacket.Data data && data.block() == block) send(finalAck);
    }

    /** The client's tsize for a write, or -1 when it sent none or an unusable one. */
    private long announcedSize() {
        String tsize = request.options().get("tsize");
        if (tsize == null) return -1;
        try { return Math.max(-1, Long.parseLong(tsize.strip())); }
        catch (NumberFormatException unusable) { return -1; }
    }

    private long usableSpace() {
        try { return Files.getFileStore(share.root()).getUsableSpace(); }
        catch (IOException unknown) { return Long.MAX_VALUE; }
    }

    /**
     * The requested options this server accepts, with the values it uses (RFC 2347); sets the block size. tsize is
     * answered with {@code size}, or left out when {@code size} is -1.
     */
    private Map<String, String> negotiate(long size) {
        var accepted = new LinkedHashMap<String, String>();
        String block = request.options().get("blksize");
        if (block != null) {
            try {
                int asked = Integer.parseInt(block.strip());
                if (asked >= 8) {
                    blockSize = Math.min(asked, TftpPacket.MAX_BLOCK);
                    accepted.put("blksize", String.valueOf(blockSize));
                }
            } catch (NumberFormatException unusable) { /* left out, as RFC 2347 allows */ }
        }
        if (request.options().containsKey("tsize") && size >= 0) accepted.put("tsize", String.valueOf(size));
        return accepted;
    }

    /** Sends {@code outgoing} until the client acknowledges {@code block}; false when it gives up or the client sends an error. */
    private boolean awaitAck(byte[] outgoing, int block) throws IOException {
        for (int attempt = 0; attempt <= retries; attempt++) {
            send(outgoing);
            long deadline = System.nanoTime() + timeout.toNanos();
            for (TftpPacket reply = receive(deadline); reply != null; reply = receive(deadline)) {
                if (reply instanceof TftpPacket.Ack ack && ack.block() == block) return true;
                if (reply instanceof TftpPacket.Error) return false;
            }
        }
        return false;
    }

    /** The client's next packet before the deadline, or null; anyone else gets error 5, and malformed packets are dropped. */
    TftpPacket receive(long deadline) throws IOException {
        while (true) {
            long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (left <= 0) return null;
            socket.setSoTimeout((int) Math.min(left, Integer.MAX_VALUE));
            var packet = new DatagramPacket(buffer, buffer.length);
            try { socket.receive(packet); }
            catch (SocketTimeoutException timedOut) { return null; }
            if (!client.equals(packet.getSocketAddress())) {
                if (!TftpPacket.isError(packet.getData(), packet.getLength())) reply(packet.getSocketAddress(), UNKNOWN_ID);
                continue;
            }
            try { return TftpPacket.parse(packet.getData(), packet.getLength()); }
            catch (IllegalArgumentException malformed) { /* dropped; a resend may follow */ }
        }
    }

    void send(TftpPacket packet) throws IOException { send(packet.encode()); }

    void send(byte[] bytes) throws IOException { socket.send(new DatagramPacket(bytes, bytes.length, client)); }

    /** Sends {@code packet} to {@code to}, best effort. */
    private void reply(SocketAddress to, TftpPacket packet) {
        byte[] bytes = packet.encode();
        try { socket.send(new DatagramPacket(bytes, bytes.length, to)); }
        catch (IOException ignored) { /* the peer will time out */ }
    }

    /** Answers a refusal with its error packet and logs it under the client path. */
    void refuse(IOException failure, String path) throws IOException {
        TftpPacket.Error error = error(failure);
        send(error);
        events.error(remote, error.message() + ": " + path);
    }

    /** The error packet for a failure; fixed texts only. */
    static TftpPacket.Error error(IOException failure) {
        if (failure instanceof NoSuchFileException) return NOT_FOUND;
        if (failure instanceof PathRefusedException || failure instanceof ReadOnlyShareException || failure instanceof IsFolderException
            || failure instanceof AccessDeniedException) return ACCESS;
        String text = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (text.contains("no space") || text.contains("disk full") || text.contains("not enough space")) return DISK_FULL;
        return FAILED;
    }
}
