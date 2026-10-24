package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandoffSocketTest {
    @TempDir Path dir;

    private Path socket() { return dir.resolve("socket"); }
    private Path token() { return dir.resolve("token"); }

    private HandoffSocket bind(Function<LaunchRequest, LaunchRequest.Response> handler) {
        HandoffSocket endpoint = HandoffSocket.bind(socket(), token(), handler);
        assertThat(endpoint).as("bound the endpoint").isNotNull();
        return endpoint;
    }

    /** A plain launch of build {@code /app.jar} at {@code modified}, run in this test's folder. */
    private LaunchRequest launch(long modified) { return LaunchRequest.of(Path.of("/app.jar"), modified, dir, List.of(), List.of()); }

    private LaunchRequest.Response handOff(LaunchRequest request) { return HandoffSocket.handOff(socket(), token(), request); }

    @Test void anAcceptedRequestReachesTheHandlerAndTheTokenFileIsOwnerOnly() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(launch(7L))).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).singleElement().satisfies(request -> {
                assertThat(request.codeLocation()).isEqualTo(Path.of("/app.jar"));
                assertThat(request.codeLocationModified()).isEqualTo(7L);
                assertThat(request.intent()).isEqualTo(LaunchRequest.Intent.RAISE);
                assertThat(request.workingDirectory()).isEqualTo(dir);
            });
            if (token().getFileSystem().supportedFileAttributeViews().contains("posix")) {
                assertThat(java.nio.file.attribute.PosixFilePermissions
                    .toString(Files.getPosixFilePermissions(token()))).isEqualTo("rw-------");
            }
        }
        assertThat(socket()).doesNotExist();
        assertThat(token()).doesNotExist();
    }

    @Test void anOwnerLeavesOnlyItsSocketAndItsTokenInTheFolder() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
             var listing = Files.list(dir)) {
            assertThat(listing.map(entry -> entry.getFileName().toString()))
                .as("no lock file and no process-id file beside them").containsExactlyInAnyOrder("socket", "token");
        }
    }

    @Test void aPathsRequestArrivesWhole() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        var request = LaunchRequest.of(Path.of("/app.jar"), 7L, dir, List.of(dir.resolve("notes ü.txt"), dir), List.of("--safe-mode"));
        try (HandoffSocket endpoint = bind(received -> { seen.add(received); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(request)).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).containsExactly(request.withToken(Files.readString(token()).strip()));
        }
    }

    @Test void aWrongTokenIsRefusedWithoutReachingTheHandler() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            Files.writeString(token(), "not-the-real-token\n");
            assertThat(handOff(launch(7L))).isEqualTo(LaunchRequest.Response.TOKEN);
            assertThat(seen).as("the handler never saw it").isEmpty();
        }
    }

    @Test void anUpdatedAnswerReachesTheLaunchAndTheEndpointStays() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.UPDATED)) {
            assertThat(handOff(launch(9L))).isEqualTo(LaunchRequest.Response.UPDATED);
            assertThat(handOff(launch(9L))).as("an update never releases the endpoint").isEqualTo(LaunchRequest.Response.UPDATED);
            assertThat(socket()).exists();
        }
    }

    @Test void theOwnersAnswerIsUpdatedForAnotherBuildAndProtocolWhenItDidNotTakeTheRequest() {
        var seen = new ConcurrentLinkedQueue<String>();
        var answer = HandoffSocket.answering((request, updated) -> seen.add(request.intent() + " " + updated), Path.of("/app.jar"), 7L);
        assertThat(answer.apply(LaunchRequest.of(Path.of("/app.jar"), 7L, dir, List.of(dir), List.of())))
            .isEqualTo(LaunchRequest.Response.OK);
        assertThat(answer.apply(LaunchRequest.of(Path.of("/new.jar"), 9L, dir, List.of(), List.of("--safe-mode"))))
            .isEqualTo(LaunchRequest.Response.UPDATED);
        assertThat(seen).containsExactly("OPEN_PATHS false", "RAISE true");
        var declined = HandoffSocket.answering((request, updated) -> false, Path.of("/app.jar"), 7L);
        assertThat(declined.apply(LaunchRequest.of(Path.of("/new.jar"), 9L, dir, List.of(), List.of())))
            .as("an owner that did not take the request has not answered (spec 5.3)").isEqualTo(LaunchRequest.Response.PROTOCOL);
    }

    @Test void aRequestNearTheLineLimitArrivesWhole() throws Exception {
        // The owner reads in chunks as they come: only 500 ms without progress ends a read, never its total time.
        var paths = new java.util.ArrayList<Path>();
        int encoded = 0;
        for (int i = 0; encoded < LaunchRequest.MAX_LINE_BYTES - 64 * 1024; i++) {
            Path path = dir.resolve("a-file-with-a-rather-long-name-to-fill-the-line-" + i + ".txt");
            paths.add(path);
            encoded += java.util.Base64.getEncoder().encodeToString(path.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).length() + 1;
        }
        var request = LaunchRequest.of(Path.of("/app.jar"), 7L, dir, paths, List.of());
        assertThat(request.encode().length()).isBetween(LaunchRequest.MAX_LINE_BYTES - 64 * 1024, LaunchRequest.MAX_LINE_BYTES);
        assertThat(request.fits()).isTrue();
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(received -> { seen.add(received); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(request)).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).hasSize(1);
            assertThat(seen.peek().paths()).isEqualTo(paths);
        }
    }

    @Test void handingOffToNothingFailsQuietly() {
        // No socket, no token, nothing was ever bound here. Contrast with the killed owner below.
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
    }

    @Test void aTokenWhoseOwnerWasKilledFailsAtTheConnectRatherThanTheToken() throws Exception {
        // The post-SIGKILL state: the token and the socket file both survived, nothing is listening.
        Files.createFile(socket());
        Files.writeString(token(), "left-over-token\n");
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
    }

    @Test void anUnknownCodeLocationHandsOffInsteadOfCrashingTheLauncher() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(LaunchRequest.of(null, 0L, dir, List.of(), List.of()))).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).singleElement().extracting(LaunchRequest::codeLocation).isEqualTo(Path.of(""));
        }
    }

    @Test void aTokenThatCannotBeWrittenDeclinesAndLeavesNoSocketBehind() {
        // The token gates every request, so an endpoint without one must not exist at all.
        assertThat(HandoffSocket.bind(socket(), dir.resolve("absent/directory/token"), request -> LaunchRequest.Response.OK)).isNull();
        assertThat(socket()).as("a half-built endpoint is cleaned up, not left to confuse the next bind").doesNotExist();
    }

    @Test void aSocketFileLeftByACrashIsReplaced() throws Exception {
        Files.createFile(socket());
        Files.writeString(token(), "left-over\n");
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(handOff(launch(1L))).as("the stale file was replaced by a working endpoint").isEqualTo(LaunchRequest.Response.OK);
        }
    }

    @Test void aPeerThatHangsUpBeforeTheReplyLogsQuietlyRatherThanAlarmingly() throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(HandoffSocket.class.getName());
        java.util.logging.Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        var records = new ConcurrentLinkedQueue<java.util.logging.LogRecord>();
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
                SocketChannel.open(UnixDomainSocketAddress.of(socket())).close();
                // One acceptor serves in order: a real reply proves the peer that hung up was served.
                assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
        assertThat(records).as("a peer that hung up must not log an alarming, stack-trace-bearing WARNING")
            .allSatisfy(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.FINE);
                assertThat(record.getThrown()).as("no stack trace for ordinary traffic").isNull();
            });
    }

    @Test void aPersistentHandlerFailureBacksOffButTheLoopKeepsServing() throws Exception {
        var callCount = new java.util.concurrent.atomic.AtomicInteger();
        int failuresBeforeSuccess = 2;
        try (HandoffSocket endpoint = bind(request -> {
            if (callCount.incrementAndGet() <= failuresBeforeSuccess) throw new RuntimeException("simulated failure");
            return LaunchRequest.Response.OK;
        })) {
            long started = System.nanoTime();
            for (int i = 0; i < failuresBeforeSuccess; i++)
                assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertThat(elapsedMillis).as("the loop must back off between failures, not spin").isGreaterThanOrEqualTo(40);
            assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
            assertThat(callCount.get()).isEqualTo(failuresBeforeSuccess + 1);
        }
    }

    @Test void aPathTooLongForTheOperatingSystemDeclinesInsteadOfThrowing() throws Exception {
        Path deep = dir;
        for (int i = 0; i < 12; i++) deep = deep.resolve("a-directory-with-a-long-name");
        Files.createDirectories(deep);
        assertThat(deep.toString().length()).isGreaterThan(104);
        assertThat(HandoffSocket.bind(deep.resolve("socket"), deep.resolve("token"), request -> LaunchRequest.Response.OK)).isNull();
        assertThat(HandoffSocket.pathFits(deep.resolve("socket"))).isFalse();
        assertThat(HandoffSocket.pathFits(socket())).isTrue();
        assertThat(HandoffSocket.tooLong(deep.resolve("socket")))
            .isEqualTo("Jasper's home folder path is too long for its socket: " + deep.resolve("socket"));
    }

    @Test void theCodeLocationOfThisBuildIsResolvableAndItsTimestampIsReadable() {
        Path source = HandoffSocket.codeLocation();
        assertThat(source).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(source.isAbsolute()).isTrue();
        assertThat(HandoffSocket.lastModified(source)).isPositive();
        assertThat(HandoffSocket.lastModified(dir.resolve("absent"))).isZero();
    }

    @Test void theAcceptLoopSurvivesAConnectionThatSendsNothingUsable() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            try (var junk = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
                junk.write(java.nio.ByteBuffer.wrap("nonsense\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            assertThat(handOff(launch(1L))).as("still serving after junk").isEqualTo(LaunchRequest.Response.OK);
        }
    }

    @Test void aClientThatConnectsAndSaysNothingCannotHoldTheEndpoint() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
             SocketChannel silent = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
            // The silent peer occupies the accept thread until its read deadline expires.
            assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
        }
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
        }
    }

    @Test void afterClosingNoFurtherRequestIsAccepted() throws Exception {
        HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
        endpoint.close();
        endpoint.close(); // Idempotent.
        // Re-create the token, so the refusal comes from the dead listener rather than a missing file.
        Files.writeString(token(), "left-over-token\n");
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
        try (ServerSocketChannel proof = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            proof.bind(UnixDomainSocketAddress.of(socket()));  // The path was released.
        }
        Files.deleteIfExists(socket());
    }
}
