package dev.jasper.remote.projects;

import dev.jasper.remote.RemoteSettings;
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
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/** A loopback SSH server whose exec channels run real {@code sh} commands and whose SFTP sees this machine's files. */
final class ProjectsFixture implements AutoCloseable {
    final LoopbackServer server = new LoopbackServer();
    final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final Path home;
    final RemoteHost host;
    final Connections connections;

    ProjectsFixture(Path directory) throws Exception {
        home = Files.createDirectories(directory.resolve("home")).toRealPath();
        var files = new NativeFileSystemFactory();
        files.setUsersHomeDir(directory.resolve("users").toString());
        files.setCreateHome(true);
        server.server.setFileSystemFactory(files);
        server.server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        server.server.setCommandFactory((channel, command) -> {
            server.execCommands.add(command);
            return new RealExec(command, home);
        });
        host = RemoteHost.create("build", "127.0.0.1", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
        var settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO, false, false);
        connections = new Connections(() -> settings, new KnownHosts(directory.resolve("known_hosts"), Optional.empty()), Optional.empty(),
            id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty(), Optional.of(id -> CompletableFuture.completedFuture(Optional.of(
                new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)))),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.ONCE), background, ui, (delay, work) -> () -> { });
    }

    /** Runs {@code work} on the fixture's UI thread and waits for it. */
    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }

    /** A lease on the shared session, connecting first. */
    SessionLease lease() throws Exception {
        return onUi(() -> connections.lease(connections.identity(host.id()), null, status -> { })).get(10, TimeUnit.SECONDS);
    }

    @Override public void close() throws Exception {
        ui.submit(connections::close).get(10, TimeUnit.SECONDS);
        background.close();
        ui.close();
        server.close();
    }
}
