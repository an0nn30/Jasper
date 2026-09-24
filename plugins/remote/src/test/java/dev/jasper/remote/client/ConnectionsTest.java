package dev.jasper.remote.client;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.agent.AgentClient;
import dev.jasper.remote.agent.FakeAgent;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import dev.jasper.vault.model.SshKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ConnectionsTest {
    final ExecutorService background = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("bg-", 0).factory());
    final ExecutorService ui = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("ui").factory());
    final Map<UUID, RemoteHost> hosts = new HashMap<>();
    final Map<UUID, Credential> credentials = new HashMap<>();
    final List<char[]> issuedPassphrases = new ArrayList<>();
    final List<Credential> issued = new ArrayList<>();
    final List<HostKeyVerifier.Question> questions = new ArrayList<>();
    final List<Runnable> scheduled = new ArrayList<>();
    HostKeyVerifier.Decision decision = HostKeyVerifier.Decision.TRUST;
    RemoteSettings settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ZERO, Duration.ZERO, false, true);
    Connections connections;
    KnownHosts trust;

    @AfterEach void stop() { if (connections != null) onUi(connections::close); background.shutdownNow(); ui.shutdownNow(); }

    /** Runs on the fake UI thread and waits: the registry is UI-thread-only. */
    <T> T onUi(java.util.function.Supplier<T> work) { try { return ui.submit(work::get).get(10, TimeUnit.SECONDS); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    void onUi(Runnable work) { onUi(() -> { work.run(); return null; }); }

    Connections connections(Path dir, Optional<AgentClient> agent) {
        trust = new KnownHosts(dir.resolve("known_hosts"), Optional.empty());
        Function<UUID, CompletableFuture<Optional<Credential>>> vault = id -> {
            Credential seed = credentials.get(id);
            if (seed == null) return CompletableFuture.completedFuture(Optional.empty());
            var copy = new Credential(seed.id(), seed.name(), seed.kind(), seed.username().orElse(null),
                seed.password() == null ? null : seed.password().clone(), seed.keyPath().orElse(null),
                seed.keyBytes().map(byte[]::clone).orElse(null), seed.passphrase() == null ? null : seed.passphrase().clone());
            issued.add(copy);
            if (copy.passphrase() != null) issuedPassphrases.add(copy.passphrase());
            return CompletableFuture.completedFuture(Optional.of(copy));
        };
        connections = new Connections(() -> settings, trust, agent, id -> Optional.ofNullable(hosts.get(id)), Optional.of(vault),
            question -> { questions.add(question); return CompletableFuture.completedFuture(decision); }, background, ui,
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); });
        return connections;
    }

    RemoteHost host(String name, int port, String username, Auth auth, Optional<UUID> jump) {
        RemoteHost host = RemoteHost.create(name, "127.0.0.1", port, username, auth, "", jump);
        hosts.put(host.id(), host);
        return host;
    }

    UUID passwordCredential() {
        UUID id = UUID.randomUUID();
        credentials.put(id, new Credential(id, "deploy login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null));
        return id;
    }

    Connections.Shell shell(RemoteHost host) { return onUi(() -> connections.shell(host.id(), 100, 30, status -> { })).join(); }
    Connections.Shell dedicatedShell(RemoteHost host) { return onUi(() -> connections.shell(host.id(), null, 100, 30, status -> { }, true)).join(); }

    static void awaitSessions(LoopbackServer server, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (server.server.getActiveSessions().size() != count && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(server.server.getActiveSessions()).hasSize(count);
    }

    static String readUntil(TerminalConnection connection, String marker) throws IOException {
        var out = new StringBuilder();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!out.toString().contains(marker)) {
            if (System.nanoTime() > deadline) throw new IOException("Timed out waiting for " + marker + " in: " + out);
            int b = connection.output().read();
            if (b < 0) throw new IOException("EOF before " + marker + " in: " + out);
            out.append((char) b);
        }
        return out.toString();
    }

    static void type(TerminalConnection connection, String text) throws IOException { connection.input().write(text.getBytes(StandardCharsets.UTF_8)); connection.input().flush(); }

    static String failure(CompletableFuture<?> future) {
        try { future.get(15, TimeUnit.SECONDS); return "no failure"; }
        catch (java.util.concurrent.ExecutionException failure) { return failure.getCause().getMessage(); }
        catch (Exception other) { return other.toString(); }
    }

    @Test void leasesShareWithShellAndKeepCapturedEndpointAfterHostEdit(@TempDir Path dir) throws Exception {
        try (var firstServer = new LoopbackServer(); var secondServer = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("target", firstServer.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = shell(host);
            var first = onUi(() -> connections.lease(shell.identity(), null, status -> {})).get(10, TimeUnit.SECONDS);
            var second = onUi(() -> connections.lease(shell.identity(), null, status -> {})).get(10, TimeUnit.SECONDS);
            assertThat(first.session()).isSameAs(second.session());
            first.close(); first.close();
            onUi(() -> {});
            assertThat(second.session().isOpen()).isTrue();
            assertThat(onUi(connections::channelCount)).isEqualTo(1);
            var edited = host.withEdited("target renamed", "127.0.0.1", secondServer.port(), "deploy", new Auth.Vault(passwordCredential()), "", Optional.empty());
            hosts.put(host.id(), edited);
            var current = onUi(() -> connections.lease(connections.identity(host.id()), null, status -> {})).get(10, TimeUnit.SECONDS);
            assertThat(current.session()).isNotSameAs(second.session());
            var original = onUi(() -> connections.lease(shell.identity(), null, status -> {})).get(10, TimeUnit.SECONDS);
            assertThat(original.session()).isSameAs(second.session());
            assertThat(original.identity().host().port()).isEqualTo(firstServer.port());
            original.close(); second.close(); current.close(); shell.connection().close().run();
        }
    }

    @Test void effectiveVaultUsernameChangesCannotReuseOldSession(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.server.setPasswordAuthenticator((user, password, session) -> password.equals("s3cret"));
            connections(dir, Optional.empty());
            UUID credential = passwordCredential();
            var host = host("target", server.port(), "", new Auth.Vault(credential), Optional.empty());
            var first = onUi(() -> connections.lease(connections.identity(host.id()), null, status -> {})).get(10, TimeUnit.SECONDS);
            credentials.put(credential, new Credential(credential, "changed", Kind.ACCOUNT_PASSWORD, "other", "s3cret".toCharArray(), null, null));
            var second = onUi(() -> connections.lease(connections.identity(host.id()), null, status -> {})).get(10, TimeUnit.SECONDS);
            assertThat(second.session()).isNotSameAs(first.session());
            assertThat(first.session().getUsername()).isEqualTo("deploy");
            assertThat(second.session().getUsername()).isEqualTo("other");
            first.close(); second.close();
        }
    }

    static final class Owner implements dev.jasper.sdk.terminal.WindowHandle {
        final UUID id = UUID.randomUUID(); boolean open = true;
        public UUID id() { return id; }
        public List<dev.jasper.sdk.terminal.TabHandle> tabs() { return List.of(); }
        public Optional<dev.jasper.sdk.terminal.TabHandle> activeTab() { return Optional.empty(); }
        public boolean isActive() { return false; }
        public boolean isOpen() { return open; }
        public void toFront() {}
    }

    @Test void deadPromptOwnerDoesNotCancelAnotherWindowsAcquire(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            var firstOwner = new Owner(); var secondOwner = new Owner();
            var firstPrompt = new CompletableFuture<dev.jasper.sdk.terminal.WindowHandle>();
            var secondPrompt = new CompletableFuture<dev.jasper.sdk.terminal.WindowHandle>();
            var firstAnswer = new CompletableFuture<HostKeyVerifier.Decision>();
            connections = new Connections(() -> settings, new KnownHosts(dir.resolve("known_hosts"), Optional.empty()), Optional.empty(),
                id -> Optional.ofNullable(hosts.get(id)), Optional.of((owner, id) -> CompletableFuture.completedFuture(Optional.of(
                    new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)))),
                (owner, question) -> {
                    if (!firstPrompt.isDone()) { firstPrompt.complete(owner); return firstAnswer; }
                    secondPrompt.complete(owner); return CompletableFuture.completedFuture(HostKeyVerifier.Decision.TRUST);
                }, background, ui, (delay, task) -> () -> {});
            var host = host("shared", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), Optional.empty());
            var first = onUi(() -> connections.lease(connections.identity(host.id()), firstOwner, status -> {}));
            var second = onUi(() -> connections.lease(connections.identity(host.id()), secondOwner, status -> {}));
            assertThat(firstPrompt.get(10, TimeUnit.SECONDS)).isSameAs(firstOwner);
            onUi(() -> { firstOwner.open = false; first.cancel(true); });
            assertThat(secondPrompt.get(10, TimeUnit.SECONDS)).isSameAs(secondOwner);
            try (var lease = second.get(10, TimeUnit.SECONDS)) { assertThat(lease.session().isOpen()).isTrue(); }
            assertThat(firstAnswer).isCancelled();
        }
    }

    @Test void deadCredentialOwnerHandsSharedAcquireToSurvivingWindow(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            var firstOwner = new Owner(); var secondOwner = new Owner();
            var waiting = new CompletableFuture<Optional<Credential>>();
            var requested = new CompletableFuture<Void>();
            connections = new Connections(() -> settings, new KnownHosts(dir.resolve("known_hosts"), Optional.empty()), Optional.empty(),
                id -> Optional.ofNullable(hosts.get(id)), Optional.of((owner, id) -> {
                    if (owner == firstOwner) { requested.complete(null); return waiting; }
                    assertThat(owner).isSameAs(secondOwner);
                    return CompletableFuture.completedFuture(Optional.of(new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)));
                }), (owner, question) -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.TRUST), background, ui, (delay, task) -> () -> {});
            var host = host("shared", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), Optional.empty());
            var first = onUi(() -> connections.lease(connections.identity(host.id()), firstOwner, status -> {}));
            var second = onUi(() -> connections.lease(connections.identity(host.id()), secondOwner, status -> {}));
            requested.get(10, TimeUnit.SECONDS);
            onUi(() -> { firstOwner.open = false; first.cancel(true); waiting.complete(Optional.empty()); });
            try (var lease = second.get(10, TimeUnit.SECONDS)) { assertThat(lease.session().isOpen()).isTrue(); }
        }
    }

    @Test void changedJumpVaultUsernameChangesEntireRouteIdentity(@TempDir Path dir) throws Exception {
        try (var jumpServer = new LoopbackServer(); var target = new LoopbackServer()) {
            jumpServer.server.setPasswordAuthenticator((user, password, session) -> password.equals("s3cret"));
            connections(dir, Optional.empty());
            UUID login = passwordCredential();
            var jump = host("jump", jumpServer.port(), "", new Auth.Vault(login), Optional.empty());
            var host = host("target", target.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.of(jump.id()));
            var first = onUi(() -> connections.lease(connections.identity(host.id()), null, status -> {})).get(10, TimeUnit.SECONDS);
            credentials.put(login, new Credential(login, "changed", Kind.ACCOUNT_PASSWORD, "other", "s3cret".toCharArray(), null, null));
            var second = onUi(() -> connections.lease(connections.identity(host.id()), null, status -> {})).get(10, TimeUnit.SECONDS);
            assertThat(first.identity().hops().get(1).username()).isEqualTo("deploy");
            assertThat(second.identity().hops().get(1).username()).isEqualTo("other");
            assertThat(second.session()).isNotSameAs(first.session());
            assertThat(first.session().isOpen()).isTrue();
            first.close(); second.close();
        }
    }

    @Test void managedKeysAuthenticateAfterSourceDeletionWithoutAgent(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            var generator = new KeyGenerator(dir.resolve("keys"));
            SshKey first = generator.generate(KeyAlgorithm.ED25519, "first", "test");
            SshKey second = generator.generate(KeyAlgorithm.ED25519, "second", "test", "phrase".toCharArray());
            server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(second.publicPath()).strip()).resolvePublicKey(null, null, null));
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            credentials.put(a, new Credential(a, "first", Kind.SSH_KEY, null, null, null, Files.readAllBytes(first.privatePath()), null));
            credentials.put(b, new Credential(b, "second", Kind.SSH_KEY, null, null, null, Files.readAllBytes(second.privatePath()), "phrase".toCharArray()));
            for (Path path : List.of(first.privatePath(), first.publicPath(), second.privatePath(), second.publicPath())) Files.delete(path);
            connections(dir, Optional.empty());
            var target = host("managed", server.port(), "deploy", new Auth.VaultKeys(List.of(a, b)), Optional.empty());
            assertThat(readUntil(shell(target).connection(), "\r\n")).startsWith("READY");
            assertThat(issued).isNotEmpty().allSatisfy(c -> assertThatThrownBy(c::keyBytes).isInstanceOf(IllegalStateException.class));
        }
    }
    @Test void passwordAuthOpensAShellThatEchoesResizesAndExits(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            RemoteHost host = host("prod", server.port(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            List<String> statuses = new ArrayList<>();
            CompletableFuture<Connections.Shell> future = onUi(() -> connections.shell(host.id(), 100, 30, statuses::add));
            Connections.Shell shell = future.get(15, TimeUnit.SECONDS);
            TerminalConnection connection = shell.connection();
            assertThat(readUntil(connection, "\r\n")).isEqualTo("READY xterm-256color 100x30\r\n");
            assertThat(statuses).containsSubsequence("Connecting…", "Authenticating…", "Opening shell…");
            assertThat(questions).as("an unknown host key was trusted").hasSize(1);
            assertThat(questions.getFirst().fingerprint()).isEqualTo(KnownHosts.fingerprint(server.hostPublicKey()));
            assertThat(Files.readString(dir.resolve("known_hosts"))).contains("[127.0.0.1]:" + server.port() + " ");
            type(connection, "hi");
            assertThat(readUntil(connection, "hi")).endsWith("hi");
            connection.resize().accept(120, 40);
            assertThat(readUntil(connection, "\r\n")).contains("WINCH 120x40");
            assertThat(onUi(connections::channelCount)).isEqualTo(1);
            assertThat(onUi(() -> connections.connected(host.id()))).isTrue();
            type(connection, "q");
            assertThat(connection.exited().get(10, TimeUnit.SECONDS)).isEqualTo(7);
            assertThat(onUi(connections::channelCount)).as("remote exit releases its channel").isZero();
            connection.close().run();
            assertThat(onUi(connections::channelCount)).isZero();
            assertThat(scheduled).as("linger scheduled after the last channel").hasSize(1);
            onUi(scheduled.getFirst());
            Thread.sleep(200);
            assertThat(onUi(() -> connections.connected(host.id()))).isFalse();
            assertThat(issued).isNotEmpty().allSatisfy(credential -> assertThatThrownBy(credential::password).as("closed after use").isInstanceOf(IllegalStateException.class));
        }
    }

    @Test void keyAuthUsesTheVaultKeyFileAndSharesOneSession(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            SshKey generated = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "test", "t");
            server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(generated.publicPath()).strip()).resolvePublicKey(null, null, null));
            connections(dir, Optional.empty());
            UUID id = UUID.randomUUID();
            credentials.put(id, new Credential(id, "key", Kind.SSH_KEY, null, null, generated.privatePath(), null));
            RemoteHost host = host("keyed", server.port(), "deploy", new Auth.Vault(id), Optional.empty());
            Connections.Shell first = shell(host), second = shell(host);
            assertThat(readUntil(first.connection(), "\r\n")).startsWith("READY");
            assertThat(readUntil(second.connection(), "\r\n")).startsWith("READY");
            assertThat(onUi(connections::channelCount)).isEqualTo(2);
            assertThat(questions).as("the same host key is asked about once").hasSize(1);
            first.connection().close().run();
            assertThat(scheduled).as("no linger while a channel remains").isEmpty();
            server.close();
            assertThat(failure(second.connection().exited())).contains("connection lost");
            Thread.sleep(200);
            assertThat(onUi(() -> connections.connected(host.id()))).isFalse();
        }
    }

    @Test void agentAuthSignsThroughTheAgent(@TempDir Path dir) throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Path socket = Path.of("/tmp", "jasper-agent-" + UUID.randomUUID());
        try (var server = new LoopbackServer(); var agent = new FakeAgent(pair)) {
            agent.serveUnixSocket(socket);
            server.allow(pair.getPublic());
            connections(dir, AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Linux"));
            RemoteHost host = host("agent", server.port(), "deploy", Auth.AGENT, Optional.empty());
            assertThat(readUntil(shell(host).connection(), "\r\n")).startsWith("READY");
            assertThat(agent.flagsSeen).isNotEmpty();
        }
        connections(dir, Optional.empty());
        RemoteHost noAgent = host("noagent", 1, "deploy", Auth.AGENT, Optional.empty());
        assertThat(failure(onUi(() -> connections.shell(noAgent.id(), 80, 24, s -> { })))).isEqualTo("SSH agent not available");
    }

    @Test void emptyAgentExplainsMissingKeysAndRetryUsesNewlyLoadedKey(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var agent = new FakeAgent()) {
            connections(dir, Optional.of(new AgentClient(agent::handle)));
            RemoteHost target = host("imported", server.port(), "deploy", Auth.AGENT, Optional.empty());
            String error = failure(onUi(() -> connections.shell(target.id(), 80, 24, s -> { })));
            assertThat(error).contains("SSH agent", "no usable keys", "Vault");
            assertThat(questions).as("no host-key prompt when no key can be offered").isEmpty();
            assertThat(onUi(connections::channelCount)).isZero();

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            agent.keys.add(pair);
            server.allow(pair.getPublic());
            assertThat(readUntil(shell(target).connection(), "\r\n")).startsWith("READY");
        }
    }

    @Test void hostKeyDecisionsAndChangesAreEnforced(@TempDir Path dir) throws Exception {
        int port;
        try (var server = new LoopbackServer()) {
            port = server.port();
            connections(dir, Optional.empty());
            RemoteHost host = host("prod", port, "", new Auth.Vault(passwordCredential()), Optional.empty());
            decision = HostKeyVerifier.Decision.CANCEL;
            assertThat(failure(onUi(() -> connections.shell(host.id(), 80, 24, s -> { })))).isEqualTo("Host key not trusted");
            credentials.put(((Auth.Vault) host.auth()).credentialId(), new Credential(UUID.randomUUID(), "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null));
            decision = HostKeyVerifier.Decision.ONCE;
            assertThat(readUntil(shell(host).connection(), "\r\n")).startsWith("READY");
            assertThat(dir.resolve("known_hosts")).doesNotExist();
            onUi(connections::close);
            connections(dir, Optional.empty());
            credentials.put(((Auth.Vault) host.auth()).credentialId(), new Credential(UUID.randomUUID(), "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null));
            decision = HostKeyVerifier.Decision.TRUST;
            assertThat(readUntil(shell(host).connection(), "\r\n")).startsWith("READY");
            assertThat(questions).hasSize(3);
            onUi(connections::close);
        }
        try (var replaced = new LoopbackServer(port)) {
            connections(dir, Optional.empty());
            RemoteHost host = host("prod", port, "", new Auth.Vault(passwordCredential()), Optional.empty());
            assertThat(failure(onUi(() -> connections.shell(host.id(), 80, 24, s -> { })))).startsWith("Host key rejected: fingerprint changed");
            assertThat(questions).as("a changed key never prompts").hasSize(3);
        }
    }

    @Test void failuresHaveTheSpecsMessages(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            UUID wrong = UUID.randomUUID();
            credentials.put(wrong, new Credential(wrong, "bad", Kind.ACCOUNT_PASSWORD, "deploy", "nope".toCharArray(), null, null));
            assertThat(failure(onUi(() -> connections.shell(host("bad", server.port(), "", new Auth.Vault(wrong), Optional.empty()).id(), 80, 24, s -> { })))).isEqualTo("Authentication failed (tried password)");
            assertThat(failure(onUi(() -> connections.shell(host("denied", server.port(), "", new Auth.Vault(UUID.randomUUID()), Optional.empty()).id(), 80, 24, s -> { })))).isEqualTo("Credential denied");
            RemoteHost unresolvable = RemoteHost.create("nowhere", "no-such-host.invalid", 22, "u", new Auth.Vault(passwordCredential()), "", Optional.empty());
            hosts.put(unresolvable.id(), unresolvable);
            assertThat(failure(onUi(() -> connections.shell(unresolvable.id(), 80, 24, s -> { })))).isEqualTo("Could not resolve host no-such-host.invalid");
            int closed; try (var probe = new ServerSocket(0)) { closed = probe.getLocalPort(); }
            assertThat(failure(onUi(() -> connections.shell(host("refused", closed, "deploy", new Auth.Vault(passwordCredential()), Optional.empty()).id(), 80, 24, s -> { })))).isEqualTo("Connection refused");
            assertThat(failure(onUi(() -> connections.shell(UUID.randomUUID(), 80, 24, s -> { })))).isEqualTo("Host not found");
        }
    }

    @Test void cancellingDuringConnectLeavesNothingBehind(@TempDir Path dir) throws Exception {
        try (var silent = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            connections(dir, Optional.empty());
            RemoteHost host = host("silent", silent.getLocalPort(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            CompletableFuture<Connections.Shell> future = onUi(() -> connections.shell(host.id(), 80, 24, s -> { }));
            Thread.sleep(300);
            future.cancel(true);
            Thread.sleep(300);
            assertThat(onUi(() -> connections.connected(host.id()))).isFalse();
            assertThat(onUi(connections::channelCount)).isZero();
        }
    }

    @Test void proxyJumpGoesThroughTheJumpHostsSharedSession(@TempDir Path dir) throws Exception {
        try (var bastion = new LoopbackServer(); var target = new LoopbackServer()) {
            connections(dir, Optional.empty());
            RemoteHost jump = host("bastion", bastion.port(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            RemoteHost inner = host("inner", target.port(), "", new Auth.Vault(passwordCredential()), Optional.of(jump.id()));
            assertThat(readUntil(shell(inner).connection(), "\r\n")).startsWith("READY");
            assertThat(onUi(() -> connections.connected(jump.id()))).as("the jump session is shared and kept").isTrue();
            assertThat(questions).extracting(HostKeyVerifier.Question::port).containsExactly(bastion.port(), target.port());
            assertThat(readUntil(shell(jump).connection(), "\r\n")).startsWith("READY");
            assertThat(questions).as("no new prompt for the shared jump session").hasSize(2);
        }
    }
    @Test void cancellingWithdrawsVaultRequest(@TempDir Path dir) throws Exception {
        var pending = new CompletableFuture<Optional<Credential>>();
        connections = new Connections(() -> settings, new KnownHosts(dir.resolve("known_hosts"), Optional.empty()), Optional.empty(),
            id -> Optional.ofNullable(hosts.get(id)), Optional.of(id -> pending), q -> CompletableFuture.completedFuture(decision), background, ui,
            (delay, task) -> () -> {});
        RemoteHost target = host("waiting", 22, "u", new Auth.Vault(UUID.randomUUID()), Optional.empty());
        var future = onUi(() -> connections.shell(target.id(), 80, 24, status -> {}));
        onUi(() -> future.cancel(true));
        onUi(() -> {});
        assertThat(pending).isCancelled();
    }

    @Test void cancellingSecondKeyRequestClosesFirstCopy(@TempDir Path dir) throws Exception {
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        var first = new Credential(firstId, "first", Kind.SSH_KEY, null, null, null, new byte[] {1}, null);
        var second = new CompletableFuture<Optional<Credential>>();
        var requested = new CompletableFuture<Void>();
        connections = new Connections(() -> settings, new KnownHosts(dir.resolve("known_hosts"), Optional.empty()), Optional.empty(),
            id -> Optional.ofNullable(hosts.get(id)), Optional.of(id -> {
                if (id.equals(firstId)) return CompletableFuture.completedFuture(Optional.of(first));
                requested.complete(null); return second;
            }), q -> CompletableFuture.completedFuture(decision), background, ui, (delay, task) -> () -> {});
        var target = host("waiting", 22, "u", new Auth.VaultKeys(List.of(firstId, secondId)), Optional.empty());
        var future = onUi(() -> connections.shell(target.id(), 80, 24, status -> {}));
        requested.get(5, TimeUnit.SECONDS);
        onUi(() -> future.cancel(true)); onUi(() -> {}); onUi(() -> {});
        assertThat(second).isCancelled();
        assertThatThrownBy(first::keyBytes).isInstanceOf(IllegalStateException.class);
    }

    @Test void managedHostRejectsPathAndPasswordCredentials(@TempDir Path dir) {
        connections(dir, Optional.empty());
        UUID pathId = UUID.randomUUID();
        credentials.put(pathId, new Credential(pathId, "legacy", Kind.SSH_KEY, null, null, dir.resolve("absent"), null));
        for (UUID id : List.of(pathId, passwordCredential())) {
            var target = host(id.toString(), 22, "u", new Auth.VaultKeys(List.of(id)), Optional.empty());
            assertThat(failure(onUi(() -> connections.shell(target.id(), 80, 24, s -> {})))).contains("Import this key");
            assertThat(issued).allSatisfy(copy -> assertThatThrownBy(copy::keyBytes).isInstanceOf(IllegalStateException.class));
        }
    }

    @Test void callbacksUseUiAndJumpReleasesAfterLastShell(@TempDir Path dir) throws Exception {
        try (var bastion = new LoopbackServer(); var target = new LoopbackServer()) {
            connections(dir, Optional.empty());
            RemoteHost jump = host("jump", bastion.port(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            RemoteHost inner = host("inner", target.port(), "", new Auth.Vault(passwordCredential()), Optional.of(jump.id()));
            var threads = new java.util.concurrent.CopyOnWriteArrayList<String>();
            var shell = onUi(() -> connections.shell(inner.id(), 80, 24, status -> threads.add(Thread.currentThread().getName()))).get(15, TimeUnit.SECONDS);
            assertThat(threads).allMatch(name -> name.equals("ui"));
            shell.connection().close().run();
            onUi(() -> {});
            onUi(scheduled.getFirst());
            onUi(() -> {});
            assertThat(scheduled).as("jump also becomes idle after its dependent closes").isNotEmpty();
            onUi(() -> { for (Runnable task : List.copyOf(scheduled)) task.run(); });
            onUi(() -> {});
            assertThat(onUi(() -> connections.connected(jump.id()))).isFalse();
        }
    }

    @Test void cyclicJumpFailsPromptly(@TempDir Path dir) {
        connections(dir, Optional.empty());
        RemoteHost a = host("a", 22, "u", new Auth.Vault(passwordCredential()), Optional.empty());
        RemoteHost b = host("b", 22, "u", new Auth.Vault(passwordCredential()), Optional.of(a.id()));
        hosts.put(a.id(), a.withEdited("a", "127.0.0.1", 22, "u", a.auth(), "", Optional.of(b.id())));
        assertThat(failure(onUi(() -> connections.shell(a.id(), 80, 24, status -> {})))).contains("jump host");
    }

    @Test void passphraseProtectedVaultKeyAuthenticates(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            char[] passphrase = "test Unicode \u03c0 passphrase".toCharArray();
            SshKey generated = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "encrypted", "test", passphrase);
            server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(generated.publicPath()).strip()).resolvePublicKey(null, null, null));
            connections(dir, Optional.empty());
            UUID id = UUID.randomUUID();
            credentials.put(id, new Credential(id, "encrypted", Kind.SSH_KEY, null, null, generated.privatePath(), passphrase));
            RemoteHost target = host("encrypted", server.port(), "deploy", new Auth.Vault(id), Optional.empty());
            assertThat(readUntil(shell(target).connection(), "\r\n")).startsWith("READY");
            assertThat(issuedPassphrases.getLast()).containsOnly((char) 0);
        }
    }

    @Test void inspectsOperatingSystemOnAnExistingSessionWithoutOpeningAnotherShell(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "NAME=Ubuntu\nPRETTY_NAME=\"Ubuntu 24.04 LTS\"\n"));
            connections(dir, Optional.empty());
            var host = host("linux", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = shell(host);
            var info = onUi(() -> connections.inspect(shell)).get(5, TimeUnit.SECONDS);
            assertThat(info.os()).isEqualTo("Ubuntu 24.04 LTS");
            assertThat(info.address()).isEqualTo("127.0.0.1");
            assertThat(onUi(connections::channelCount)).isEqualTo(1);
            assertThat(server.execCommands).containsExactly("uname -s", "cat /etc/os-release");
            onUi(() -> connections.inspect(shell)).get(5, TimeUnit.SECONDS);
            assertThat(server.execCommands).hasSize(2);
            shell.connection().close().run();
        }
    }

    @Test void unavailableHostInformationDoesNotBreakTheShell(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("unknown", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = shell(host);
            var info = onUi(() -> connections.inspect(shell)).get(5, TimeUnit.SECONDS);
            assertThat(info.os()).isEmpty();
            assertThat(shell.connection().exited()).isNotDone();
            shell.connection().close().run();
        }
    }

    @Test void silentMetadataCommandTimesOutWithoutBlockingOrClosingTheShell(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.execReplies(Map.of(), false);
            connections(dir, Optional.empty());
            var host = host("silent", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = shell(host);
            var info = onUi(() -> connections.inspect(shell)).get(6, TimeUnit.SECONDS);
            assertThat(info.os()).isEmpty();
            assertThat(shell.connection().exited()).isNotDone();
            assertThat(onUi(connections::channelCount)).isEqualTo(1);
            shell.connection().close().run();
        }
    }

    @Test void inspectionKeepsTheAuthenticatedEndpointWhenSavedHostChanges(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.execReplies(Map.of("uname -s", "Darwin\n"));
            connections(dir, Optional.empty());
            var original = host("mac", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = shell(original);
            var changed = original.withEdited("new", "different.example", 22, "deploy", original.auth(), "", Optional.empty());
            onUi(() -> hosts.put(original.id(), changed));
            assertThat(shell.host()).isEqualTo(original);
            var info = onUi(() -> connections.inspect(shell)).get(5, TimeUnit.SECONDS);
            var cache = new dev.jasper.remote.hosts.HostInfoCache(dir.resolve("facts.properties"));
            cache.put(shell.host(), info);
            assertThat(cache.get(original).os()).isEqualTo("macOS");
            assertThat(cache.get(changed).os()).isEmpty();
            shell.connection().close().run();
        }
    }

    @Test void dedicatedShellsGetTheirOwnConnectionsAndCloseWithTheirShell(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("followed", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shared = shell(host);
            var first = dedicatedShell(host);
            var second = dedicatedShell(host);
            awaitSessions(server, 3);
            assertThat(shared.folder()).isEmpty();
            assertThat(first.folder()).isPresent();
            assertThat(readUntil(first.connection(), "\r\n")).startsWith("READY");
            var lease = onUi(() -> connections.lease(shared.identity(), null, status -> { })).get(10, TimeUnit.SECONDS);
            awaitSessions(server, 3);
            lease.close();
            first.connection().close().run();
            awaitSessions(server, 2);
            assertThat(onUi(() -> List.copyOf(scheduled))).as("a dedicated connection does not linger").isEmpty();
            shared.connection().close().run();
            Thread.sleep(200);
            assertThat(server.server.getActiveSessions()).as("the shared connection lingers").hasSize(2);
            onUi(() -> { var tasks = List.copyOf(scheduled); scheduled.clear(); tasks.forEach(Runnable::run); });
            awaitSessions(server, 1);
            second.connection().close().run();
            awaitSessions(server, 0);
        }
    }

    @Test void dedicatedShellProbesItsOwnSessionAndMapsExitStatuses(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("probe", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            var folder = shell.folder().orElseThrow();
            server.execResult("sh -s", 0, "jasper-cwd\0/srv/app\0".getBytes(StandardCharsets.UTF_8));
            assertThat(folder.read()).contains("/srv/app");
            assertThat(server.execCommands).containsExactly("sh -s");
            server.execResult("sh -s", 0, new byte[0]);
            assertThat(folder.read()).isEmpty();
            server.execResult("sh -s", 3, new byte[0]);
            assertThatThrownBy(folder::read).isInstanceOf(ShellFolder.Unsupported.class);
            server.execResult("sh -s", 1, new byte[0]);
            assertThatThrownBy(folder::read).isInstanceOf(IOException.class).isNotInstanceOf(ShellFolder.Unsupported.class).hasMessage("Directory probe failed");
            shell.connection().close().run();
        }
    }

    @Test void enterInTheShellSignalsItsFolder(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("enter", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            var entered = new java.util.concurrent.CountDownLatch(1);
            shell.folder().orElseThrow().onEnter(entered::countDown);
            type(shell.connection(), "ls\r");
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(readUntil(shell.connection(), "ls\r")).as("the bytes still reach the shell").contains("ls\r");
            shell.connection().close().run();
        }
    }

    @Test void inspectionUsesTheDedicatedShellsOwnConnection(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.execReplies(Map.of("uname -s", "Darwin\n"));
            connections(dir, Optional.empty());
            var host = host("mac", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            assertThat(onUi(() -> connections.inspect(shell)).get(5, TimeUnit.SECONDS).os()).isEqualTo("macOS");
            awaitSessions(server, 1);
            shell.connection().close().run();
            awaitSessions(server, 0);
        }
    }

    @Test @EnabledOnOs({OS.LINUX, OS.MAC}) void probeScriptRunsOverARealExecChannel(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            server.server.setCommandFactory(org.apache.sshd.server.shell.ProcessShellCommandFactory.INSTANCE);
            connections(dir, Optional.empty());
            var host = host("real", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var shell = dedicatedShell(host);
            // The test JVM normally has no sshd ancestor, so the script finds no shell. What matters: the real
            // `sh -s` received the whole script and its end of input, and exited 0 within the timeout.
            var result = shell.folder().orElseThrow().read();
            assertThat(result.isEmpty() || result.orElseThrow().startsWith("/")).isTrue();
            shell.connection().close().run();
        }
    }

}
