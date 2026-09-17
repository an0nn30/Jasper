package dev.jasper.app;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.URISyntaxException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * The single-instance endpoint. One resident process owns it; every other launch hands off to it
 * and exits.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, and that is deliberate:
 * AWT shuts its event dispatch thread down once nothing is displayable, so this thread is what keeps
 * a windowless resident JVM alive. Closing the endpoint ends the loop, which is how a resident
 * process with no windows exits.
 */
final class HandoffSocket implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HandoffSocket.class.getName());
    /** A request is five short fields; anything longer is not one of ours. */
    private static final int MAX_LINE_BYTES = 8 * 1024;
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    private static final int MAX_SOCKET_PATH_BYTES = 100;
    private static final long HANDOFF_TIMEOUT_MILLIS = 2_000;
    /** A peer that connects and sends nothing must not hold the endpoint open indefinitely. */
    private static final long READ_TIMEOUT_MILLIS = 2_000;

    private final ServerSocketChannel channel;
    private final Path socketPath;
    private final Path tokenPath;
    private final Path lockPath;
    private final String token;
    private final Function<LaunchRequest, LaunchRequest.Response> handler;
    private volatile boolean closed;

    private HandoffSocket(ServerSocketChannel channel, Path socketPath, Path tokenPath, Path lockPath,
                          String token, Function<LaunchRequest, LaunchRequest.Response> handler) {
        this.channel = channel;
        this.socketPath = socketPath;
        this.tokenPath = tokenPath;
        this.lockPath = lockPath;
        this.token = token;
        this.handler = handler;
    }

    /**
     * Becomes the owner, or returns null. Null means another process already owns the endpoint, or
     * it is unavailable here; residency is an optimization, so failing to get it is never fatal.
     */
    static HandoffSocket bind(Path socketPath, Path tokenPath, Path lockPath,
                              Function<LaunchRequest, LaunchRequest.Response> handler) {
        if (socketPath.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SOCKET_PATH_BYTES) {
            LOG.log(System.Logger.Level.WARNING,
                "Background residency is off: " + socketPath + " is too long for a Unix socket path");
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socketPath.getParent());
            restrict(socketPath.getParent(), "rwx------");
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socketPath)) {
                    if (owned(socketPath)) return null;
                    Files.deleteIfExists(socketPath);
                }
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socketPath));
                String token = writeToken(tokenPath);
                var endpoint = new HandoffSocket(opened, socketPath, tokenPath, lockPath, token, handler);
                Thread.ofPlatform().name("jasper-handoff-accept").start(endpoint::acceptLoop);
                return endpoint;
            }
        } catch (IOException | RuntimeException failure) {
            // A bind that got as far as the socket file but no further must not leave it: the next
            // start would find a path nothing is listening on and have to recover from it.
            if (opened != null) {
                try { opened.close(); } catch (IOException ignored) { }
                try { Files.deleteIfExists(socketPath); } catch (IOException ignored) { }
            }
            LOG.log(System.Logger.Level.WARNING,
                "Background residency is unavailable; running as an ordinary application", failure);
            return null;
        }
    }

    /**
     * Asks a resident process to reveal a window. True only when it replied {@code ok}; no daemon,
     * a stale build, a bad token or a wedged process all return false and leave the caller to start
     * normally.
     */
    static boolean handOff(Path socketPath, Path tokenPath, Path codeSource, long modified) {
        String token = readToken(tokenPath);
        if (token == null) return false;
        // codeSource() is nullable; an unresolvable source compares equal to itself and to nothing else.
        var request = new LaunchRequest(token, codeSource == null ? Path.of("") : codeSource, modified);
        var answer = new CompletableFuture<LaunchRequest.Response>();
        // A wedged owner must not hang the launch, so the exchange is bounded from outside it.
        Thread worker = Thread.ofPlatform().daemon().name("jasper-handoff").start(() -> {
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                write(client, request.encode());
                answer.complete(LaunchRequest.Response.of(readLine(client, READ_TIMEOUT_MILLIS)));
            } catch (IOException | RuntimeException failure) {
                answer.complete(LaunchRequest.Response.PROTOCOL);
            }
        });
        try {
            return answer.get(HANDOFF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) == LaunchRequest.Response.OK;
        } catch (TimeoutException | ExecutionException failure) {
            worker.interrupt();
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The jar or classes directory this build was loaded from; null when it cannot be determined. */
    static Path codeSource() {
        try {
            var source = HandoffSocket.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Epoch milliseconds, or zero when the file cannot be read; zero never equals a real stamp. */
    static long lastModified(Path path) {
        if (path == null) return 0;
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException unavailable) {
            return 0;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
        // Under the same lock bind uses: if anything answers on our path now, a successor owns it
        // and these files are its, not ours.
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (owned(socketPath)) return;
            Files.deleteIfExists(socketPath);
            Files.deleteIfExists(tokenPath);
        } catch (IOException | RuntimeException unavailable) {
            // Including OverlappingFileLockException: leave the files rather than risk a successor's.
        }
    }

    private void acceptLoop() {
        while (!closed) {
            try (SocketChannel client = channel.accept()) {
                serve(client);
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException | RuntimeException failure) {
                if (closed) return;
                LOG.log(System.Logger.Level.WARNING, "A handoff request failed", failure);
            }
        }
    }

    private void serve(SocketChannel client) throws IOException {
        LaunchRequest request = LaunchRequest.decode(readLine(client, READ_TIMEOUT_MILLIS));
        LaunchRequest.Response response;
        if (request == null) response = LaunchRequest.Response.PROTOCOL;
        else if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                request.token().getBytes(StandardCharsets.UTF_8))) {
            LOG.log(System.Logger.Level.WARNING, "A handoff request presented the wrong token; refused");
            response = LaunchRequest.Response.TOKEN;
        } else response = handler.apply(request);
        write(client, response.line());
        // An older build must release the endpoint so the newer launcher can own it. The reply is
        // already written, and the caller starts normally once it reads the refusal.
        if (response == LaunchRequest.Response.STALE) close();
    }

    /** True when something is listening: a connect that succeeds proves a live owner. */
    private static boolean owned(Path socketPath) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

    private static String writeToken(Path tokenPath) throws IOException {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(tokenPath, token + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(tokenPath, "rw-------");
        return token;
    }

    private static String readToken(Path tokenPath) {
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).strip();
            return token.isEmpty() ? null : token;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Owner-only where the filesystem understands it; Windows relies on the per-user directory ACL. */
    private static void restrict(Path path, String permissions) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    /** Bounded read of one line. A peer that connects and says nothing must not hold the endpoint. */
    private static String readLine(SocketChannel channel, long timeoutMillis) throws IOException {
        channel.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(MAX_LINE_BYTES);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        int scanned = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) break;
            for (; scanned < buffer.position(); scanned++) {
                if (buffer.get(scanned) == '\n') {
                    return new String(buffer.array(), 0, scanned, StandardCharsets.UTF_8);
                }
            }
            if (read == 0) {
                if (System.nanoTime() >= deadline) break;
                try { Thread.sleep(5); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
    }

    private static void write(SocketChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
