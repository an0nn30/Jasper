package dev.jasper.remote;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.client.StartFolder;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
}
