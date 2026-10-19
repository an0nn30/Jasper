package dev.jasper.app.residency;

import dev.jasper.app.platform.CodeLocation;
import dev.jasper.app.platform.OwnerOnlyFiles;
import java.io.IOException;
import java.net.StandardProtocolFamily;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * The handoff endpoint (workspace-tabs spec 5.3). The home's owner serves it; every other launch hands its request to it
 * and exits.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, and that is deliberate:
 * AWT shuts its event dispatch thread down once nothing is displayable, so this thread is what keeps
 * a windowless resident JVM alive. Closing the endpoint ends the loop, which is how a resident
 * process with no windows exits.
 */
public final class HandoffSocket implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HandoffSocket.class.getName());
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    private static final int MAX_SOCKET_PATH_BYTES = 100;
    /** An answer is a word or two. */
    private static final int MAX_REPLY_BYTES = 256;
    /** The launch's wait for an answer: the owner reads until its peer has been silent for READ_TIMEOUT_MILLIS, then
     *  gives its event thread up to a second to take the request (spec 5.3), so a request queued behind one stalled
     *  peer is still answered inside it. */
    private static final long HANDOFF_TIMEOUT_MILLIS = 3_000;
    /** How long a peer may stay silent while the accept thread reads from it, so one stalled peer holds the endpoint
     *  for at most this long. A real client writes its line at once; a long line arrives in chunks, each resetting it. */
    private static final long READ_TIMEOUT_MILLIS = 500;
    /** Backoff after an accept-loop failure, so a persistent error (e.g. exhausted file
     *  descriptors) cannot spin the loop at 100% CPU retrying instantly. */
    private static final long ACCEPT_FAILURE_BACKOFF_MILLIS = 50;

    private final ServerSocketChannel channel;
    private final Path socketPath;
    private final Path tokenPath;
    private final Path lockPath;
    private final String token;
    private final Function<LaunchRequest, LaunchRequest.Response> handler;
    private final Thread acceptor;
    private volatile boolean closed;

    private HandoffSocket(ServerSocketChannel channel, Path socketPath, Path tokenPath, Path lockPath,
                          String token, Function<LaunchRequest, LaunchRequest.Response> handler) {
        this.channel = channel;
        this.socketPath = socketPath;
        this.tokenPath = tokenPath;
        this.lockPath = lockPath;
        this.token = token;
        this.handler = handler;
        this.acceptor = Thread.ofPlatform().name("jasper-handoff-accept").unstarted(this::acceptLoop);
    }

    /**
     * Becomes the owner, or returns null. Null means another process already owns the endpoint, or
     * it is unavailable here; residency is an optimization, so failing to get it is never fatal.
     */
    public static HandoffSocket bind(Path socketPath, Path tokenPath, Path lockPath,
                              Function<LaunchRequest, LaunchRequest.Response> handler) {
        if (!pathFits(socketPath)) {
            LOG.log(System.Logger.Level.WARNING, tooLong(socketPath));
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socketPath.getParent());
            OwnerOnlyFiles.restrict(socketPath.getParent(), "rwx------");
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socketPath)) {
                    if (owned(socketPath)) return null;
                    Files.deleteIfExists(socketPath);
                }
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socketPath));
                String token = OwnerOnlyFiles.writeToken(tokenPath);
                OwnerOnlyFiles.writeProcessId(ownerPath(tokenPath));
                var endpoint = new HandoffSocket(opened, socketPath, tokenPath, lockPath, token, handler);
                endpoint.acceptor.start();
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
     * Hands {@code request} to the home's owner, signed with the token beside its socket, and returns the owner's
     * answer: OK or UPDATED when it took the request; TOKEN or PROTOCOL otherwise, including when nothing answers or no
     * token can be read. Blocks, bounded.
     *
     * @param socketPath the owner's endpoint
     * @param tokenPath the token beside it
     * @param request the launch's request; its token is replaced
     * @return the answer
     */
    public static LaunchRequest.Response handOff(Path socketPath, Path tokenPath, LaunchRequest request) {
        String token = readToken(tokenPath);
        if (token == null) return LaunchRequest.Response.PROTOCOL;
        LaunchRequest signed = request.withToken(token);
        var answer = new CompletableFuture<LaunchRequest.Response>();
        // A wedged owner must not hang the caller, so the exchange is bounded from outside it.
        Thread worker = Thread.ofPlatform().daemon().name("jasper-handoff").start(() -> {
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                write(client, signed.encode());
                // The launcher's own budget, not the server's silent-peer tolerance: this reply may
                // legitimately be queued behind a stalled peer, and must not give up just as it
                // is about to be served. The future below is the hard outer bound either way.
                answer.complete(LaunchRequest.Response.of(readLine(client, HANDOFF_TIMEOUT_MILLIS, MAX_REPLY_BYTES)));
            } catch (IOException | RuntimeException failure) {
                answer.complete(LaunchRequest.Response.PROTOCOL);
            }
        });
        try {
            return answer.get(HANDOFF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException failure) {
            worker.interrupt();
            return LaunchRequest.Response.PROTOCOL;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return LaunchRequest.Response.PROTOCOL;
        }
    }

    /**
     * The owner's answer (spec 5.3): {@code taker} is offered each request and whether the launch ran another build than
     * {@code source} at {@code modified}. When it takes the request the answer is UPDATED for another build and OK
     * otherwise; when it does not, PROTOCOL, which the launch treats as no answer and tries again.
     *
     * @param taker takes a request, or declines it; bounded, since the accept thread waits for it
     * @param source this build's code location, or null when unknown
     * @param modified its modification time
     * @return the endpoint's handler
     */
    public static Function<LaunchRequest, LaunchRequest.Response> answering(BiPredicate<LaunchRequest, Boolean> taker,
                                                                            Path source, long modified) {
        Objects.requireNonNull(taker, "taker");
        return request -> {
            boolean updated = request.anotherBuild(source, modified);
            if (!taker.test(request, updated)) return LaunchRequest.Response.PROTOCOL;
            return updated ? LaunchRequest.Response.UPDATED : LaunchRequest.Response.OK;
        };
    }

    /**
     * Whether {@code socketPath} fits a Unix socket address: macOS allows 104 bytes and Jasper keeps to 100.
     *
     * @param socketPath an endpoint's path
     * @return true when it can be bound
     */
    public static boolean pathFits(Path socketPath) {
        return socketPath.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_SOCKET_PATH_BYTES;
    }

    /**
     * What a launch says, and an owner logs, when its home's socket path is too long to bind (plan Ruling 15).
     *
     * @param socketPath the endpoint's path
     * @return the message
     */
    public static String tooLong(Path socketPath) {
        return "Jasper's home folder path is too long for its socket: " + socketPath;
    }

    /** True while some process accepts connections on the endpoint: a plain launch would hand off to it. */
    public static boolean live(Path socketPath) { return owned(socketPath); }

    /**
     * The process id the current owner recorded beside its token, or -1 when there is none or it cannot be read. A
     * hint, not proof: an owner that died without closing leaves its id behind, so only a successful
     * {@link #handOff} shows the owner was live. Windows needs it before the handoff, to pass on the foreground right,
     * and the not-responding error names it.
     */
    public static long ownerPid(Path tokenPath) {
        try {
            long pid = Long.parseLong(Files.readString(ownerPath(tokenPath), StandardCharsets.UTF_8).strip());
            return pid > 0 ? pid : -1;
        } catch (IOException | RuntimeException unavailable) {
            return -1;
        }
    }

    /** Beside the token rather than in it, so a launcher from another build still reads the token it expects. */
    static Path ownerPath(Path tokenPath) {
        return tokenPath.resolveSibling(tokenPath.getFileName() + ".pid");
    }

    /** The jar or classes directory this build was loaded from; null when it cannot be determined. */
    public static Path codeLocation() {
        return CodeLocation.of(HandoffSocket.class);
    }

    /** Epoch milliseconds, or zero when the file cannot be read; zero never equals a real stamp. */
    public static long lastModified(Path path) {
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
        // On Linux a thread blocked in accept() keeps the listening socket alive until it wakes, so our
        // own dying listener could answer the probe below and pass for a successor. Serving is bounded
        // by READ_TIMEOUT_MILLIS, so the acceptor is always gone well within this.
        if (Thread.currentThread() != acceptor) {
            try { acceptor.join(Duration.ofMillis(2 * READ_TIMEOUT_MILLIS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        // Under the same lock bind uses: if anything answers on our path now, a successor owns it
        // and these files are its, not ours.
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (owned(socketPath)) return;
            Files.deleteIfExists(socketPath);
            Files.deleteIfExists(tokenPath);
            Files.deleteIfExists(ownerPath(tokenPath));
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
                // A persistent failure -- accept() itself throwing, e.g. because file descriptors
                // are exhausted -- must not spin this loop at 100% CPU: a long-lived resident
                // process has no window on screen for anyone to notice from. A close() during the
                // sleep is still picked up by the loop guard above, at most this much later.
                try {
                    Thread.sleep(ACCEPT_FAILURE_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void serve(SocketChannel client) throws IOException {
        LaunchRequest request = LaunchRequest.decode(readLine(client, READ_TIMEOUT_MILLIS, LaunchRequest.MAX_LINE_BYTES));
        LaunchRequest.Response response;
        if (request == null) response = LaunchRequest.Response.PROTOCOL;
        else if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                request.token().getBytes(StandardCharsets.UTF_8))) {
            LOG.log(System.Logger.Level.WARNING, "A handoff request presented the wrong token; refused");
            response = LaunchRequest.Response.TOKEN;
        } else response = handler.apply(request);
        try {
            write(client, response.line());
        } catch (IOException peerGone) {
            // A launch that gave up waiting, or a liveness probe that connects and disconnects without writing: from
            // here both look like a peer that hung up before the reply arrived. Ordinary traffic, not a
            // stack-trace-worthy failure; a genuine failure earlier in this method (reading the request) still
            // propagates and is logged as one by acceptLoop.
            LOG.log(System.Logger.Level.DEBUG, "Could not reply to a handoff request; the peer had already gone");
        }
    }

    /** True when something is listening: a connect that succeeds proves a live owner. */
    private static boolean owned(Path socketPath) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

    private static String readToken(Path tokenPath) {
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).strip();
            return token.isEmpty() ? null : token;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Bounded read of one line of at most {@code limit} bytes: it ends when the peer has sent nothing for
     * {@code timeoutMillis}, so a peer that connects and says nothing cannot hold the endpoint, while a long line that
     * keeps arriving is read whole.
     */
    private static String readLine(SocketChannel channel, long timeoutMillis, int limit) throws IOException {
        channel.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(limit);
        long silence = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long deadline = System.nanoTime() + silence;
        int scanned = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) break;
            if (read > 0) deadline = System.nanoTime() + silence;
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
