package dev.jasper.remote.client;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.agent.AgentClient;
import dev.jasper.remote.agent.MinaAgentFactory;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.HostInfo;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.vault.api.Credential;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;

/** Shared sessions; all registry state belongs to the UI executor. Each shell and dependent hop owns a reference. */
public final class Connections {
    public record Shell(RemoteHost host, TerminalConnection connection) {
        public UUID hostId() { return host.id(); }
    }
    private static final AttributeRepository.AttributeKey<HostKeyVerifier> VERIFY = new AttributeRepository.AttributeKey<>();
    private final Supplier<RemoteSettings> settings;
    private final KnownHosts trust;
    private final Optional<AgentClient> agent;
    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Optional<Function<UUID, CompletableFuture<Optional<Credential>>>> credentials;
    private final Function<HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt;
    private final Executor background, ui;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Map<UUID, Shared> sessions = new HashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private SshClient plainClient, agentClient;
    private int channels;
    private volatile boolean closed;

    /** Thread-safe ownership of in-flight I/O and prompts, including resources arriving after cancellation. */
    private static final class Resources {
        private final List<Runnable> cleanup = new ArrayList<>();
        private boolean closed;
        synchronized void check() { if (closed) throw new Failures.Failure("Cancelled"); }
        void add(Runnable action) {
            synchronized (this) { if (!closed) { cleanup.add(action); return; } }
            action.run();
        }
        void close() {
            List<Runnable> actions;
            synchronized (this) { if (closed) return; closed = true; actions = List.copyOf(cleanup); cleanup.clear(); }
            for (Runnable action : actions.reversed()) try { action.run(); } catch (RuntimeException ignored) {}
        }
    }
    private final class Shared {
        final RemoteHost host;
        final RemoteSettings settings = Connections.this.settings.get();
        final CompletableFuture<ClientSession> ready = new CompletableFuture<>();
        final Resources resources = new Resources();
        final HostKeyVerifier verifier;
        Shared parent;
        ClientSession session;
        CompletableFuture<HostInfo> info;
        int refs;
        boolean evicted;
        Runnable cancelLinger;
        Shared(RemoteHost host, Consumer<String> status) {
            this.host = host;
            verifier = new HostKeyVerifier(trust, question -> {
                var answer = new CompletableFuture<HostKeyVerifier.Decision>();
                resources.add(() -> answer.cancel(true));
                ui.execute(() -> {
                    if (answer.isDone()) return;
                    status.accept("Verifying host key…");
                    var actual = prompt.apply(question);
                    answer.whenComplete((value, failure) -> { if (answer.isCancelled()) ui.execute(() -> actual.cancel(true)); });
                    actual.whenComplete((value, failure) -> { if (failure == null) answer.complete(value); else answer.completeExceptionally(failure); });
                });
                return answer;
            }, settings::authTimeout);
        }
    }
    public Connections(Supplier<RemoteSettings> settings, KnownHosts trust, Optional<AgentClient> agent, Function<UUID, Optional<RemoteHost>> hosts,
                       Optional<Function<UUID, CompletableFuture<Optional<Credential>>>> credentials,
                       Function<HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt,
                       Executor background, Executor ui, BiFunction<Duration, Runnable, Runnable> schedule) {
        this.settings = settings; this.trust = trust; this.agent = agent; this.hosts = hosts; this.credentials = credentials;
        this.prompt = prompt; this.background = background; this.ui = ui; this.schedule = schedule;
    }
    public int channelCount() { return channels; }
    public boolean connected(UUID id) { Shared s = sessions.get(id); return s != null && s.session != null && s.session.isOpen(); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Best-effort metadata on the existing transport, cached for its lifetime; never authenticates a new connection. */
    public CompletableFuture<HostInfo> inspect(Shell shell) {
        Shared shared = sessions.get(shell.hostId());
        if (closed || shared == null || shared.host != shell.host() || shared.session == null || !shared.session.isOpen()) return CompletableFuture.completedFuture(HostInfo.EMPTY);
        if (shared.info != null) return shared.info;
        shared.refs++;
        if (shared.cancelLinger != null) { shared.cancelLinger.run(); shared.cancelLinger = null; }
        shared.info = new CompletableFuture<>();
        onBackground(() -> HostProbe.read(shared.session, shared.parent == null)).whenComplete((info, failure) -> ui.execute(() -> {
            shared.info.complete(failure == null ? info : HostInfo.EMPTY);
            release(shared);
        }));
        return shared.info;
    }

    public CompletableFuture<Shell> shell(UUID id, int columns, int rows, Consumer<String> status) {
        var result = new CompletableFuture<Shell>();
        RemoteHost host = hosts.apply(id).orElse(null);
        if (closed || host == null) return CompletableFuture.failedFuture(new Failures.Failure(closed ? "Remote is stopping" : "Host not found"));
        try { validateChain(host); } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        Shared shared = acquire(host, status);
        boolean[] released = {false}, counted = {false};
        Runnable release = () -> ui.execute(() -> {
            if (released[0]) return;
            released[0] = true;
            if (counted[0]) { channels--; notifyChanged(); }
            release(shared);
        });
        result.whenComplete((ignored, failure) -> { if (result.isCancelled()) release.run(); });
        shared.ready.whenComplete((session, failure) -> ui.execute(() -> {
            if (result.isDone()) return;
            if (failure != null) { release.run(); result.completeExceptionally(failure(shared, failure)); return; }
            status.accept("Opening shell…");
            onBackground(() -> ShellChannels.open(session, columns, rows, shared.settings.connectTimeout(), release))
                .whenComplete((connection, openFailure) -> ui.execute(() -> {
                    if (openFailure != null) { release.run(); result.completeExceptionally(failure(shared, openFailure)); return; }
                    if (result.isDone()) { connection.close().run(); return; }
                    if (!released[0]) { counted[0] = true; channels++; notifyChanged(); }
                    if (!result.complete(new Shell(shared.host, connection))) connection.close().run();
                }));
        }));
        return result;
    }
    private Failures.Failure failure(Shared shared, Throwable failure) {
        return new Failures.Failure(Failures.message(failure, shared.settings.connectTimeout(), shared.host.hostname()), failure);
    }
    private void validateChain(RemoteHost host) {
        var seen = new java.util.HashSet<UUID>();
        RemoteHost current = host;
        while (true) {
            if (!seen.add(current.id())) throw new Failures.Failure("jump host cycle");
            if (current.jump().isEmpty()) return;
            current = hosts.apply(current.jump().get()).orElseThrow(() -> new Failures.Failure("jump host missing"));
        }
    }
    private Shared acquire(RemoteHost host, Consumer<String> status) {
        Shared prior = sessions.get(host.id());
        if (prior != null && prior.session != null && !prior.session.isOpen()) { evict(prior); prior = null; }
        if (prior != null) {
            prior.refs++;
            if (prior.cancelLinger != null) { prior.cancelLinger.run(); prior.cancelLinger = null; }
            return prior;
        }
        Shared fresh = new Shared(host, status); fresh.refs = 1; sessions.put(host.id(), fresh);
        if (host.jump().isPresent()) fresh.parent = acquire(hosts.apply(host.jump().get()).orElseThrow(), status);
        CompletableFuture<ClientSession> via = fresh.parent == null ? CompletableFuture.completedFuture(null) : fresh.parent.ready;
        via.whenComplete((jump, failure) -> ui.execute(() -> {
            if (fresh.evicted) return;
            if (failure != null) { fail(fresh, failure); return; }
            CompletableFuture<List<Credential>> credential;
            try { credential = credentialFor(host); } catch (RuntimeException bad) { fail(fresh, bad); return; }
            fresh.resources.add(() -> ui.execute(() -> credential.cancel(true)));
            credential.whenComplete((secret, denied) -> ui.execute(() -> {
                if (fresh.evicted) { if (secret != null) secret.forEach(Credential::close); return; }
                if (denied != null) { fail(fresh, denied); return; }
                if (!(host.auth() instanceof Auth.Agent) && secret.isEmpty()) { fail(fresh, new Failures.Failure("Credential denied")); return; }
                status.accept("Connecting…");
                onBackground(() -> connect(host, secret, Optional.ofNullable(jump), fresh, status)).whenComplete((session, problem) -> ui.execute(() -> {
                    if (problem != null) { fail(fresh, problem); return; }
                    if (fresh.evicted) { session.close(true); return; }
                    fresh.session = session;
                    session.addCloseFutureListener(done -> ui.execute(() -> evict(fresh)));
                    if (!session.isOpen()) { fail(fresh, new Failures.Failure("connection lost")); return; }
                    fresh.ready.complete(session); notifyChanged();
                }));
            }));
        }));
        return fresh;
    }
    private CompletableFuture<List<Credential>> credentialFor(RemoteHost host) {
        if (host.auth() instanceof Auth.Agent)
            return agent.isPresent() && settings.get().useAgent() ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.failedFuture(new Failures.Failure("SSH agent not available"));
        if (credentials.isEmpty()) return CompletableFuture.failedFuture(new Failures.Failure("needs Credential Vault"));
        List<UUID> ids = host.auth() instanceof Auth.Vault v ? List.of(v.credentialId()) : ((Auth.VaultKeys) host.auth()).credentialIds();
        var result = new CompletableFuture<List<Credential>>();
        var acquired = new ArrayList<Credential>();
        var active = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Optional<Credential>>>();
        result.whenComplete((values, failure) -> {
            if (failure != null) ui.execute(() -> {
                acquired.forEach(Credential::close); acquired.clear();
                var pending = active.get(); if (pending != null) pending.cancel(false);
            });
        });
        acquireCredential(ids, 0, acquired, active, result, host.auth() instanceof Auth.VaultKeys);
        return result;
    }
    private void acquireCredential(List<UUID> ids, int at, List<Credential> acquired,
            java.util.concurrent.atomic.AtomicReference<CompletableFuture<Optional<Credential>>> active,
            CompletableFuture<List<Credential>> result, boolean managedOnly) {
        if (result.isDone()) return;
        if (at == ids.size()) { result.complete(List.copyOf(acquired)); return; }
        CompletableFuture<Optional<Credential>> request;
        try { request = credentials.orElseThrow().apply(ids.get(at)); active.set(request); }
        catch (RuntimeException failure) { result.completeExceptionally(failure); return; }
        request.whenComplete((value, failure) -> ui.execute(() -> {
            if (result.isDone()) { if (value != null) value.ifPresent(Credential::close); return; }
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (value.isEmpty()) { result.completeExceptionally(new Failures.Failure("Credential denied")); return; }
            Credential secret = value.orElseThrow();
            if (managedOnly && (secret.kind() != dev.jasper.vault.api.Kind.SSH_KEY || secret.keyBytes().isEmpty() || secret.keyPath().isPresent())) {
                secret.close(); result.completeExceptionally(new Failures.Failure("Import this key into Vault before connecting")); return;
            }
            acquired.add(secret); acquireCredential(ids, at + 1, acquired, active, result, managedOnly);
        }));
    }
    /** Background: TCP (through the jump's forward when given), host key, authentication. Closes the credential. */
    private ClientSession connect(RemoteHost host, List<Credential> credential, Optional<ClientSession> jump, Shared shared, Consumer<String> status) throws Exception {
        RemoteSettings current = shared.settings;
        String username = host.username().isEmpty() ? credential.stream().flatMap(c -> c.username().stream()).findFirst().orElse("") : host.username();
        try {
            if (username.isEmpty()) throw new Failures.Failure("No username for " + host.name());
            if (host.auth() instanceof Auth.Agent) {
                try (AgentClient probe = agent.orElseThrow().withTimeout(current.authTimeout())) {
                    shared.resources.add(probe::close);
                    if (probe.identities().isEmpty()) {
                        throw new Failures.Failure("SSH agent has no usable keys; load a key or choose Vault.");
                    }
                } catch (IOException unavailable) {
                    throw new Failures.Failure("SSH agent unavailable: " + unavailable.getMessage(), unavailable);
                }
                shared.resources.check();
            }
            SshClient client = client(host.auth() instanceof Auth.Agent);
            shared.resources.check();
            String address = host.hostname(); int port = host.port();
            if (jump.isPresent()) {
                SshdSocketAddress bound = jump.get().startLocalPortForwarding(new SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, 0), new SshdSocketAddress(host.hostname(), host.port()));
                shared.resources.add(() -> { try { jump.get().stopLocalPortForwarding(bound); } catch (IOException ignored) {} });
                address = bound.getHostName(); port = bound.getPort();
            }
            var attributes = AttributeRepository.ofAttributesMap(Map.of(
                HostKeyVerifier.TARGET, new HostKeyVerifier.Target(host.hostname(), host.port()), VERIFY, shared.verifier));
            var connecting = client.connect(username, address, port, attributes);
            shared.resources.add(connecting::cancel);
            ClientSession session = connecting.verify(current.connectTimeout()).getSession();
            shared.resources.add(() -> session.close(true));
            shared.resources.check();
            List<KeyPair> keys = new ArrayList<>();
            List<String> passwords = new ArrayList<>();
            try {
                if (!current.keepalive().isZero()) {
                    CoreModuleProperties.HEARTBEAT_INTERVAL.set(session, current.keepalive());
                    CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(session, current.keepalive());
                    CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(session, 3);
                }
                CoreModuleProperties.AUTH_TIMEOUT.set(session, current.authTimeout());
                List<String> tried = new ArrayList<>();
                for (Credential secret : credential) {
                    if (secret.keyPath().isPresent() || secret.keyBytes().isPresent()) {
                        String passphrase = secret.passphrase() == null ? null : new String(secret.passphrase());
                        try (var stream = secret.keyBytes().isPresent() ? new java.io.ByteArrayInputStream(secret.keyBytes().orElseThrow()) : Files.newInputStream(secret.keyPath().orElseThrow())) {
                            Iterable<KeyPair> pairs = SecurityUtils.loadKeyPairIdentities(session, NamedResource.ofName("Vault SSH key"), stream,
                                passphrase == null ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(passphrase));
                            for (KeyPair pair : pairs) { session.addPublicKeyIdentity(pair); keys.add(pair); }
                        } catch (IOException | GeneralSecurityException unreadable) { throw new Failures.Failure("Could not read the Vault key " + secret.name()); }
                        tried.add("publickey");
                    }
                    if (secret.password() != null) { String password = new String(secret.password()); session.addPasswordIdentity(password); passwords.add(password); tried.add("password"); }
                }
                if (credential.isEmpty()) tried.add("publickey (agent)");
                ui.execute(() -> status.accept("Authenticating…"));
                try { var auth = session.auth(); shared.resources.add(auth::cancel); auth.verify(current.authTimeout()); }
                catch (IOException denied) {
                    String rejection = session.getAttribute(HostKeyVerifier.REJECTION);
                    if (rejection != null) throw new Failures.Failure(rejection);
                    String text = String.valueOf(denied.getMessage());
                    if (text.contains("timeout") || text.contains("timed out")) throw new Failures.Failure("Timed out after " + current.authTimeout().toSeconds() + " s");
                    throw new Failures.Failure("Authentication failed (tried " + String.join(", ", tried) + ")");
                }
                return session;
            } catch (Exception failure) {
                session.close(false);
                throw failure;
            } finally {
                keys.forEach(session::removePublicKeyIdentity);
                passwords.forEach(session::removePasswordIdentity);
            }
        } finally {
            credential.forEach(Credential::close);
        }
    }


    private synchronized SshClient client(boolean withAgent) {
        if (closed) throw new Failures.Failure("Remote is stopping");
        SshClient existing = withAgent ? agentClient : plainClient;
        if (existing != null) return existing;
        SshClient client = SshClient.setUpDefaultClient();
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        client.setServerKeyVerifier((session, address, key) -> {
            HostKeyVerifier verifier = session.getConnectionContext().getAttribute(VERIFY);
            return verifier != null && verifier.verifyServerKey(session, address, key);
        });
        if (withAgent) agent.ifPresent(a -> client.setAgentFactory(new MinaAgentFactory(a)));
        client.start();
        if (withAgent) agentClient = client; else plainClient = client;
        return client;
    }
    private void fail(Shared shared, Throwable failure) { shared.ready.completeExceptionally(failure); evict(shared); }
    private void release(Shared shared) {
        if (shared.refs > 0) shared.refs--;
        if (shared.refs != 0 || shared.evicted) return;
        if (!shared.ready.isDone()) { evict(shared); return; }
        if (shared.cancelLinger == null) shared.cancelLinger = schedule.apply(settings.get().linger(), () -> ui.execute(() -> { if (shared.refs == 0) evict(shared); }));
    }
    private void evict(Shared shared) {
        if (shared.evicted) return;
        shared.evicted = true;
        sessions.remove(shared.host.id(), shared);
        if (shared.cancelLinger != null) { shared.cancelLinger.run(); shared.cancelLinger = null; }
        shared.ready.completeExceptionally(new Failures.Failure("connection lost"));
        shared.resources.close();
        if (shared.parent != null) { release(shared.parent); shared.parent = null; }
        notifyChanged();
    }
    public void close() {
        closed = true;
        for (Shared shared : List.copyOf(sessions.values())) evict(shared);
        background.execute(() -> { synchronized (this) {
            if (plainClient != null) plainClient.stop();
            if (agentClient != null) agentClient.stop();
            plainClient = null; agentClient = null;
        }});
    }
    private void notifyChanged() { listeners.forEach(Runnable::run); }
    private <T> CompletableFuture<T> onBackground(Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> { try { result.complete(work.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        return result;
    }
}
