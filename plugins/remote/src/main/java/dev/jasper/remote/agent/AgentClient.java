package dev.jasper.remote.agent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

/**
 * Speaks only {@code SSH_AGENTC_REQUEST_IDENTITIES} and {@code SSH_AGENTC_SIGN_REQUEST}. One short-lived
 * connection per request: a Unix-domain socket at {@code SSH_AUTH_SOCK}, or the OpenSSH named pipe on Windows.
 */
public final class AgentClient {
    public static final int FLAG_RSA_SHA2_256 = 2;
    public static final int FLAG_RSA_SHA2_512 = 4;
    static final int REQUEST_IDENTITIES = 11, IDENTITIES_ANSWER = 12, SIGN_REQUEST = 13, SIGN_RESPONSE = 14;
    static final String WINDOWS_PIPE = "\\\\.\\pipe\\openssh-ssh-agent";
    private static final int MAX_REPLY = 1 << 20;

    /** One key the agent holds; {@code blob} is its SSH wire encoding, sent back when asking for a signature. */
    public record Identity(PublicKey key, byte[] blob, String comment) { }

    private final Function<byte[], byte[]> exchange;

    public AgentClient(Function<byte[], byte[]> exchange) { this.exchange = exchange; }

    /** The agent named by the environment: {@code SSH_AUTH_SOCK}, or the OpenSSH pipe on Windows; empty when neither applies. */
    public static Optional<AgentClient> forEnvironment(Map<String, String> env, String osName) {
        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        String socket = env.get("SSH_AUTH_SOCK");
        if (socket != null && !socket.isBlank()) return Optional.of(new AgentClient(request -> unixSocket(Path.of(socket), request)));
        if (windows) return Optional.of(new AgentClient(request -> namedPipe(WINDOWS_PIPE, request)));
        return Optional.empty();
    }

    public List<Identity> identities() throws IOException {
        ByteArrayBuffer reply = request(new byte[] {(byte) REQUEST_IDENTITIES}, IDENTITIES_ANSWER);
        int count = reply.getInt();
        var out = new ArrayList<Identity>();
        for (int i = 0; i < count; i++) {
            byte[] blob = reply.getBytes();
            String comment = reply.getString(StandardCharsets.UTF_8);
            try { out.add(new Identity(new ByteArrayBuffer(blob).getRawPublicKey(), blob, comment)); }
            catch (RuntimeException | org.apache.sshd.common.SshException unsupported) { /* a key type MINA cannot use; skip it */ }
        }
        return List.copyOf(out);
    }

    /** The agent's signature over {@code data} with {@code identity}: the algorithm name and the raw signature. */
    public Map.Entry<String, byte[]> sign(Identity identity, byte[] data, int flags) throws IOException {
        var request = new ByteArrayBuffer();
        request.putByte((byte) SIGN_REQUEST);
        request.putBytes(identity.blob());
        request.putBytes(data);
        request.putInt(flags);
        ByteArrayBuffer reply = request(request.getCompactData(), SIGN_RESPONSE);
        var signature = new ByteArrayBuffer(reply.getBytes());
        return Map.entry(signature.getString(StandardCharsets.UTF_8), signature.getBytes());
    }

    private ByteArrayBuffer request(byte[] message, int expectedType) throws IOException {
        byte[] reply;
        try { reply = exchange.apply(message); }
        catch (UncheckedIOException failure) { throw new IOException("SSH agent not available: " + failure.getCause().getMessage(), failure.getCause()); }
        if (reply.length == 0) throw new IOException("SSH agent sent an empty reply");
        int type = reply[0] & 0xff;
        if (type != expectedType) throw new IOException("SSH agent refused the request (reply type " + type + ")");
        return new ByteArrayBuffer(reply, 1, reply.length - 1);
    }

    static byte[] unixSocket(Path socket, byte[] message) {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            ByteBuffer frame = ByteBuffer.allocate(4 + message.length).putInt(message.length).put(message).flip();
            while (frame.hasRemaining()) channel.write(frame);
            ByteBuffer length = ByteBuffer.allocate(4);
            while (length.hasRemaining()) if (channel.read(length) < 0) throw new IOException("agent closed the connection");
            int size = length.flip().getInt();
            if (size < 1 || size > MAX_REPLY) throw new IOException("agent reply of " + size + " bytes");
            ByteBuffer body = ByteBuffer.allocate(size);
            while (body.hasRemaining()) if (channel.read(body) < 0) throw new IOException("agent closed the connection");
            return body.array();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    static byte[] namedPipe(String pipe, byte[] message) {
        try (var file = new RandomAccessFile(pipe, "rw")) {
            file.write(ByteBuffer.allocate(4 + message.length).putInt(message.length).put(message).array());
            int size = file.readInt();
            if (size < 1 || size > MAX_REPLY) throw new IOException("agent reply of " + size + " bytes");
            byte[] body = new byte[size];
            file.readFully(body);
            return body;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
