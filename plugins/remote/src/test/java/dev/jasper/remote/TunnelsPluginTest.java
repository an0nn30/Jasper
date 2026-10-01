package dev.jasper.remote;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.tunnels.EchoServer;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelFile;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.ui.HostRows;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.tunnels.TunnelsScope;
import dev.jasper.remote.ui.tunnels.TunnelsTestAccess;
import dev.jasper.remote.ui.tunnels.TunnelsUi;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.list;
import static dev.jasper.remote.ui.UiTestAccess.menuFor;
import static org.assertj.core.api.Assertions.*;

class TunnelsPluginTest {
    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    /** Drains background work until the condition holds; sockets and MINA run on their own threads meanwhile. */
    static void await(FakePluginHost host, BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            RemotePluginTest.settle(host);
            if (condition.getAsBoolean()) return;
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }

    /** Remote beside the fake Vault, one trusted host on the loopback server, and an active window. */
    record Started(RemotePlugin plugin, PluginContext context, RemoteHost prod, UUID window) { }

    Started start(FakePluginHost host, LoopbackServer server, Path dir) throws Exception {
        var vault = new FakeVault();
        host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
        RemotePlugin plugin = plugin(dir.resolve("ssh"));
        var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
        UUID credential = vault.password("deploy login", "deploy", "s3cret");
        RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty());
        plugin.store().put(prod);
        RemotePluginTest.settle(host);
        new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        return new Started(plugin, context, prod, window);
    }

    @Test void registersTheTunnelsSurface(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.start(RemotePluginTest.INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains(TunnelsUi.TOGGLE + "|Toggle Tunnels|true", TunnelsUi.SHOW + "|Show Tunnels|true",
                TunnelsUi.NEW + "|New Tunnel...|true", TunnelsUi.FORWARD + "|Forward a Port...|true", TunnelsUi.STOP_ALL + "|Stop All Tunnels|false");
            assertThat(host.panels()).contains(TunnelsUi.PANEL + "|Tunnels|LEFT|" + TunnelsUi.TOGGLE);
            assertThat(host.scopes()).contains(TunnelsScope.ID + "|Tunnels|toggle,browse,edit");
            assertThat(host.menu("top:" + RemotePlugin.MENU)).containsSubsequence("item:" + TunnelsUi.TOGGLE, "item:" + TunnelsUi.NEW,
                "item:" + TunnelsUi.FORWARD, "item:" + TunnelsUi.STOP_ALL);
            assertThat(host.menu("context")).contains("item:" + TunnelsUi.FORWARD);
            assertThat(host.status()).as("hidden while nothing runs").isEmpty();
        }
    }

    @Test void aSavedTunnelStartsFromThePaletteForwardsAndOpensInTheBrowser(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
            started.plugin().tunnelStore().put(db);
            RemotePluginTest.settle(host);
            var manager = started.plugin().tunnels().manager();
            assertThat(host.searchScope(TunnelsScope.ID, "", started.window(), null)).containsExactly("tunnel." + db.id() + "|db|true");
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "toggle", started.window(), null);
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "the tunnel to come up");
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "hello")).isEqualTo("hello");
            assertThat(host.status()).containsExactly(TunnelsUi.STATUS + "|RIGHT|1 tunnel|db: up|" + TunnelsUi.TOGGLE);
            assertThat(host.actions()).contains(TunnelsUi.STOP_ALL + "|Stop All Tunnels|true");
            host.searchScope(TunnelsScope.ID, "", started.window(), null);
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "browse", started.window(), null);
            assertThat(host.browsed()).containsExactly(URI.create("http://127.0.0.1:" + port + "/"));
            host.searchScope(TunnelsScope.ID, "", started.window(), null);
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "toggle", started.window(), null);
            assertThat(manager.state(db.id())).isEqualTo(TunnelState.STOPPED);
            RemotePluginTest.settle(host);
            assertThat(host.status()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void aPaletteRowActsOnTheTunnelAsItIsSavedNow(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var first = new EchoServer(); var second = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var manager = started.plugin().tunnels().manager();
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", first.port());
            started.plugin().tunnelStore().put(db);
            RemotePluginTest.settle(host);
            host.searchScope(TunnelsScope.ID, "", started.window(), null);
            started.plugin().tunnelStore().put(db.withEdited("renamed", db.hostId(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", second.port()));
            RemotePluginTest.settle(host);
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "edit", started.window(), null);
            assertThat(host.windows()).as("the editor opens on the tunnel as saved now").containsExactly("dialog|Edit renamed|true");
            TunnelsTestAccess.cancel(started.plugin().tunnels().currentEditor()).doClick();
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "toggle", started.window(), null);
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "the tunnel to come up");
            assertThat(manager.running(db.id()).orElseThrow().targetPort()).as("it started as saved now").isEqualTo(second.port());
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "edited")).isEqualTo("edited");
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void forwardAPortStartsAQuickForwardThatStoppingForgets(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            assertThat(host.invoke(TunnelsUi.FORWARD, started.window(), null)).isTrue();
            assertThat(host.windows()).containsExactly("dialog|Forward a Port|true");
            var panel = tunnels.currentForward();
            TunnelsTestAccess.host(panel).setSelectedItem(started.prod());
            TunnelsTestAccess.targetHost(panel).setText("127.0.0.1");
            TunnelsTestAccess.remotePort(panel).setText(String.valueOf(echo.port()));
            TunnelsTestAccess.forward(panel).doClick();
            assertThat(host.windows()).as("the dialog closed").isEmpty();
            var manager = tunnels.manager();
            assertThat(manager.quickForwards()).singleElement().satisfies(tunnel -> assertThat(tunnel.quick()).isTrue());
            Tunnel quick = manager.quickForwards().getFirst();
            await(host, () -> manager.state(quick.id()) instanceof TunnelState.Up, "the quick forward to come up");
            int port = ((TunnelState.Up) manager.state(quick.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "quick")).isEqualTo("quick");
            assertThat(host.searchScope(TunnelsScope.ID, "", started.window(), null)).containsExactly("tunnel." + quick.id() + "|localhost:" + port + "|true");
            host.executeInScope(TunnelsScope.ID, "tunnel." + quick.id(), "toggle", started.window(), null);
            assertThat(manager.quickForwards()).as("stopping a quick forward forgets it").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void deletingAHostNamesTheTunnelsThatUseIt(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            started.plugin().tunnelStore().put(Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db", 5432));
            RemotePluginTest.settle(host);
            var hosts = (HostsPanel) host.openPanel(RemotePlugin.PANEL, started.window());
            int row = -1;
            for (int i = 0; i < list(hosts).getModel().getSize(); i++)
                if (list(hosts).getModel().getElementAt(i) instanceof HostRows.Host entry && entry.host().id().equals(started.prod().id())) row = i;
            JMenuItem delete = null;
            for (var item : menuFor(hosts, row).getComponents()) if (item instanceof JMenuItem entry && entry.getText().equals("Delete…")) delete = entry;
            assertThat(delete).isNotNull();
            delete.doClick();
            var confirm = started.plugin().currentConfirm();
            assertThat(((JLabel) confirm.getComponent(0)).getText())
                .isEqualTo("Delete the saved host prod? Open sessions stay connected. 1 tunnel uses this host (db); they stay, marked Host missing.");
        }
    }

    @Test void anOutsideEditRestartsARunningTunnelAndARemovalStopsIt(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var first = new EchoServer(); var second = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var store = started.plugin().tunnelStore();
            var manager = started.plugin().tunnels().manager();
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", first.port());
            store.put(db);
            RemotePluginTest.settle(host);
            manager.start(db, null);
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "up");
            Tunnel moved = db.withEdited("db", db.hostId(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", second.port());
            Files.writeString(store.file(), TunnelFile.format(List.of(moved)));
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(5)));
            store.poll();
            await(host, () -> manager.running(db.id()).map(Tunnel::targetPort).orElse(0) == second.port()
                && manager.state(db.id()) instanceof TunnelState.Up, "the restart");
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "moved")).isEqualTo("moved");
            Files.writeString(store.file(), TunnelFile.format(List.of()));
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(10)));
            store.poll();
            await(host, () -> manager.state(db.id()).equals(TunnelState.STOPPED), "a removed tunnel to stop");
        }
    }
}
