package dev.jasper.app.bootstrap;

import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.CommandListener;
import dev.jasper.app.commandline.CommandProtocol;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.LaunchRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 5.1 to 5.3 in one JVM. A file lock belongs to the process, so a second claim here meets this JVM's lock exactly
 * as another process's; {@code SingleInstanceProcessTest} repeats the essentials across real processes.
 */
@Timeout(60)
@org.junit.jupiter.api.extension.ExtendWith(dev.jasper.app.testsupport.VirtualThreadDump.class)
class SingleInstanceTest {
    private static final SingleInstance.Timing QUICK =
        new SingleInstance.Timing(Duration.ofMillis(600), Duration.ofMillis(300), Duration.ofMillis(20));
    private static final String NOT_RESPONDING = "Jasper is already running but not responding. Quit it (process 4242) and try again.";
    private static final CommandDispatcher NO_CALLS = invocation -> CompletableFuture.completedFuture(0);

    @TempDir Path home;
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AutoCloseable> opened = new ConcurrentLinkedQueue<>();

    @AfterEach void closeAll() throws Exception {
        for (AutoCloseable each : opened) each.close();
    }

    private AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    private SingleInstance.Claim claim(SingleInstance.Timing timing, String... args) {
        var claim = SingleInstance.claim(AppArguments.parse(args, home), dirs(), timing, failures::add);
        if (claim instanceof SingleInstance.Claim.Owner owner) opened.add(owner.instance());
        return claim;
    }

    private Instance owner() {
        var claim = claim(SingleInstance.Timing.STANDARD);
        assertThat(claim).isInstanceOf(SingleInstance.Claim.Owner.class);
        return ((SingleInstance.Claim.Owner) claim).instance();
    }

    /** An owner whose application is ready, with {@code edt} as its event thread and {@code accepting} as its state. */
    private Instance ready(ConcurrentLinkedQueue<LaunchRequest> seen, boolean accepting, java.util.concurrent.Executor edt)
        throws Exception {
        Instance instance = owner();
        instance.serve((request, updated) -> seen.add(request), () -> accepting, edt, NO_CALLS);
        until(() -> !instance.holding());
        return instance;
    }

    private InstanceLock heldElsewhere() throws IOException {
        InstanceLock lock = InstanceLock.tryAcquire(dirs().instanceLock()).orElseThrow();
        opened.add(lock);
        return lock;
    }

    /** The id of a process that has certainly ended: a JVM that ran {@code -version}. */
    private static long finishedProcess() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(java, "-version").redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return process.pid();
    }

    @Test void aFreeHomeMakesTheLaunchItsOwnerWithBothEndpointsBound() {
        Instance instance = owner();
        assertThat(instance.commandSocket()).isEqualTo(dirs().commandSocket());
        assertThat(instance.commands()).isNotNull();
        assertThat(dirs().daemonSocket()).exists();
        assertThat(dirs().instanceLock()).exists();
        assertThat(InstanceLock.ownerPid(dirs().instanceLock())).isEqualTo(ProcessHandle.current().pid());
        assertThat(failures).isEmpty();
    }

    @Test void aSecondLaunchIsAnsweredOnReceiptAndItsRequestRunsOnceTheOwnerIsReady() throws Exception {
        Instance instance = owner();
        Files.writeString(home.resolve("notes.txt"), "");
        assertThat(claim(SingleInstance.Timing.STANDARD, "--safe-mode", "notes.txt")).as("answered before the owner is ready")
            .isEqualTo(new SingleInstance.Claim.Done(0));
        assertThat(claim(SingleInstance.Timing.STANDARD)).isEqualTo(new SingleInstance.Claim.Done(0));
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        instance.serve((request, updated) -> seen.add(request), () -> true, Runnable::run, NO_CALLS);
        until(() -> seen.size() == 2);
        assertThat(seen).extracting(LaunchRequest::intent).containsExactly(LaunchRequest.Intent.OPEN_PATHS, LaunchRequest.Intent.RAISE);
        LaunchRequest first = seen.peek();
        assertThat(first.paths()).containsExactly(home.resolve("notes.txt"));
        assertThat(first.workingDirectory()).isEqualTo(home);
        assertThat(first.ignoredFlags()).containsExactly("--safe-mode");
        assertThat(failures).isEmpty();
    }

    @Test void aCallWaitsForTheOwnerAndIsAnsweredWhenItHasRun() throws Exception {
        Instance instance = owner();
        String token = Files.readString(CommandListener.tokenFile(dirs().commandSocket())).strip();
        var reply = CompletableFuture.supplyAsync(() -> call(token, List.of("editor")));
        Thread.sleep(300);
        assertThat(reply).as("held until the owner is ready").isNotDone();
        instance.serve((request, updated) -> { }, () -> true, Runnable::run, invocation -> {
            invocation.out("ran " + invocation.arguments() + "\n");
            return CompletableFuture.completedFuture(7);
        });
        assertThat(reply.get(10, TimeUnit.SECONDS)).isEqualTo(CommandProtocol.out("ran [editor]\n") + CommandProtocol.exit(7));
    }

    @Test void aCallWhoseDispatcherThrowsAnErrorIsAnsweredWithAFailureAndLaterCallsStillRun() throws Exception {
        Instance instance = owner();
        String token = Files.readString(CommandListener.tokenFile(dirs().commandSocket())).strip();
        instance.serve((request, updated) -> { }, () -> true, Runnable::run, invocation -> {
            if (invocation.arguments().contains("boom")) throw new AssertionError("a broken command");
            return CompletableFuture.completedFuture(3);
        });
        until(() -> !instance.holding());
        assertThat(call(token, List.of("boom"))).isEqualTo(CommandProtocol.err("jasper: a broken command\n") + CommandProtocol.exit(1));
        assertThat(call(token, List.of("fine"))).isEqualTo(CommandProtocol.exit(3));
        assertThat(instance.commands().busy()).as("no call is left running").isFalse();
    }

    @Test void aLoginOrWindowlessLaunchExitsWhileAnOwnerRuns() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        ready(seen, true, Runnable::run);
        assertThat(claim(SingleInstance.Timing.STANDARD, "--background")).isEqualTo(new SingleInstance.Claim.Done(0));
        assertThat(claim(SingleInstance.Timing.STANDARD, "--no-window")).isEqualTo(new SingleInstance.Claim.Done(0));
        Thread.sleep(200);
        assertThat(seen).as("neither handed anything off").isEmpty();
    }

    @Test void anOwnerThatNeverAnswersEndsTheLaunchWithTheErrorAndNoSecondInstance() throws Exception {
        heldElsewhere();
        Files.writeString(dirs().instanceLock().resolveSibling("instance.pid"), "4242\n");
        long began = System.nanoTime();
        assertThat(claim(QUICK)).isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("it kept trying").isGreaterThanOrEqualTo(Duration.ofMillis(600));
        assertThat(failures).containsExactly(NOT_RESPONDING);
        assertThat(dirs().daemonSocket()).as("it never bound an endpoint").doesNotExist();
        assertThat(SingleInstance.unresponsive(-1)).isEqualTo("Jasper is already running but not responding. Quit it and try again.");
    }

    @Test void aReadyOwnerWhoseEventThreadNeverTakesTheRequestIsNotResponding() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        ready(seen, true, task -> { });
        assertThat(claim(QUICK)).as("spec 5.3: answered only once the event thread took it").isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(failures).containsExactly(SingleInstance.unresponsive(ProcessHandle.current().pid()));
    }

    @Test void aRequestTheEventThreadReachesAfterTheLaunchGaveUpIsWithdrawnAndNeverCarriedOut() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        var reached = new AtomicInteger();
        ScheduledExecutorService late = Executors.newSingleThreadScheduledExecutor();
        try {
            // An event thread that gets to each task 1.5 s after it was posted: beyond the 1 s the owner waits for it.
            ready(seen, true, task -> late.schedule(() -> {
                task.run();
                reached.incrementAndGet();
            }, 1500, TimeUnit.MILLISECONDS));
            assertThat(claim(QUICK)).as("spec 5.3: not taken within a second").isEqualTo(new SingleInstance.Claim.Done(1));
            assertThat(failures).containsExactly(SingleInstance.unresponsive(ProcessHandle.current().pid()));
            // Runs after the late task: the executor runs by due time, and this one is due later than that one.
            late.schedule(() -> { }, 1500, TimeUnit.MILLISECONDS).get(10, TimeUnit.SECONDS);
            assertThat(reached.get()).as("the event thread did reach the request").isPositive();
            assertThat(seen).as("withdrawn, never carried out").isEmpty();
        } finally {
            late.shutdownNow();
        }
    }

    @Test void anOwnerThatTakesTheConnectionButNeverRepliesIsNotResponding() throws Exception {
        heldElsewhere();
        Files.writeString(dirs().daemonToken(), "a-token\n");
        // Bound and listening, never served: what a stopped owner (kill -STOP) looks like from outside.
        try (ServerSocketChannel stopped = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            stopped.bind(UnixDomainSocketAddress.of(dirs().daemonSocket()));
            assertThat(claim(QUICK)).isEqualTo(new SingleInstance.Claim.Done(1));
        } finally {
            Files.deleteIfExists(dirs().daemonSocket());
        }
        assertThat(failures).containsExactly(SingleInstance.unresponsive(ProcessHandle.current().pid()));
    }

    @Test void aQuittingOwnerTurnsHandoffsAwayAndTheLaunchTakesTheLockOnceItHasGone() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        Instance quitting = ready(seen, false, Runnable::run);
        var second = CompletableFuture.supplyAsync(() -> claim(SingleInstance.Timing.STANDARD));
        Thread.sleep(500);
        assertThat(second).as("it keeps trying while the owner quits").isNotDone();
        quitting.close();
        assertThat(second.get(10, TimeUnit.SECONDS)).isInstanceOf(SingleInstance.Claim.Owner.class);
        assertThat(seen).as("the quitting owner took nothing").isEmpty();
        assertThat(failures).isEmpty();
    }

    @Test void aLaunchThatOutlastsItsOwnerBecomesTheOwner() throws Exception {
        InstanceLock lock = heldElsewhere();
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(300); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            lock.close();
        });
        assertThat(claim(SingleInstance.Timing.STANDARD, "notes.txt")).isInstanceOf(SingleInstance.Claim.Owner.class);
        assertThat(failures).isEmpty();
    }

    @Test void aReplacementWhosePredecessorNeverQuitsWaitsForItAndNamesIt() throws Exception {
        heldElsewhere();
        // The recorded owner is another process, so only the replacement's wait can name the live process it replaces.
        Files.writeString(dirs().instanceLock().resolveSibling("instance.pid"), "4242\n");
        long alive = ProcessHandle.current().pid();
        // A plain hand-off would give up after 100 ms; the wait is the 700 ms.
        var timing = new SingleInstance.Timing(Duration.ofMillis(100), Duration.ofMillis(700), Duration.ofMillis(20));
        long began = System.nanoTime();
        assertThat(claim(timing, "--replacing", Long.toString(alive))).isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("it waited out the replacement's time, not the hand-off's")
            .isGreaterThanOrEqualTo(Duration.ofMillis(650));
        assertThat(failures).containsExactly(SingleInstance.unresponsive(alive));
    }

    @Test void aReplacementWhosePredecessorHasGoneHandsOffToTheOwnerThatTookItsPlace() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        ready(seen, true, Runnable::run);
        Files.writeString(home.resolve("notes.txt"), "");
        assertThat(claim(QUICK, "--replacing", Long.toString(finishedProcess()), "notes.txt"))
            .as("plan Ruling 27").isEqualTo(new SingleInstance.Claim.Done(0));
        assertThat(seen).singleElement().satisfies(request -> assertThat(request.paths()).containsExactly(home.resolve("notes.txt")));
        assertThat(failures).isEmpty();
    }

    @Test void aLaunchWithMorePathsThanTheLineHoldsSaysSo() {
        owner();
        String[] many = IntStream.range(0, 20_000).mapToObj(i -> "a-long-file-name-number-" + i + ".txt").toArray(String[]::new);
        assertThat(claim(SingleInstance.Timing.STANDARD, many)).isEqualTo(new SingleInstance.Claim.Done(2));
        assertThat(failures).containsExactly("Jasper cannot open that many paths at once.");
    }

    @Test void aHomeThatCannotBeLockedEndsTheLaunchWithTheReason() throws Exception {
        Files.writeString(home.resolve("daemon"), "not a folder");
        assertThat(claim(SingleInstance.Timing.STANDARD)).isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(failures).singleElement().asString().startsWith("Jasper could not lock its home folder (" + dirs().instanceLock() + "): ");
    }

    @Test void aHomeTooDeepForItsSocketIsSaidSoInsteadOfWaitingForAnAnswer() throws Exception {
        Path deep = home;
        for (int i = 0; i < 4; i++) deep = deep.resolve("a-directory-with-a-long-name");
        var deepDirs = new AppDirs(deep, deep.resolve("config.toml"), deep.resolve("logs"));
        assertThat(deepDirs.daemonSocket().toString().getBytes(StandardCharsets.UTF_8).length).isGreaterThan(100);
        var first = SingleInstance.claim(AppArguments.parse(new String[0], home), deepDirs, QUICK, failures::add);
        assertThat(first).as("it owns the home, unreachable (plan Ruling 15)").isInstanceOf(SingleInstance.Claim.Owner.class);
        opened.add(((SingleInstance.Claim.Owner) first).instance());
        long began = System.nanoTime();
        assertThat(SingleInstance.claim(AppArguments.parse(new String[0], home), deepDirs, SingleInstance.Timing.STANDARD, failures::add))
            .isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("at once, not after 10 s").isLessThan(Duration.ofSeconds(5));
        assertThat(failures).containsExactly(HandoffSocket.tooLong(deepDirs.daemonSocket()));
        assertThat(failures.peek()).startsWith("Jasper's home folder path is too long for its socket: ");
    }

    @Test void theFailureReporterWritesTheMessageOnStandardError() {
        var error = new ByteArrayOutputStream();
        LaunchFailure.reporter(new PrintStream(error, true, StandardCharsets.UTF_8)).accept(NOT_RESPONDING);
        assertThat(error.toString(StandardCharsets.UTF_8)).as("headless: no dialog").isEqualTo(NOT_RESPONDING + System.lineSeparator());
    }

    private String call(String token, List<String> arguments) {
        try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(dirs().commandSocket()))) {
            ByteBuffer request = ByteBuffer.wrap(new CommandProtocol.Request(token, home, Optional.empty(), arguments).encode()
                .getBytes(StandardCharsets.UTF_8));
            while (request.hasRemaining()) client.write(request);
            var bytes = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (client.read(buffer.clear()) >= 0) bytes.write(buffer.array(), 0, buffer.position());
            return bytes.toString(StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
