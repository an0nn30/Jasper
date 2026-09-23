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
import dev.jasper.sdk.terminal.WindowHandle;
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
    static {
        // Each plugin has its own BC classes. A JVM-global named provider may return keys
        // owned by a different plugin loader; use this loader's provider instance instead.
        SecurityUtils.registerSecurityProvider(new org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar() {
            private final java.security.Provider local = new org.bouncycastle.jce.provider.BouncyCastleProvider();
            @Override public boolean isNamedProviderUsed() { return false; }
            @Override public java.security.Provider getSecurityProvider() { return local; }
        });
    }

    public record Shell(RemoteHost host, ConnectionIdentity identity, TerminalConnection connection) {
        public UUID hostId() { return host.id(); }
    }
    private static final AttributeRepository.AttributeKey<HostKeyVerifier> VERIFY = new AttributeRepository.AttributeKey<>();
    private final Supplier<RemoteSettings> settings;
    private final KnownHosts trust;
    private final Optional<AgentClient> agent;
    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Optional<BiFunction<WindowHandle, UUID, CompletableFuture<Optional<Credential>>>> credentials;
    private final BiFunction<WindowHandle, HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt;
    private final Executor background, ui;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Map<ConnectionIdentity, Shared> sessions = new HashMap<>();
    private final java.util.Set<CompletableFuture<Prepared>> preparations = new java.util.HashSet<>();
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
        final ConnectionIdentity identity;
        final Map<Object, WindowHandle> owners = new java.util.LinkedHashMap<>();
        WindowHandle promptOwner;
        CompletableFuture<HostKeyVerifier.Decision> promptFuture;
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
        Shared(ConnectionIdentity identity, Consumer<String> status) {
            this.identity = identity; this.host = identity.host();
            verifier = new HostKeyVerifier(trust, question -> {
                var answer = new CompletableFuture<HostKeyVerifier.Decision>();
                resources.add(() -> answer.cancel(true));
                ui.execute(() -> {
                    if (answer.isDone()) return;
                    status.accept("Verifying host key…");
                    ask(this, question, answer);
                });
                return answer;
            }, settings::authTimeout);
        }
    }
    public Connections(Supplier<RemoteSettings> settings, KnownHosts trust, Optional<AgentClient> agent, Function<UUID, Optional<RemoteHost>> hosts,
                       Optional<Function<UUID, CompletableFuture<Optional<Credential>>>> credentials,
                       Function<HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt,
                       Executor background, Executor ui, BiFunction<Duration, Runnable, Runnable> schedule) {
        this(settings, trust, agent, hosts, credentials.map(source -> (owner, id) -> source.apply(id)),
            (owner, question) -> prompt.apply(question), background, ui, schedule);
    }
    public Connections(Supplier<RemoteSettings> settings, KnownHosts trust, Optional<AgentClient> agent, Function<UUID, Optional<RemoteHost>> hosts,
                       Optional<BiFunction<WindowHandle, UUID, CompletableFuture<Optional<Credential>>>> credentials,
                       BiFunction<WindowHandle, HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt,
                       Executor background, Executor ui, BiFunction<Duration, Runnable, Runnable> schedule) {
        this.settings = settings; this.trust = trust; this.agent = agent; this.hosts = hosts; this.credentials = credentials;
        this.prompt = prompt; this.background = background; this.ui = ui; this.schedule = schedule;
    }
    public int channelCount() { return channels; }
    public boolean connected(UUID id) { return sessions.values().stream().anyMatch(s -> s.host.id().equals(id) && s.session != null && s.session.isOpen()); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Best-effort metadata on the existing transport, cached for its lifetime; never authenticates a new connection. */
    public CompletableFuture<HostInfo> inspect(Shell shell) {
        Shared shared = sessions.get(shell.identity());
        if (closed || shared == null || shared.session == null || !shared.session.isOpen()) return CompletableFuture.completedFuture(HostInfo.EMPTY);
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

    /** Captures the configured route without prompting. Empty logins are resolved before lease sharing. */
    public ConnectionIdentity identity(UUID id) {
        var route = new ArrayList<ConnectionIdentity.Hop>();
        var seen = new java.util.HashSet<UUID>();
        UUID current = id;
        while (current != null) {
            if (!seen.add(current)) throw new Failures.Failure("jump host cycle");
            var host = hosts.apply(current).orElseThrow(() -> new Failures.Failure(route.isEmpty() ? "Host not found" : "jump host missing"));
            route.add(new ConnectionIdentity.Hop(host, host.username()));
            current = host.jump().orElse(null);
        }
        return new ConnectionIdentity(route);
    }

    /** Resolves current effective accounts without connecting; used for durable resume validation. */
    public CompletableFuture<ConnectionIdentity> resolveIdentity(UUID id, WindowHandle owner) {
        var result = new CompletableFuture<ConnectionIdentity>();
        var prepared = prepare(identity(id), owner);
        result.whenComplete((v, e) -> { if (result.isCancelled()) prepared.cancel(true); });
        prepared.whenComplete((value, failure) -> ui.execute(() -> {
            if (failure != null) result.completeExceptionally(failure);
            else { value.close(); result.complete(value.identity()); }
        }));
        return result;
    }

    private final class Prepared implements AutoCloseable {
        final List<ConnectionIdentity.Hop> route = new ArrayList<>();
        final Map<UUID, List<Credential>> secrets = new HashMap<>();
        ConnectionIdentity identity() { return new ConnectionIdentity(route); }
        @Override public void close() { secrets.values().forEach(list -> list.forEach(Credential::close)); secrets.clear(); }
    }

    private CompletableFuture<Prepared> prepare(ConnectionIdentity identity, WindowHandle owner) {
        var result = new CompletableFuture<Prepared>();
        var prepared = new Prepared();
        preparations.add(result);
        result.whenComplete((value, failure) -> ui.execute(() -> preparations.remove(result)));
        var active = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<List<Credential>>>();
        result.whenComplete((value, failure) -> { if (failure != null) ui.execute(() -> {
            var pending = active.get(); if (pending != null) pending.cancel(true); prepared.close();
        }); });
        prepareHop(identity, owner, 0, prepared, active, result);
        return result;
    }

    private void prepareHop(ConnectionIdentity identity, WindowHandle owner, int index, Prepared prepared,
            java.util.concurrent.atomic.AtomicReference<CompletableFuture<List<Credential>>> active, CompletableFuture<Prepared> result) {
        if (result.isDone()) return;
        if (closed || owner != null && !owner.isOpen()) { result.cancel(true); return; }
        if (index == identity.hops().size()) { result.complete(prepared); return; }
        var hop = identity.hops().get(index);
        if (!hop.username().isBlank()) {
            prepared.route.add(hop); prepareHop(identity, owner, index + 1, prepared, active, result); return;
        }
        var request = credentialFor(hop.host(), owner); active.set(request);
        request.whenComplete((values, failure) -> ui.execute(() -> {
            if (result.isDone()) { if (values != null) values.forEach(Credential::close); return; }
            if (failure != null) { result.completeExceptionally(failure); return; }
            String username = values.stream().flatMap(c -> c.username().stream()).findFirst().orElse("");
            if (username.isBlank()) { values.forEach(Credential::close); result.completeExceptionally(new Failures.Failure("No username for " + hop.host().name())); return; }
            prepared.secrets.put(hop.host().id(), values);
            prepared.route.add(new ConnectionIdentity.Hop(hop.host(), username));
            prepareHop(identity, owner, index + 1, prepared, active, result);
        }));
    }

    public CompletableFuture<SessionLease> lease(ConnectionIdentity identity, WindowHandle owner, Consumer<String> status) {
        var result = new CompletableFuture<SessionLease>();
        if (closed) return CompletableFuture.failedFuture(new Failures.Failure("Remote is stopping"));
        var prepared = prepare(identity, owner);
        result.whenComplete((v, e) -> { if (result.isCancelled()) prepared.cancel(true); });
        prepared.whenComplete((value, failure) -> ui.execute(() -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (result.isDone() || closed) { value.close(); if (closed) result.cancel(true); return; }
            Shared shared;
            try { shared = acquire(value.identity(), owner, status, value); }
            catch (RuntimeException bad) { value.close(); result.completeExceptionally(bad); return; }
            value.close();
            Object token = new Object(); shared.owners.put(token, owner);
            var released = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable release = () -> ui.execute(() -> {
                if (!released.compareAndSet(false, true)) return;
                shared.owners.remove(token); release(shared); refreshPrompts();
            });
            result.whenComplete((v, e) -> { if (result.isCancelled()) release.run(); });
            shared.ready.whenComplete((session, problem) -> ui.execute(() -> {
                if (result.isDone()) return;
                if (problem != null) { release.run(); result.completeExceptionally(failure(shared, problem)); return; }
                if (owner != null && !owner.isOpen()) { release.run(); result.cancel(true); return; }
                var lease = new SessionLease(shared.identity, session, release);
                if (!result.complete(lease)) lease.close();
            }));
        }));
        return result;
    }

    public CompletableFuture<Shell> shell(UUID id, int columns, int rows, Consumer<String> status) {
        return shell(id, null, columns, rows, status);
    }
    public CompletableFuture<Shell> shell(UUID id, WindowHandle owner, int columns, int rows, Consumer<String> status) {
        var result = new CompletableFuture<Shell>();
        CompletableFuture<SessionLease> requested;
        try { requested = lease(identity(id), owner, status); }
        catch (RuntimeException bad) { return CompletableFuture.failedFuture(bad); }
        result.whenComplete((v, e) -> { if (result.isCancelled()) requested.cancel(true); });
        requested.whenComplete((lease, failure) -> ui.execute(() -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (result.isDone()) { lease.close(); return; }
            boolean[] counted = {false}, released = {false};
            Runnable release = () -> ui.execute(() -> {
                if (released[0]) return; released[0] = true;
                if (counted[0]) { channels--; notifyChanged(); } lease.close();
            });
            result.whenComplete((v, e) -> { if (result.isCancelled()) release.run(); });
            status.accept("Opening shell…");
            onBackground(() -> ShellChannels.open(lease.session(), columns, rows, settings.get().connectTimeout(), release))
                .whenComplete((connection, problem) -> ui.execute(() -> {
                    if (problem != null) { release.run(); result.completeExceptionally(problem); return; }
                    if (result.isDone()) { connection.close().run(); return; }
                    if (!released[0]) { counted[0] = true; channels++; notifyChanged(); }
                    if (!result.complete(new Shell(lease.host(), lease.identity(), connection))) connection.close().run();
                }));
        }));
        return result;
    }

    private boolean uses(Shared candidate, Shared ancestor) {
        for (Shared at = candidate; at != null; at = at.parent) if (at == ancestor) return true;
        return false;
    }
    private WindowHandle promptOwner(Shared shared) {
        for (var candidate : sessions.values()) if (uses(candidate, shared))
            for (var owner : candidate.owners.values()) if (owner != null && owner.isOpen()) return owner;
        return null;
    }
    private boolean stillRequested(Shared shared, WindowHandle owner) {
        return sessions.values().stream().filter(s -> uses(s, shared)).anyMatch(s -> s.owners.containsValue(owner));
    }
    private void refreshPrompts() {
        for (var shared : List.copyOf(sessions.values())) if (shared.promptFuture != null && shared.promptOwner != null
                && (!shared.promptOwner.isOpen() || !stillRequested(shared, shared.promptOwner))) shared.promptFuture.cancel(true);
    }
    private void ask(Shared shared, HostKeyVerifier.Question question, CompletableFuture<HostKeyVerifier.Decision> answer) {
        if (answer.isDone()) return;
        WindowHandle owner = promptOwner(shared);
        shared.promptOwner = owner;
        var actual = prompt.apply(owner, question); shared.promptFuture = actual;
        answer.whenComplete((v, e) -> { if (answer.isCancelled()) ui.execute(() -> actual.cancel(true)); });
        actual.whenComplete((value, failure) -> ui.execute(() -> {
            if (shared.promptFuture == actual) shared.promptFuture = null;
            if (answer.isDone()) return;
            if ((failure != null || value == HostKeyVerifier.Decision.CANCEL) && owner != null
                    && (!owner.isOpen() || !stillRequested(shared, owner)) && promptOwner(shared) != null) {
                ask(shared, question, answer); return;
            }
            if (failure == null) answer.complete(value); else answer.completeExceptionally(failure);
        }));
    }
    private Failures.Failure failure(Shared shared, Throwable failure) {
        return new Failures.Failure(Failures.message(failure, shared.settings.connectTimeout(), shared.host.hostname()), failure);
    }
    private Shared acquire(ConnectionIdentity identity, WindowHandle owner, Consumer<String> status, Prepared prepared) {
        RemoteHost host = identity.host();
        Shared prior = sessions.get(identity);
        if (prior != null && prior.session != null && !prior.session.isOpen()) { evict(prior); prior = null; }
        if (prior != null) {
            prior.refs++;
            if (prior.cancelLinger != null) { prior.cancelLinger.run(); prior.cancelLinger = null; }
            return prior;
        }
        Shared fresh = new Shared(identity, status); fresh.refs = 1; sessions.put(identity, fresh);
        List<Credential> captured = prepared.secrets.remove(host.id());
        if (captured != null) fresh.resources.add(() -> captured.forEach(Credential::close));
        if (identity.parent() != null) fresh.parent = acquire(identity.parent(), owner, status, prepared);
        CompletableFuture<ClientSession> via = fresh.parent == null ? CompletableFuture.completedFuture(null) : fresh.parent.ready;
        via.whenComplete((jump, failure) -> ui.execute(() -> {
            if (fresh.evicted) return;
            if (failure != null) { fail(fresh, failure); return; }
            authenticate(fresh, jump, captured, owner, status);
        }));
        return fresh;
    }
    private void authenticate(Shared fresh, ClientSession jump, List<Credential> captured, WindowHandle owner, Consumer<String> status) {
            CompletableFuture<List<Credential>> credential;
            try { credential = captured == null ? credentialFor(fresh.host, owner) : CompletableFuture.completedFuture(captured); } catch (RuntimeException bad) { fail(fresh, bad); return; }
            fresh.resources.add(() -> ui.execute(() -> credential.cancel(true)));
            credential.whenComplete((secret, denied) -> ui.execute(() -> {
                if (fresh.evicted) { if (secret != null) secret.forEach(Credential::close); return; }
                if (denied != null) {
                    WindowHandle surviving = promptOwner(fresh);
                    if (owner != null && !owner.isOpen() && surviving != null) { authenticate(fresh, jump, null, surviving, status); return; }
                    fail(fresh, denied); return;
                }
                if (!(fresh.host.auth() instanceof Auth.Agent) && secret.isEmpty()) { fail(fresh, new Failures.Failure("Credential denied")); return; }
                status.accept("Connecting…");
                onBackground(() -> connect(fresh.host, secret, Optional.ofNullable(jump), fresh, status)).whenComplete((session, problem) -> ui.execute(() -> {
                    if (problem != null) { fail(fresh, problem); return; }
                    if (fresh.evicted) { session.close(true); return; }
                    fresh.session = session;
                    session.addCloseFutureListener(done -> ui.execute(() -> evict(fresh)));
                    if (!session.isOpen()) { fail(fresh, new Failures.Failure("connection lost")); return; }
                    fresh.ready.complete(session); notifyChanged();
                }));
            }));
    }
    private CompletableFuture<List<Credential>> credentialFor(RemoteHost host, WindowHandle owner) {
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
        acquireCredential(owner, ids, 0, acquired, active, result, host.auth() instanceof Auth.VaultKeys);
        return result;
    }
    private void acquireCredential(WindowHandle owner, List<UUID> ids, int at, List<Credential> acquired,
            java.util.concurrent.atomic.AtomicReference<CompletableFuture<Optional<Credential>>> active,
            CompletableFuture<List<Credential>> result, boolean managedOnly) {
        if (result.isDone()) return;
        if (at == ids.size()) { result.complete(List.copyOf(acquired)); return; }
        CompletableFuture<Optional<Credential>> request;
        try { request = credentials.orElseThrow().apply(owner, ids.get(at)); active.set(request); }
        catch (RuntimeException failure) { result.completeExceptionally(failure); return; }
        request.whenComplete((value, failure) -> ui.execute(() -> {
            if (result.isDone()) { if (value != null) value.ifPresent(Credential::close); return; }
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (value.isEmpty()) { result.completeExceptionally(new Failures.Failure("Credential denied")); return; }
            Credential secret = value.orElseThrow();
            if (managedOnly && (secret.kind() != dev.jasper.vault.api.Kind.SSH_KEY || secret.keyBytes().isEmpty() || secret.keyPath().isPresent())) {
                secret.close(); result.completeExceptionally(new Failures.Failure("Import this key into Vault before connecting")); return;
            }
            acquired.add(secret); acquireCredential(owner, ids, at + 1, acquired, active, result, managedOnly);
        }));
    }
    /** Background: TCP (through the jump's forward when given), host key, authentication. Closes the credential. */
    private ClientSession connect(RemoteHost host, List<Credential> credential, Optional<ClientSession> jump, Shared shared, Consumer<String> status) throws Exception {
        RemoteSettings current = shared.settings;
        String username = shared.identity.username();
        try {
            if (username.isEmpty()) throw new Failures.Failure("No username for " + host.name());
            if (host.username().isEmpty() && !credential.stream().flatMap(c -> c.username().stream()).findFirst().orElse("").equals(username))
                throw new Failures.Failure("Vault username changed; reopen this host before connecting");
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
        sessions.remove(shared.identity, shared);
        if (shared.cancelLinger != null) { shared.cancelLinger.run(); shared.cancelLinger = null; }
        shared.ready.completeExceptionally(new Failures.Failure("connection lost"));
        shared.resources.close();
        if (shared.parent != null) { release(shared.parent); shared.parent = null; }
        notifyChanged();
    }
    public void close() {
        closed = true;
        for (var preparing : List.copyOf(preparations)) preparing.cancel(true);
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
