package dev.jasper.remote;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.NotConnectedException;
import dev.jasper.remote.api.RemoteFolder;
import dev.jasper.remote.api.RemoteProjects;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.client.StartFolder;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RemoteProjectsPluginTest {
    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    static void settle(FakePluginHost host) { for (int i = 0; i < 20; i++) { host.runBackground(); host.flush(); } }

    @Test void openHostInStartsTheShellInTheFolder(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            String start = StartFolder.command("/srv/app");
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", start, ""));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost saved = RemoteHost.create("build", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty())
                .withFollowDirectory(false);
            plugin.store().put(saved);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            plugin.openHostIn(context.terminals().window(window).orElseThrow(), saved, "/srv/app");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!server.execCommands.contains(start) && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
            assertThat(server.execCommands).contains(start);
            assertThat(host.openRequests()).hasSize(1);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static <T> T await(FakePluginHost host, CompletableFuture<T> future) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test void consumersFindRemoteProjects(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            server.execReplies(Map.of("uname -s", "Linux\n", "cat /etc/os-release", "", "sh -s", "jasper-list\0src/b.txt\0a.txt\0"));
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost saved = RemoteHost.create("build", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty());
            plugin.store().put(saved);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.remote"), Set.of(), consumer::set);
            RemoteProjects projects = consumer.get().services().require(RemoteProjects.class);
            assertThat(projects.hostName(saved.id())).contains("build");
            assertThat(projects.folder(new FileLocation.Sourced(projects.fileSourceId(), saved.id().toString(), "/srv/app")))
                .contains(new RemoteFolder(saved.id(), "build", "/srv/app"));
            assertThat(projects.connected(saved.id())).isFalse();
            var options = new ListOptions(List.of(), 1_000, Duration.ofSeconds(30), () -> false);
            var refused = projects.list(saved.id(), "/srv/app", options, Optional.empty()).toCompletableFuture();
            assertThatThrownBy(() -> await(host, refused)).hasCauseInstanceOf(NotConnectedException.class);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var listing = await(host, projects.list(saved.id(), "/srv/app", options, Optional.of(context.terminals().window(window).orElseThrow()))
                .toCompletableFuture());
            assertThat(listing.files()).containsExactly("a.txt", "src/b.txt");
            assertThat(projects.connected(saved.id())).as("the session lingers").isTrue();
            server.execReplies(Map.of("sh -s", "jasper-exists\0/srv/app\0.git\0"));
            var found = await(host, projects.exists(saved.id(), List.of("/srv/app"), Set.of(".git")).toCompletableFuture());
            assertThat(found).containsEntry("/srv/app", Set.of(".git"));
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
