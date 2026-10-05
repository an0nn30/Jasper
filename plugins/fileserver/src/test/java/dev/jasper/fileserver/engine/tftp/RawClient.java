package dev.jasper.fileserver.engine.tftp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;

/** A TFTP client that sends exactly the packets a test asks for, so options, losses and strangers can be tested. */
final class RawClient implements AutoCloseable {
    /** A packet and the server port it came from (the transfer's own port, or the server's for refusals). */
    record Reply(int port, TftpPacket packet) { }

    final DatagramSocket socket;

    RawClient() throws IOException { socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)); }

    void send(int port, TftpPacket packet) throws IOException {
        byte[] bytes = packet.encode();
        socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getLoopbackAddress(), port));
    }

    Optional<Reply> receive(Duration wait) throws IOException {
        socket.setSoTimeout((int) Math.max(1, wait.toMillis()));
        byte[] buffer = new byte[TftpPacket.MAX_PACKET];
        var packet = new DatagramPacket(buffer, buffer.length);
        try { socket.receive(packet); }
        catch (SocketTimeoutException none) { return Optional.empty(); }
        return Optional.of(new Reply(packet.getPort(), TftpPacket.parse(packet.getData(), packet.getLength())));
    }

    Reply next() throws IOException {
        return receive(Duration.ofSeconds(5)).orElseThrow(() -> new AssertionError("no TFTP packet within 5 s"));
    }

    static TftpPacket.Request read(String file, String mode, String... options) { return new TftpPacket.Request(false, file, mode, options(options)); }
    static TftpPacket.Request write(String file, String mode, String... options) { return new TftpPacket.Request(true, file, mode, options(options)); }

    private static LinkedHashMap<String, String> options(String... pairs) {
        var options = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) options.put(pairs[i], pairs[i + 1]);
        return options;
    }

    @Override public void close() { socket.close(); }
}
