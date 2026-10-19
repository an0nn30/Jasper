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
import java.time.Duration;
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
@org.junit.jupiter.api.extension.ExtendWith(CommandListenerTest.VirtualThreadDump.class)
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

    @Test void aConnectionThatHasNotSentItsRequestYetKeepsTheListenerBusy() throws Exception {
        var idle = new AtomicInteger();
        CommandListener listener = bind(invocation -> CompletableFuture.completedFuture(0));
        listener.onIdle(idle::incrementAndGet);
        try (SocketChannel waiting = open()) {
            until(listener::busy);
            assertThat(exchange(request(token(), List.of("one")))).isEqualTo(CommandProtocol.exit(0));
            assertThat(idle).as("another client has connected but not sent its request").hasValue(0);
            assertThat(listener.busy()).isTrue();
        }
        until(() -> idle.get() == 1);
        assertThat(listener.busy()).isFalse();
    }

    @Test void aConnectionThatEndsBeforeAnyCallHasRunLeavesIdleQuiet() throws Exception {
        var idle = new AtomicInteger();
        CommandListener listener = bind(invocation -> CompletableFuture.completedFuture(0));
        listener.onIdle(idle::incrementAndGet);
        try (SocketChannel probe = open()) {
            until(listener::busy);
        }
        until(() -> !listener.busy());
        assertThat(idle).as("another launch's liveness probe, before the first call").hasValue(0);
        assertThat(exchange(request(token(), List.of("one")))).isEqualTo(CommandProtocol.exit(0));
        until(() -> idle.get() == 1);
    }

    @Test void anExitCodeOutsideZeroTo255IsSentAsOne() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(Integer.parseInt(invocation.arguments().getFirst())));
        assertThat(exchange(request(token(), List.of("300")))).isEqualTo(CommandProtocol.exit(1));
        assertThat(exchange(request(token(), List.of("-1")))).isEqualTo(CommandProtocol.exit(1));
        assertThat(exchange(request(token(), List.of("255")))).isEqualTo(CommandProtocol.exit(255));
    }

    @Test void repeatedImmediateRepliesAlwaysDeliverTheirExitFrame() throws Exception {
        bind(invocation -> CompletableFuture.completedFuture(Integer.parseInt(invocation.arguments().getFirst())));
        for (int round = 0; round < 100; round++) {
            for (int code : new int[] {300, -1, 255}) {
                assertThat(exchange(request(token(), List.of(Integer.toString(code)))))
                    .as("round %s, exit %s", round, code)
                    .isEqualTo(CommandProtocol.exit(code == 255 ? 255 : 1));
            }
        }
    }

    /** JUnit's standard thread dump omits virtual threads, including the command reader and writer. */
    static final class VirtualThreadDump implements org.junit.jupiter.api.extension.PreInterruptCallback {
        @Override public void beforeThreadInterrupt(org.junit.jupiter.api.extension.PreInterruptContext interrupted,
                org.junit.jupiter.api.extension.ExtensionContext context) throws Exception {
            Path output = Path.of("build/reports/tests/command-listener-threads").toAbsolutePath();
            Files.createDirectories(output);
            Path dump = Files.createTempFile(output, "threads-", ".txt");
            String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
            Process diagnostic = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "jcmd" + suffix).toString(),
                Long.toString(ProcessHandle.current().pid()), "Thread.dump_to_file", "-overwrite", dump.toString())
                .redirectErrorStream(true).redirectOutput(output.resolve("jcmd.log").toFile()).start();
            try {
                if (diagnostic.waitFor(5, TimeUnit.SECONDS)) System.out.println(Files.readString(dump));
                else System.out.println("Virtual thread dump timed out: " + dump);
            } finally { diagnostic.destroyForcibly(); }
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

    /** About 2.8 MiB of frames once encoded, far more than the socket buffers hold while a client reads nothing. */
    private static List<String> floodChunks() {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < 32; i++) chunks.add("chunk " + i + " " + "x".repeat(64 * 1024) + "\n");
        return chunks;
    }

    private static String frames(List<String> chunks, int exitCode) {
        return String.join("", chunks.stream().map(CommandProtocol::out).toList()) + CommandProtocol.exit(exitCode);
    }

    @Test void aHandlerNeverWaitsForAClientThatHasStoppedReading() throws Exception {
        List<String> chunks = floodChunks();
        var returned = new CompletableFuture<Void>();
        bind(invocation -> {
            chunks.forEach(invocation::out);
            returned.complete(null);
            return CompletableFuture.completedFuture(3);
        });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("flood")));
            returned.get(5, TimeUnit.SECONDS);
            String replies = readAll(client);
            // Not isEqualTo: a failure would print megabytes.
            assertThat(replies.equals(frames(chunks, 3))).as("every output frame in order, then the exit frame last").isTrue();
        }
    }

    @Test void closingEndsTheCallsStillRunningWithoutWaitingForThem() throws Exception {
        var started = new CompletableFuture<Void>();
        CommandListener listener = bind(invocation -> { started.complete(null); return new CompletableFuture<>(); });
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("editor", "--wait")));
            started.get(10, TimeUnit.SECONDS);
            long begun = System.nanoTime();
            listener.close();
            assertThat(Duration.ofNanos(System.nanoTime() - begun)).as("a running call is not waited for").isLessThan(Duration.ofSeconds(2));
            assertThat(readAll(client)).as("no exit frame, so the client says Jasper quit").isEmpty();
        }
    }

    @Test void closingRightAfterACallEndsStillDeliversItsExitFrame() throws Exception {
        // A non-resident Jasper quits as its last call ends: the exit frame is queued, not yet written, when close() runs.
        List<String> chunks = floodChunks();
        var queued = new CompletableFuture<Void>();
        var pending = new CompletableFuture<Integer>();
        CommandListener listener = bind(invocation -> {
            chunks.forEach(invocation::out);
            queued.complete(null);
            return pending;
        });
        // Jasper closes the listener once it reports its last call over, as these tests do.
        var over = new CompletableFuture<Void>();
        listener.onIdle(() -> over.complete(null));
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("editor", "--wait")));
            var received = new CompletableFuture<String>();
            Thread.ofVirtual().start(() -> {
                try { received.complete(readAll(client)); } catch (IOException | RuntimeException failure) { received.completeExceptionally(failure); }
            });
            queued.get(10, TimeUnit.SECONDS);
            pending.complete(3);
            over.get(10, TimeUnit.SECONDS);
            listener.close();
            assertThat(received.get(10, TimeUnit.SECONDS).equals(frames(chunks, 3)))
                .as("every output frame in order, then the exit frame last, although the listener closed at once").isTrue();
        }
    }

    @Test void closingWaitsOnlyAMomentForAClientThatReadsNothing() throws Exception {
        List<String> chunks = floodChunks();
        var queued = new CompletableFuture<Void>();
        CommandListener listener = bind(invocation -> {
            chunks.forEach(invocation::out);
            queued.complete(null);
            return CompletableFuture.completedFuture(0);
        });
        var over = new CompletableFuture<Void>();
        listener.onIdle(() -> over.complete(null));
        try (SocketChannel client = open()) {
            send(client, request(token(), List.of("flood")));
            queued.get(10, TimeUnit.SECONDS);
            over.get(10, TimeUnit.SECONDS);
            long begun = System.nanoTime();
            listener.close();
            assertThat(Duration.ofNanos(System.nanoTime() - begun)).as("the grace for finished calls is bounded").isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test void aSocketPathTooLongForTheOperatingSystemLeavesTheCommandUnavailable() {
        Path deep = dir.resolve("x".repeat(120)).resolve("commands");
        assertThat(CommandListener.bind(deep, invocation -> CompletableFuture.completedFuture(0))).isNull();
    }

    /** jasper_cli_core.h's JC_MAX_SOCKET_PATH is the same 100 bytes. */
    @Test void aHundredBytesIsTheLongestSocketPath() {
        int room = CommandListener.MAX_SOCKET_PATH_BYTES - dir.toString().length() - 1;
        org.junit.jupiter.api.Assumptions.assumeTrue(room > 0, "the temporary folder leaves room for a name");
        CommandListener longest = CommandListener.bind(dir.resolve("x".repeat(room)), invocation -> CompletableFuture.completedFuture(0));
        assertThat(longest).isNotNull();
        bound.add(longest);
        assertThat(CommandListener.bind(dir.resolve("y".repeat(room + 1)), invocation -> CompletableFuture.completedFuture(0))).isNull();
    }
}
