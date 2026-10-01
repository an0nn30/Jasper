package dev.jasper.remote.tunnels;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TunnelManagerTest {
    final ExecutorService background = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("bg-", 0).factory());
    final ExecutorService ui = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("ui").factory());
    final Map<UUID, RemoteHost> hosts = new HashMap<>();
    final List<Runnable> lingering = new CopyOnWriteArrayList<>();
    final List<String> timeoutThreads = new CopyOnWriteArrayList<>();
    final RemoteSettings settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ZERO, Duration.ZERO, false, true);
    @TempDir Path dir;
    LoopbackServer server;
    EchoServer echo;
    Connections connections;
    TunnelManager manager;
    RemoteHost prod;
    volatile String password = "s3cret";

    @BeforeEach void open() throws Exception {
        server = new LoopbackServer();
        echo = new EchoServer();
        var trust = new KnownHosts(dir.resolve("known_hosts"), Optional.empty());
        trust.trust("127.0.0.1", server.port(), server.hostPublicKey());
        Function<UUID, CompletableFuture<Optional<Credential>>> vault = id -> CompletableFuture.completedFuture(Optional.of(
            new Credential(id, "deploy login", Kind.ACCOUNT_PASSWORD, "deploy", password.toCharArray(), null, null)));
        connections = new Connections(() -> settings, trust, Optional.empty(), id -> Optional.ofNullable(hosts.get(id)), Optional.of(vault),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.CANCEL), background, ui,
            (delay, task) -> { lingering.add(task); return () -> lingering.remove(task); });
        manager = new TunnelManager(connections, () -> { timeoutThreads.add(Thread.currentThread().getName()); return Duration.ofSeconds(5); }, background, ui);
        prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
        hosts.put(prod.id(), prod);
    }

    @AfterEach void close() throws Exception {
        onUi(() -> { manager.close(); connections.close(); });
        echo.close();
        server.close();
        background.shutdownNow();
        ui.shutdownNow();
    }

    /** Runs on the fake UI thread and waits: the manager is UI-thread-only. */
    <T> T onUi(Supplier<T> work) {
        try { return ui.submit(work::get).get(10, TimeUnit.SECONDS); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    void onUi(Runnable work) { onUi(() -> { work.run(); return null; }); }

    TunnelState await(UUID id, Predicate<TunnelState> wanted, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            TunnelState state = onUi(() -> manager.state(id));
            if (wanted.test(state)) return state;
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what + "; last state " + state);
            Thread.sleep(20);
        }
    }

    static void awaitTrue(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    int up(Tunnel tunnel) throws InterruptedException {
        onUi(() -> manager.start(tunnel, null));
        return ((TunnelState.Up) await(tunnel.id(), TunnelState.Up.class::isInstance, tunnel.name() + " up")).boundPort();
    }

    Counters counters(Tunnel tunnel) { return ((TunnelState.Up) onUi(() -> manager.state(tunnel.id()))).counters(); }

    Tunnel local(String bindAddress, int bindPort, int targetPort) {
        return Tunnel.create("db", prod.id(), TunnelKind.LOCAL, bindAddress, bindPort, "127.0.0.1", targetPort);
    }

    static int closedPort() throws IOException {
        try (var probe = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) { return probe.getLocalPort(); }
    }

    static boolean refused(int port) {
        try (var socket = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) { return false; }
        catch (ConnectException refused) { return true; }
        catch (IOException other) { return false; }
    }

    /** Sends {@code text} and reads the same number of bytes back, without half-closing: MINA's server drops a forwarded port's connection on FIN. */
    static String viaServer(int port, String text) throws IOException {
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            client.getOutputStream().write(bytes);
            return new String(client.getInputStream().readNBytes(bytes.length), StandardCharsets.UTF_8);
        }
    }

    /** Whether the other side hung up: end of stream or a reset. */
    static boolean ended(Socket socket) {
        try { return socket.getInputStream().read() == -1; } catch (IOException reset) { return true; }
    }

    @Test void aLocalTunnelForwardsBothWaysAndCounts() throws Exception {
        var changes = new AtomicInteger();
        onUi(() -> manager.onChanged(changes::incrementAndGet));
        Tunnel db = local(Tunnel.LOCALHOST, 0, echo.port());
        int port = up(db);
        assertThat(EchoServer.roundTrip(port, "hello")).isEqualTo("hello");
        assertThat(EchoServer.roundTrip(port, "again")).isEqualTo("again");
        Counters counters = counters(db);
        awaitTrue(() -> counters.open() == 0 && counters.received() == 10 && counters.sent() == 10, "the bytes to be counted");
        assertThat(onUi(() -> manager.active())).isEqualTo(1);
        assertThat(onUi(() -> manager.running(db.id()))).contains(db);
        assertThat(onUi(() -> manager.runningTunnels())).containsExactly(db);
        assertThat(server.server.getActiveSessions()).as("one session for every connection").hasSize(1);
        assertThat(changes.get()).as("binding, connecting, up").isGreaterThanOrEqualTo(3);
    }

    @Test void stopClosesTheListenerAndLiveConnectionsAndReleasesTheSession() throws Exception {
        Tunnel db = local("127.0.0.1", 0, echo.port());
        int port = up(db);
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            client.getOutputStream().write('x');
            assertThat(client.getInputStream().read()).isEqualTo('x');
            onUi(() -> manager.stop(db.id()));
            assertThat(ended(client)).as("the live connection was closed").isTrue();
        }
        assertThat(onUi(() -> manager.state(db.id()))).isEqualTo(TunnelState.STOPPED);
        assertThat(onUi(() -> manager.active())).isZero();
        awaitTrue(() -> refused(port), "the port to close");
        awaitTrue(() -> !lingering.isEmpty(), "the session to linger");
        List.copyOf(lingering).forEach(Runnable::run);
        awaitTrue(() -> server.server.getActiveSessions().isEmpty(), "the session to close");
    }

    @Test void aBusyPortFailsBeforeAnyConnection() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            Tunnel db = local("127.0.0.1", busy.getLocalPort(), echo.port());
            onUi(() -> manager.start(db, null));
            assertThat(onUi(() -> manager.state(db.id())))
                .isEqualTo(new TunnelState.Failed("Port " + busy.getLocalPort() + " is already in use on this computer"));
            assertThat(server.server.getActiveSessions()).as("nothing connected").isEmpty();
            assertThat(onUi(() -> manager.failed())).isEqualTo(1);
            assertThat(onUi(() -> manager.known())).as("a failed tunnel has a state to clear").containsExactly(db.id());
            onUi(() -> manager.stop(db.id()));
            assertThat(onUi(() -> manager.state(db.id()))).as("Stop clears a failure").isEqualTo(TunnelState.STOPPED);
            assertThat(onUi(() -> manager.failed())).isZero();
            assertThat(onUi(() -> manager.known())).isEmpty();
        }
    }

    @Test void aMissingHostFails() {
        Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 0, "x", 1);
        onUi(() -> manager.start(orphan, null));
        assertThat(onUi(() -> manager.state(orphan.id()))).isEqualTo(new TunnelState.Failed("Host missing"));
    }

    @Test void aRejectedLoginFailsTheTunnel() throws Exception {
        password = "wrong";
        Tunnel db = local(Tunnel.LOCALHOST, 0, echo.port());
        onUi(() -> manager.start(db, null));
        var failed = (TunnelState.Failed) await(db.id(), TunnelState.Failed.class::isInstance, "authentication to fail");
        assertThat(failed.reason()).startsWith("Authentication failed");
    }

    @Test void losingTheSessionFailsTheTunnelAndFreesThePort() throws Exception {
        Tunnel db = local("127.0.0.1", 0, echo.port());
        int port = up(db);
        server.server.getActiveSessions().forEach(session -> session.close(true));
        assertThat(await(db.id(), TunnelState.Failed.class::isInstance, "the loss")).isEqualTo(new TunnelState.Failed("Connection lost"));
        awaitTrue(() -> refused(port), "the port to close");
    }

    @Test void aRefusedTargetWarnsButTheTunnelStaysUp() throws Exception {
        Tunnel nowhere = local("127.0.0.1", 0, closedPort());
        int port = up(nowhere);
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            assertThat(ended(client)).isTrue();
        }
        Counters counters = counters(nowhere);
        awaitTrue(() -> counters.warning().isPresent(), "a warning");
        assertThat(counters.warning().orElseThrow()).startsWith("Connections are being refused: ");
        assertThat(onUi(() -> manager.state(nowhere.id()))).isInstanceOf(TunnelState.Up.class);
    }

    @Test void aDynamicTunnelIsASocksProxyThatResolvesOnTheServer() throws Exception {
        Tunnel socks = Tunnel.create("socks", prod.id(), TunnelKind.DYNAMIC, "127.0.0.1", 0, "", 0);
        int port = up(socks);
        var proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), port));
        try (var client = new Socket(proxy)) {
            client.setSoTimeout(10_000);
            client.connect(InetSocketAddress.createUnresolved("127.0.0.1", echo.port()), 10_000);
            client.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8));
            client.shutdownOutput();
            assertThat(new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hi");
        }
        int closed = closedPort();
        try (var client = new Socket(proxy)) {
            assertThatThrownBy(() -> client.connect(InetSocketAddress.createUnresolved("127.0.0.1", closed), 10_000))
                .as("the SOCKS reply says the host is unreachable").isInstanceOf(IOException.class);
        }
        assertThat(counters(socks).warning()).get().asString().startsWith("Connections are being refused: ");
    }

    @Test void aRemoteTunnelOpensAPortOnTheServerThatComesBackToTheTarget() throws Exception {
        Tunnel back = Tunnel.create("back", prod.id(), TunnelKind.REMOTE, "127.0.0.1", 0, "127.0.0.1", echo.port());
        int serverPort = up(back);
        assertThat(serverPort).as("the server chose a port").isPositive();
        assertThat(viaServer(serverPort, "through the server")).isEqualTo("through the server");
        Counters counters = counters(back);
        awaitTrue(() -> counters.sent() == 18 && counters.received() == 18, "the relay to count");
        onUi(() -> manager.stop(back.id()));
        awaitTrue(() -> refused(serverPort), "the server to close its port");
    }

    @Test void aRemotePortTheServerCannotOpenFailsTheTunnel() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            Tunnel back = Tunnel.create("back", prod.id(), TunnelKind.REMOTE, "127.0.0.1", busy.getLocalPort(), "127.0.0.1", echo.port());
            onUi(() -> manager.start(back, null));
            assertThat(await(back.id(), TunnelState.Failed.class::isInstance, "the refusal"))
                .isEqualTo(new TunnelState.Failed("The server refused port " + busy.getLocalPort() + ": already in use or not allowed"));
        }
    }

    @Test void quickForwardsAreListedUntilStoppedOrSaved() throws Exception {
        Tunnel quick = Tunnel.create("", prod.id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
        onUi(() -> manager.forward(quick, null));
        assertThat(onUi(() -> manager.quickForwards())).containsExactly(quick);
        await(quick.id(), TunnelState.Up.class::isInstance, "up");
        onUi(() -> manager.stop(quick.id()));
        assertThat(onUi(() -> manager.quickForwards())).as("a stopped quick forward disappears").isEmpty();

        Tunnel kept = Tunnel.create("", prod.id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
        onUi(() -> manager.forward(kept, null));
        await(kept.id(), TunnelState.Up.class::isInstance, "up");
        onUi(() -> manager.saved(kept.id()));
        assertThat(onUi(() -> manager.quickForwards())).isEmpty();
        assertThat(onUi(() -> manager.running(kept.id()))).as("saving does not stop it").isPresent();
    }

    @Test void aSecondRemoteTunnelOnTheSamePortFailsWithoutBreakingTheFirst() throws Exception {
        int port = closedPort();
        Tunnel first = Tunnel.create("first", prod.id(), TunnelKind.REMOTE, "127.0.0.1", port, "127.0.0.1", echo.port());
        assertThat(up(first)).isEqualTo(port);
        Tunnel second = Tunnel.create("second", prod.id(), TunnelKind.REMOTE, "127.0.0.1", port, "127.0.0.1", echo.port());
        onUi(() -> manager.start(second, null));
        assertThat(await(second.id(), TunnelState.Failed.class::isInstance, "the refusal"))
            .isEqualTo(new TunnelState.Failed("The server refused port " + port + ": already in use or not allowed"));
        assertThat(onUi(() -> manager.state(first.id()))).as("the first tunnel is untouched").isInstanceOf(TunnelState.Up.class);
        assertThat(viaServer(port, "still here")).as("the first tunnel still carries").isEqualTo("still here");
    }

    @Test void restartingARemoteTunnelOnTheSamePortWorks() throws Exception {
        int port = closedPort();
        Tunnel running = Tunnel.create("edit 0", prod.id(), TunnelKind.REMOTE, "127.0.0.1", port, "127.0.0.1", echo.port());
        assertThat(up(running)).isEqualTo(port);
        for (int round = 1; round <= 8; round++) {
            Tunnel restarted = Tunnel.create("edit " + round, prod.id(), TunnelKind.REMOTE, "127.0.0.1", port, "127.0.0.1", echo.port());
            Tunnel stopping = running;
            onUi(() -> { manager.stop(stopping.id()); manager.start(restarted, null); });
            var state = await(restarted.id(), s -> s instanceof TunnelState.Up || s instanceof TunnelState.Failed, "round " + round);
            assertThat(state).as("round " + round).isInstanceOf(TunnelState.Up.class);
            assertThat(viaServer(port, "round " + round)).isEqualTo("round " + round);
            running = restarted;
        }
    }

    @Test void aSlowCancelStillRunsBeforeTheNextRequestForTheSamePort() throws Exception {
        // The manager's background work for a stop is held back; the restart's request must still wait for it.
        var slowCancels = new AtomicBoolean();
        manager = new TunnelManager(connections, () -> Duration.ofSeconds(5), task -> {
            boolean hold = slowCancels.get();
            background.execute(() -> {
                if (hold) { try { Thread.sleep(500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } }
                task.run();
            });
        }, ui);
        int port = closedPort();
        Tunnel first = Tunnel.create("first", prod.id(), TunnelKind.REMOTE, "127.0.0.1", port, "127.0.0.1", echo.port());
        assertThat(up(first)).isEqualTo(port);
        slowCancels.set(true);
        onUi(() -> manager.stop(first.id()));
        slowCancels.set(false);
        Tunnel second = Tunnel.create("second", prod.id(), TunnelKind.REMOTE, "127.0.0.1", port, "127.0.0.1", echo.port());
        onUi(() -> manager.start(second, null));
        assertThat(await(second.id(), s -> s instanceof TunnelState.Up || s instanceof TunnelState.Failed, "the restart"))
            .isInstanceOf(TunnelState.Up.class);
        assertThat(viaServer(port, "after the slow cancel")).isEqualTo("after the slow cancel");
    }

    @Test void stoppingATunnelClosesAClientStillInTheSocksHandshake() throws Exception {
        Tunnel socks = Tunnel.create("socks", prod.id(), TunnelKind.DYNAMIC, "127.0.0.1", 0, "", 0);
        int port = up(socks);
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(2_000);
            Thread.sleep(300); // the tunnel has accepted it and waits for the greeting
            onUi(() -> manager.stop(socks.id()));
            try { assertThat(client.getInputStream().read()).isEqualTo(-1); }
            catch (SocketTimeoutException slow) { fail("stop left the half-greeted client connected"); }
            catch (IOException reset) { /* a reset is a hang-up too */ }
        }
    }

    @Test void connectionThreadsNeverReadTheTimeoutSetting() throws Exception {
        Tunnel nowhere = local("127.0.0.1", 0, closedPort());
        int port = up(nowhere);
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            ended(client);
        }
        Counters counters = counters(nowhere);
        awaitTrue(() -> counters.warning().isPresent(), "a warning");
        assertThat(timeoutThreads).as("the supplier is for the UI thread; connection threads use the value captured at start").containsOnly("ui");
    }

    @Test void stopAllStopsEverythingAndClearsFailures() throws Exception {
        Tunnel db = local("127.0.0.1", 0, echo.port());
        up(db);
        Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 0, "x", 1);
        onUi(() -> manager.start(orphan, null));
        onUi(() -> manager.stopAll());
        assertThat(onUi(() -> manager.active())).isZero();
        assertThat(onUi(() -> manager.failed())).isZero();
        assertThat(onUi(() -> manager.state(db.id()))).isEqualTo(TunnelState.STOPPED);
    }
}
