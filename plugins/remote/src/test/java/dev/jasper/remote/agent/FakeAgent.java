package dev.jasper.remote.agent;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

/** An ssh-agent that holds JDK key pairs and answers the two requests the plugin sends. */
public final class FakeAgent implements AutoCloseable {
    public final List<KeyPair> keys = new ArrayList<>();
    public final List<Integer> flagsSeen = new ArrayList<>();
    private ServerSocketChannel server;
    private Thread thread;
    private Path socket;

    public FakeAgent(KeyPair... pairs) { keys.addAll(List.of(pairs)); }

    /** The reply to one unframed request, unframed. */
    public byte[] handle(byte[] request) {
        try {
            var in = new ByteArrayBuffer(request);
            int type = in.getByte() & 0xff;
            var out = new ByteArrayBuffer();
            if (type == 11) {
                out.putByte((byte) 12);
                out.putInt(keys.size());
                for (KeyPair pair : keys) { out.putBytes(blob(pair.getPublic())); out.putString("fake " + KeyUtils.getKeyType(pair.getPublic())); }
            } else if (type == 13) {
                byte[] blob = in.getBytes(); byte[] data = in.getBytes(); int flags = in.getInt();
                flagsSeen.add(flags);
                PublicKey key = new ByteArrayBuffer(blob).getRawPublicKey();
                KeyPair pair = keys.stream().filter(candidate -> KeyUtils.findMatchingKey(key, List.of(candidate.getPublic())) != null).findFirst().orElse(null);
                if (pair == null) { out.putByte((byte) 5); return out.getCompactData(); }
                String algorithm; Signature signature;
                if (key.getAlgorithm().equals("RSA")) {
                    algorithm = (flags & 4) != 0 ? "rsa-sha2-512" : (flags & 2) != 0 ? "rsa-sha2-256" : "ssh-rsa";
                    signature = Signature.getInstance((flags & 4) != 0 ? "SHA512withRSA" : (flags & 2) != 0 ? "SHA256withRSA" : "SHA1withRSA");
                } else { algorithm = "ssh-ed25519"; signature = Signature.getInstance("Ed25519", new org.bouncycastle.jce.provider.BouncyCastleProvider()); }
                signature.initSign(pair.getPrivate());
                signature.update(data);
                var sig = new ByteArrayBuffer();
                sig.putString(algorithm); sig.putBytes(signature.sign());
                out.putByte((byte) 14); out.putBytes(sig.getCompactData());
            } else out.putByte((byte) 5);
            return out.getCompactData();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    static byte[] blob(PublicKey key) { var buffer = new ByteArrayBuffer(); buffer.putRawPublicKey(key); return buffer.getCompactData(); }

    /** Serves the agent protocol on a Unix-domain socket until closed; one request per connection, like the real client. */
    public void serveUnixSocket(Path socket) throws IOException {
        this.socket = socket;
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        thread = Thread.ofPlatform().daemon().start(() -> {
            while (server.isOpen()) {
                try (SocketChannel channel = server.accept()) {
                    while (true) {
                        ByteBuffer length = ByteBuffer.allocate(4);
                        while (length.hasRemaining()) if (channel.read(length) < 0) throw new IOException("closed");
                        length.flip();
                        ByteBuffer body = ByteBuffer.allocate(length.getInt());
                        while (body.hasRemaining()) if (channel.read(body) < 0) throw new IOException("closed");
                        byte[] reply = handle(body.array());
                        ByteBuffer frame = ByteBuffer.allocate(4 + reply.length).putInt(reply.length).put(reply).flip();
                        while (frame.hasRemaining()) channel.write(frame);
                    }
                } catch (IOException ended) { /* next connection */ }
            }
        });
    }

    @Override public void close() throws IOException { if (server != null) server.close(); if (socket != null) java.nio.file.Files.deleteIfExists(socket); }

    static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
}
