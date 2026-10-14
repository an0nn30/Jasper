package dev.jasper.app.commandline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class CommandListenerTest {
    @TempDir Path dir;
    private final List<CommandListener> bound = new ArrayList<>();

    @AfterEach void closeAll() { bound.forEach(CommandListener::close); }

    private Path socket() { return dir.resolve("daemon").resolve("commands"); }

    private CommandListener bind(CommandDispatcher dispatcher) {
        CommandListener listener = CommandListener.bind(socket(), dispatcher);
        assertThat(listener).as("bound the socket").isNotNull();
        bound.add(listener);
        return listener;
    }

    private String token() throws IOException { return Files.readString(CommandListener.tokenFile(socket())).strip(); }

    private String request(String token, List<String> arguments) {
        return new CommandProtocol.Request(token, dir, Optional.empty(), arguments).encode();
    }

    private SocketChannel open() throws IOException { return SocketChannel.open(UnixDomainSocketAddress.of(socket())); }

    private static void send(SocketChannel client, String text) throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (bytes.hasRemaining()) client.write(bytes);
    }

    /** Everything Jasper wrote until it closed the connection. */
    private static String readAll(SocketChannel client) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        while (client.read(buffer.clear()) >= 0) bytes.write(buffer.array(), 0, buffer.position());
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private String exchange(String line) throws IOException {
        try (SocketChannel client = open()) {
            send(client, line);
            return readAll(client);
        }
    }

    private static void until(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out");
            Thread.sleep(10);
        }
    }

    @Test void aCallGetsItsRequestAndItsOutputAndExitCodeReachTheClient() throws Exception {
        var seen = new AtomicReference<Invocation>();
        bind(invocation -> {
            seen.set(invocation);
            invocation.out("hello \u00fc\n");
            invocation.err("careful\n");
            return CompletableFuture.completedFuture(7);
        });
        String replies = exchange(request(token(), List.of("editor", "notes.txt")));
        assertThat(replies).isEqualTo(CommandProtocol.out("hello \u00fc\n") + CommandProtocol.err("careful\n") + CommandProtocol.exit(7));
        assertThat(seen.get().arguments()).containsExactly("editor", "notes.txt");
        assertThat(seen.get().workingDirectory()).isEqualTo(dir);
        assertThat(seen.get().pane()).isEmpty();
    }

    @Test void theTokenIsOwnerOnlyAndNamesItsProcess() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(0));
        Path token = CommandListener.tokenFile(socket());
        assertThat(token()).hasSizeGreaterThan(30);
        assertThat(Files.readString(CommandListener.ownerFile(socket())).strip())
            .isEqualTo(Long.toString(ProcessHandle.current().pid()));
        if (token.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(token))).isEqualTo("rw-------");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(socket().getParent()))).isEqualTo("rwx------");
        }
    }

    @Test void aWrongTokenIsRefusedAndAnotherVersionIsStale() throws Exception {
        var calls = new AtomicInteger();
        bind(invocation -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(0); });
        assertThat(exchange(request("not-the-token", List.of("editor")))).isEqualTo(CommandProtocol.REFUSED);
        assertThat(exchange("jasper-command\t2\t" + token() + "\n")).isEqualTo(CommandProtocol.STALE);
        assertThat(calls).hasValue(0);
    }

    @Test void somethingThatIsNotARequestIsDroppedUnanswered() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(exchange("GET / HTTP/1.1\r\n")).isEmpty();
        assertThat(exchange("jasper-command\t1\t" + token() + "\tnot base64\t\n")).isEmpty();
        assertThat(exchange("x".repeat(CommandProtocol.MAX_REQUEST_BYTES))).as("no newline within the limit").isEmpty();
    }

    @Test void onlyOneProcessServesTheSocketAndClosingRemovesIt() throws Exception {
        CommandListener first = bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(CommandListener.live(socket())).isTrue();
        assertThat(CommandListener.bind(socket(), invocation -> CompletableFuture.completedFuture(0))).isNull();
        first.close();
        assertThat(CommandListener.live(socket())).isFalse();
        assertThat(socket()).doesNotExist();
        assertThat(CommandListener.tokenFile(socket())).doesNotExist();
        assertThat(CommandListener.ownerFile(socket())).doesNotExist();
        bind(invocation -> CompletableFuture.completedFuture(0));
    }

    @Test void aSocketFileNobodyServesIsReplaced() throws Exception {
        Files.createDirectories(socket().getParent());
        Files.writeString(socket(), "left behind by a crash");
        bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(CommandListener.live(socket())).isTrue();
    }

    @Test void aPrivateSocketTakesItsLockFileAlongWhenItCloses() throws Exception {
        Path socket = dir.resolve("daemon").resolve("commands-42");
        CommandListener listener = CommandListener.bind(socket, invocation -> CompletableFuture.completedFuture(0));
        assertThat(listener).isNotNull();
        listener.close();
        try (var listing = Files.list(socket.getParent())) { assertThat(listing).isEmpty(); }
    }

    @Test void aCallerThatGoesAwayCancelsItsCall() throws Exception {
        var cancelled = new CountDownLatch(1);
        var started = new CompletableFuture<Invocation>();
        var pending = new CompletableFuture<Integer>();
        CommandListener listener = bind(invocation -> {
            invocation.onCancelled(cancelled::countDown);
            started.complete(invocation);
            return pending;
        });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("editor", "--wait")));
            started.get(10, TimeUnit.SECONDS);
            assertThat(listener.activeCalls()).isEqualTo(1);
        }
        assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(started.get().cancelled()).isTrue();
        pending.complete(0);
        assertThat(listener.activeCalls()).isZero();
    }

    @Test void callsRunTogetherAndIdleIsToldWhenTheLastOneEnds() throws Exception {
        List<CompletableFuture<Integer>> pending = java.util.Collections.synchronizedList(new ArrayList<>());
        var idle = new AtomicInteger();
        CommandListener listener = bind(invocation -> {
            var call = new CompletableFuture<Integer>();
            pending.add(call);
            return call;
        });
        listener.onIdle(idle::incrementAndGet);
        try (SocketChannel a = open(); SocketChannel b = open()) {
            send(a, request(token(), List.of("one")));
            send(b, request(token(), List.of("two")));
            until(() -> pending.size() == 2);
            assertThat(listener.activeCalls()).isEqualTo(2);
            pending.get(0).complete(0);
            assertThat(idle).hasValue(0);
            pending.get(1).complete(4);
            assertThat(idle).hasValue(1);
            assertThat(List.of(readAll(a), readAll(b))).containsExactlyInAnyOrder(CommandProtocol.exit(0), CommandProtocol.exit(4));
        }
    }

    @Test void aDispatcherThatThrowsOrFailsExitsOne() throws Exception {
        bind(invocation -> {
            if (invocation.arguments().getFirst().equals("throw")) throw new IllegalStateException("boom");
            return CompletableFuture.failedFuture(new IllegalStateException("disk full"));
        });
        assertThat(exchange(request(token(), List.of("throw")))).isEqualTo(CommandProtocol.err("jasper: boom\n") + CommandProtocol.exit(1));
        assertThat(exchange(request(token(), List.of("fail")))).isEqualTo(CommandProtocol.err("jasper: disk full\n") + CommandProtocol.exit(1));
    }

    @Test void aHandlerNeverWaitsForAClientThatHasStoppedReading() throws Exception {
        // About 2.8 MiB of frames, far more than the socket buffers hold while the client reads nothing.
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < 32; i++) chunks.add("chunk " + i + " " + "x".repeat(64 * 1024) + "\n");
        var returned = new CompletableFuture<Void>();
        bind(invocation -> {
            chunks.forEach(invocation::out);
            returned.complete(null);
            return CompletableFuture.completedFuture(3);
        });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("flood")));
            returned.get(5, TimeUnit.SECONDS);
            String expected = String.join("", chunks.stream().map(CommandProtocol::out).toList()) + CommandProtocol.exit(3);
            String replies = readAll(client);
            // Not isEqualTo: a failure would print megabytes.
            assertThat(replies.equals(expected)).as("every output frame in order, then the exit frame last").isTrue();
        }
    }

    @Test void closingEndsTheCallsStillRunning() throws Exception {
        var started = new CompletableFuture<Void>();
        CommandListener listener = bind(invocation -> { started.complete(null); return new CompletableFuture<>(); });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("editor", "--wait")));
            started.get(10, TimeUnit.SECONDS);
            listener.close();
            assertThat(readAll(client)).as("no exit frame, so the client says Jasper quit").isEmpty();
        }
    }

    @Test void aSocketPathTooLongForTheOperatingSystemLeavesTheCommandUnavailable() {
        Path deep = dir.resolve("x".repeat(120)).resolve("commands");
        assertThat(CommandListener.bind(deep, invocation -> CompletableFuture.completedFuture(0))).isNull();
    }
}
