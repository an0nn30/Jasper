package dev.jasper.remote.sftp;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * A loopback SSH server serving {@code <directory>/files} over SFTP, and a connection registry signed in to
 * it as {@code deploy}. Trust lives outside the served folder, so listings hold only what a test made.
 */
public final class SftpFixture implements AutoCloseable {
    public final LoopbackServer server = new LoopbackServer();
    public final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    public final ExecutorService ui = Executors.newSingleThreadExecutor();
    public final Path files;
    public final RemoteHost host;
    public final Connections connections;

    public SftpFixture(Path directory) throws Exception {
        files = Files.createDirectories(directory.resolve("files")).toRealPath();
        server.server.setFileSystemFactory(new VirtualFileSystemFactory(files));
        server.server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        host = RemoteHost.create("files", "127.0.0.1", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
        var settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO, false, false);
        connections = new Connections(() -> settings, new KnownHosts(directory.resolve("known_hosts"), Optional.empty()), Optional.empty(),
            id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty(), Optional.of(id -> CompletableFuture.completedFuture(Optional.of(
                new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)))),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.ONCE), background, ui, (delay, work) -> () -> {});
    }

    /** Runs {@code work} on the fixture's UI thread and waits for it. */
    public <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }
    public ConnectionIdentity identity() throws Exception { return onUi(() -> connections.identity(host.id())); }
    public SessionLease lease() throws Exception { return onUi(() -> connections.lease(connections.identity(host.id()), null, s -> {})).get(10, TimeUnit.SECONDS); }
    public SftpEndpoint endpoint() throws Exception { return new SftpEndpoint(lease(), Duration.ofSeconds(5)); }
    @Override public void close() throws Exception { ui.submit(connections::close).get(10, TimeUnit.SECONDS); background.close(); ui.close(); server.close(); }
}
