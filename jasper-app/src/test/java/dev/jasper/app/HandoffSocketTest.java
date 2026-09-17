package dev.jasper.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffSocketTest {
    @TempDir Path dir;

    private Path socket() { return dir.resolve("socket"); }
    private Path token() { return dir.resolve("token"); }
    private Path lock() { return dir.resolve("lock"); }

    private HandoffSocket bind(Function<LaunchRequest, LaunchRequest.Response> handler) {
        HandoffSocket endpoint = HandoffSocket.bind(socket(), token(), lock(), handler);
        assertThat(endpoint).as("bound the endpoint").isNotNull();
        return endpoint;
    }

    @Test void anAcceptedRequestReachesTheHandlerAndTheTokenFileIsOwnerOnly() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 7L)).isTrue();
            assertThat(seen).hasSize(1);
            assertThat(seen.get(0).codeSource()).isEqualTo(Path.of("/app.jar"));
            assertThat(seen.get(0).codeSourceModified()).isEqualTo(7L);
            if (token().getFileSystem().supportedFileAttributeViews().contains("posix")) {
                assertThat(java.nio.file.attribute.PosixFilePermissions
                    .toString(Files.getPosixFilePermissions(token()))).isEqualTo("rw-------");
            }
        }
        assertThat(socket()).doesNotExist();
        assertThat(token()).doesNotExist();
    }

    @Test void aWrongTokenIsRefusedWithoutReachingTheHandler() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            Files.writeString(token(), "not-the-real-token\n");
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 7L)).isFalse();
            assertThat(seen).as("the handler never saw it").isEmpty();
        }
    }

    @Test void aStaleOwnerRefusesAndReleasesTheEndpointSoTheNewBuildCanTakeIt() throws Exception {
        try (HandoffSocket ignored = bind(request -> LaunchRequest.Response.STALE)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/new.jar"), 9L)).isFalse();
            // Releasing is the point: the launcher that was refused must be able to become the owner.
            DesktopTestSupport.until(() -> !Files.exists(socket()));
        }
        try (HandoffSocket second = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/new.jar"), 9L)).isTrue();
        }
    }

    @Test void handingOffToNothingFailsQuietly() {
        // The missing-token case: no socket, no token, nothing was ever bound here. Contrast with
        // aTokenWhoseOwnerWasKilledFailsAtTheConnectRatherThanTheToken below, the dead-listener case.
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isFalse();
    }

    @Test void aTokenWhoseOwnerWasKilledFailsAtTheConnectRatherThanTheToken() throws Exception {
        // The post-SIGKILL state: the token and the socket file both survived, nothing is listening.
        Files.createFile(socket());
        Files.writeString(token(), "left-over-token\n");
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isFalse();
    }

    @Test void anUnresolvableCodeSourceHandsOffInsteadOfCrashingTheLauncher() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(HandoffSocket.handOff(socket(), token(), null, 0L)).isTrue();
            assertThat(seen).singleElement()
                .extracting(LaunchRequest::codeSource).isEqualTo(Path.of(""));
        }
    }

    @Test void aTokenThatCannotBeWrittenDeclinesAndLeavesNoSocketBehind() {
        // The token gates every request, so an endpoint without one must not exist at all.
        assertThat(HandoffSocket.bind(socket(), dir.resolve("absent/directory/token"), lock(),
            request -> LaunchRequest.Response.OK)).isNull();
        assertThat(socket()).as("a half-built endpoint is cleaned up, not left to confuse the next bind")
            .doesNotExist();
    }

    @Test void aSocketFileLeftByACrashIsReplacedButALiveOwnerIsNot() throws Exception {
        Files.createFile(socket());
        Files.writeString(token(), "left-over\n");
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L))
                .as("the stale file was replaced by a working endpoint").isTrue();
            // A second bind must decline rather than steal a live endpoint.
            assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK)).isNull();
        }
    }

    @Test void aLivenessProbeLogsQuietlyRatherThanAlarmingly() throws Exception {
        // The second bind() above finding a live owner is the ordinary upgrade path, and it always
        // probes with owned(): connect and disconnect without writing. From the live owner's side
        // that is indistinguishable from a peer that hung up before the reply arrived, and must not
        // read like a real failure.
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(HandoffSocket.class.getName());
        java.util.logging.Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<java.util.logging.LogRecord> records = new CopyOnWriteArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
                assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK))
                    .isNull();
                DesktopTestSupport.until(() -> !records.isEmpty());
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
        // DEBUG bridges to FINE through java.util.logging; a WARNING (with a stack trace) is what
        // the ordinary path used to log before this fix.
        assertThat(records).as("a normal liveness probe must not log an alarming, stack-trace-bearing WARNING")
            .allSatisfy(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.FINE);
                assertThat(record.getThrown()).as("no stack trace for ordinary traffic").isNull();
            });
    }

    @Test void aPersistentHandlerFailureBacksOffButTheLoopKeepsServing() throws Exception {
        var callCount = new java.util.concurrent.atomic.AtomicInteger();
        int failuresBeforeSuccess = 2;
        try (HandoffSocket endpoint = bind(request -> {
            if (callCount.incrementAndGet() <= failuresBeforeSuccess) {
                // Exercises the same acceptLoop catch block a persistently failing accept() would
                // (e.g. file descriptors exhausted), without needing a fake ServerSocketChannel:
                // the handler throwing before serve() replies is indistinguishable to that loop
                // from accept() itself throwing.
                throw new RuntimeException("simulated failure");
            }
            return LaunchRequest.Response.OK;
        })) {
            long started = System.nanoTime();
            for (int i = 0; i < failuresBeforeSuccess; i++) {
                assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isFalse();
            }
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            // Two failures means one backoff sleep must land strictly between them. The bound is
            // well under the 50ms configured sleep to absorb scheduling jitter, while still failing
            // clearly if the loop is not backing off at all (which completes in a few ms here).
            assertThat(elapsedMillis).as("the loop must back off between failures, not spin").isGreaterThanOrEqualTo(40);
            // And the loop survived the failures: it is still serving, not spun out or wedged.
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isTrue();
            assertThat(callCount.get()).isEqualTo(failuresBeforeSuccess + 1);
        }
    }

    @Test void aPathTooLongForTheOperatingSystemDeclinesInsteadOfThrowing() throws Exception {
        Path deep = dir;
        for (int i = 0; i < 12; i++) deep = deep.resolve("a-directory-with-a-long-name");
        Files.createDirectories(deep);
        assertThat(deep.toString().length()).isGreaterThan(104);
        assertThat(HandoffSocket.bind(deep.resolve("socket"), deep.resolve("token"), deep.resolve("lock"),
            request -> LaunchRequest.Response.OK)).isNull();
    }

    @Test void theCodeSourceOfThisBuildIsResolvableAndItsTimestampIsReadable() {
        Path source = HandoffSocket.codeSource();
        assertThat(source).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(source.isAbsolute()).isTrue();
        assertThat(HandoffSocket.lastModified(source)).isPositive();
        assertThat(HandoffSocket.lastModified(dir.resolve("absent"))).isZero();
    }

    @Test void theAcceptLoopSurvivesAConnectionThatSendsNothingUsable() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            try (var junk = java.nio.channels.SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
                junk.write(java.nio.ByteBuffer.wrap("nonsense\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L))
                .as("still serving after junk").isTrue();
        }
    }

    @Test void aClientThatConnectsAndSaysNothingCannotHoldTheEndpoint() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
             SocketChannel silent = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
            // The silent peer occupies the accept thread until its read deadline expires. Without
            // that deadline this request is never served and the endpoint is wedged for good.
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isTrue();
        }
        // And the endpoint is still usable once the silent peer is gone.
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isTrue();
        }
    }

    @Test void afterClosingNoFurtherRequestIsAccepted() throws Exception {
        HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isTrue();
        endpoint.close();
        endpoint.close(); // Idempotent.
        // Re-create the token, so the refusal comes from the dead listener rather than a missing file.
        Files.writeString(token(), "left-over-token\n");
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isFalse();
        try (ServerSocketChannel proof = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            proof.bind(UnixDomainSocketAddress.of(socket()));  // The path was released.
        }
        Files.deleteIfExists(socket());
    }
}
