package dev.jasper.app.commandline;

import java.io.ByteArrayOutputStream;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The command socket: each connection carries one {@link CommandProtocol.Request} and gets reply frames until the
 * call's exit code. Bound whenever Jasper runs, resident or not.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, so a Jasper started with
 * {@code --no-window} stays up to serve its first call; every quit path ends the process with {@code System.exit}.
 * Each connection is served on its own virtual thread, because a call such as {@code jasper editor --wait} lasts as
 * long as the user edits. Closing ends the accept loop and the connections of calls still running.
 */
public final class CommandListener implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(CommandListener.class.getName());
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    static final int MAX_SOCKET_PATH_BYTES = 100;
    /** How long a peer may take to send its request line; the client writes it at once. */
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000;
    /** Backoff after an accept failure, so a persistent error cannot spin the loop. */
    private static final long ACCEPT_FAILURE_BACKOFF_MILLIS = 50;

    private final ServerSocketChannel channel;
    private final Path socket;
    private final String token;
    private final CommandDispatcher dispatcher;
    private final Thread acceptor;
    private final Set<SocketChannel> connections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private final List<Runnable> idle = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    private CommandListener(ServerSocketChannel channel, Path socket, String token, CommandDispatcher dispatcher) {
        this.channel = channel;
        this.socket = socket;
        this.token = token;
        this.dispatcher = dispatcher;
        this.acceptor = Thread.ofPlatform().name("jasper-command-accept").unstarted(this::acceptLoop);
    }

    /**
     * Binds the socket, or returns null when another process already serves it or it is unavailable here: the
     * {@code jasper} command is a convenience, so failing to bind is never fatal. The token is written before the
     * socket appears, so a client that finds the socket can read it.
     */
    public static CommandListener bind(Path socket, CommandDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        if (socket.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SOCKET_PATH_BYTES) {
            LOG.log(System.Logger.Level.WARNING,
                "The jasper command is unavailable: " + socket + " is too long for a Unix socket path");
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socket.getParent());
            restrict(socket.getParent(), "rwx------");
            try (FileChannel lockChannel = FileChannel.open(lockFile(socket), StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socket)) {
                    if (live(socket)) return null;
                    Files.deleteIfExists(socket);
                }
                String token = writeToken(tokenFile(socket));
                writeOwner(ownerFile(socket));
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socket));
                var listener = new CommandListener(opened, socket, token, dispatcher);
                listener.acceptor.start();
                return listener;
            }
        } catch (IOException | RuntimeException failure) {
            // A bind that got as far as the socket file but no further must not leave it behind.
            if (opened != null) {
                try { opened.close(); } catch (IOException ignored) { }
                try { Files.deleteIfExists(socket); } catch (IOException ignored) { }
            }
            LOG.log(System.Logger.Level.WARNING, "The jasper command is unavailable", failure);
            return null;
        }
    }

    /** True while some process accepts connections on the socket. */
    public static boolean live(Path socket) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

    /** The token file beside a socket, which the client sends back. */
    public static Path tokenFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".token"); }

    /** The owner's process id beside its token; a Windows client passes it the right to come to the front. */
    static Path ownerFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".token.pid"); }

    static Path lockFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".lock"); }

    /** The socket this listener serves. */
    public Path socket() { return socket; }

    /** Calls whose exit code is not known yet, including those whose caller went away. */
    public int activeCalls() { return active.get(); }

    /** Runs {@code listener} each time the last running call ends, on the thread that ended it. */
    public void onIdle(Runnable listener) { idle.add(Objects.requireNonNull(listener, "listener")); }

    private void acceptLoop() {
        while (!closed) {
            try {
                SocketChannel client = channel.accept();
                Thread.ofVirtual().name("jasper-command-call").start(() -> serve(client));
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException | RuntimeException failure) {
                if (closed) return;
                LOG.log(System.Logger.Level.WARNING, "A jasper command connection could not be accepted", failure);
                try {
                    Thread.sleep(ACCEPT_FAILURE_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void serve(SocketChannel client) {
        connections.add(client);
        try {
            // close() may have run between the accept and here, and would have missed this connection.
            if (closed) return;
            String line = readRequest(client);
            if (line == null) return;
            int version = CommandProtocol.version(line);
            if (version < 0) return;
            if (version != CommandProtocol.VERSION) {
                send(client, client, CommandProtocol.STALE);
                return;
            }
            CommandProtocol.Request request = CommandProtocol.decode(line);
            if (request == null) return;
            if (!MessageDigest.isEqual(request.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                send(client, client, CommandProtocol.REFUSED);
                return;
            }
            call(client, request);
        } catch (IOException | RuntimeException failure) {
            if (!closed) LOG.log(System.Logger.Level.DEBUG, "A jasper command connection failed", failure);
        } finally {
            connections.remove(client);
            closeQuietly(client);
        }
    }

    private void call(SocketChannel client, CommandProtocol.Request request) {
        Object writing = new Object();
        var invocation = new Invocation(request.arguments(), request.workingDirectory(), request.pane(),
            text -> send(client, writing, CommandProtocol.out(text)), text -> send(client, writing, CommandProtocol.err(text)));
        var ended = new CompletableFuture<Void>();
        active.incrementAndGet();
        CompletionStage<Integer> stage;
        try {
            stage = Objects.requireNonNull(dispatcher.dispatch(invocation), "the dispatcher's stage");
        } catch (RuntimeException failure) {
            stage = CompletableFuture.failedFuture(failure);
        }
        stage.whenComplete((code, failure) -> {
            int exit = failure != null ? failed(invocation, failure) : code == null ? 1 : code;
            invocation.finish();
            send(client, writing, CommandProtocol.exit(exit));
            ended.complete(null);
            closeQuietly(client);
            if (active.decrementAndGet() == 0) {
                for (Runnable listener : idle) {
                    try { listener.run(); }
                    catch (RuntimeException broken) { LOG.log(System.Logger.Level.WARNING, "An idle listener failed", broken); }
                }
            }
        });
        // The client sends nothing after its request, so a read returns only once it goes away or the call has ended.
        ByteBuffer discard = ByteBuffer.allocate(256);
        try {
            while (!ended.isDone() && client.read(discard.clear()) >= 0) { }
        } catch (IOException closedOrGone) {
            // Closed by the completion above, or the client vanished.
        }
        if (!ended.isDone()) invocation.cancel();
    }

    private static int failed(Invocation invocation, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        LOG.log(System.Logger.Level.WARNING, "A jasper command call failed", cause);
        invocation.err("jasper: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()) + "\n");
        return 1;
    }

    /** The request line without its newline, or null when the peer sent none in time or within the limit. */
    private static String readRequest(SocketChannel client) throws IOException {
        client.configureBlocking(false);
        var bytes = new ByteArrayOutputStream();
        ByteBuffer chunk = ByteBuffer.allocate(8 * 1024);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_TIMEOUT_MILLIS);
        while (true) {
            int read = client.read(chunk.clear());
            if (read < 0) return null;
            for (int i = 0; i < read; i++) {
                byte next = chunk.get(i);
                if (next == '\n') {
                    client.configureBlocking(true);
                    return bytes.toString(StandardCharsets.UTF_8);
                }
                bytes.write(next);
                if (bytes.size() >= CommandProtocol.MAX_REQUEST_BYTES) return null;
            }
            if (read == 0) {
                if (System.nanoTime() >= deadline) return null;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    private static void send(SocketChannel client, Object lock, String frame) {
        ByteBuffer bytes = ByteBuffer.wrap(frame.getBytes(StandardCharsets.UTF_8));
        synchronized (lock) {
            try {
                while (bytes.hasRemaining()) client.write(bytes);
            } catch (IOException gone) {
                // The client went away; its connection's reader notices.
            }
        }
    }

    private static void closeQuietly(SocketChannel client) {
        try { client.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
        if (Thread.currentThread() != acceptor) {
            try {
                acceptor.join(Duration.ofSeconds(1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        for (SocketChannel connection : List.copyOf(connections)) closeQuietly(connection);
        try (FileChannel lockChannel = FileChannel.open(lockFile(socket), StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (live(socket)) return;
            Files.deleteIfExists(socket);
            Files.deleteIfExists(tokenFile(socket));
            Files.deleteIfExists(ownerFile(socket));
        } catch (IOException | RuntimeException unavailable) {
            // Including OverlappingFileLockException: leave the files rather than risk a successor's.
            return;
        }
        // A private launch's socket is named for its process, so nothing else ever takes its lock.
        if (socket.getFileName().toString().startsWith("commands-")) {
            try { Files.deleteIfExists(lockFile(socket)); } catch (IOException ignored) { }
        }
    }

    // The same owner-only files as HandoffSocket's, kept here so neither package depends on the other.
    private static String writeToken(Path file) throws IOException {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(file, token + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(file, "rw-------");
        return token;
    }

    private static void writeOwner(Path file) throws IOException {
        Files.writeString(file, ProcessHandle.current().pid() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(file, "rw-------");
    }

    /** Owner-only where the file system understands it; Windows relies on the per-user directory's ACL. */
    private static void restrict(Path path, String permissions) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }
}
