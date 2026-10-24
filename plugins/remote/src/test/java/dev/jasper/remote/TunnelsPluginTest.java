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
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.ui.tunnels.TunnelsScope;
import dev.jasper.remote.ui.tunnels.TunnelsTestAccess;
import dev.jasper.remote.ui.tunnels.TunnelsUi;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.jasper.remote.ui.UiTestAccess;
import static org.assertj.core.api.Assertions.*;

class TunnelsPluginTest {
    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(dev.jasper.remote.testsupport.Kits.UI, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
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
            assertThat(host.notifications().groups()).contains("dev.jasper.remote.tunnels|Tunnels|CARD");
            assertThat(host.actions()).contains(TunnelsUi.TOGGLE + "|Toggle Tunnels|true", TunnelsUi.SHOW + "|Show Tunnels|true",
                TunnelsUi.PICK + "|Start or Stop a Tunnel...|true", TunnelsUi.NEW + "|New Tunnel...|true", TunnelsUi.FORWARD + "|Forward a Port...|true", TunnelsUi.STOP_ALL + "|Stop All Tunnels|false");
            assertThat(host.panels()).contains(TunnelsUi.PANEL + "|Tunnels|LEFT|" + TunnelsUi.TOGGLE);
            assertThat(host.scopes()).contains(TunnelsScope.ID + "|Tunnels|toggle,browse,edit");
            assertThat(host.scopePresentation(TunnelsScope.ID)).as("spec 14.6").isEqualTo("NEUTRAL|tunnel|STANDARD,STANDARD,STANDARD");
            assertThat(host.menu("SESSION")).containsSubsequence("item:" + TunnelsUi.TOGGLE, "item:" + TunnelsUi.PICK, "item:" + TunnelsUi.NEW,
                "item:" + TunnelsUi.FORWARD, "item:" + TunnelsUi.STOP_ALL);
            assertThat(host.menu("context")).contains("item:" + TunnelsUi.FORWARD);
            assertThat(host.status()).as("hidden while nothing runs").isEmpty();
        }
    }

    @Test void startOrStopATunnelOpensTheTunnelsScope(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.start(RemotePluginTest.INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
            UUID window = host.addTerminalWindow();
            assertThat(host.invoke(TunnelsUi.PICK, window, null)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " " + TunnelsScope.ID + " - -");
        }
    }

    /**
     * Plan 2 Task 8b (Task 6d acceptance through the testkit): the Tunnels kit panel keeps its count and New Tunnel action
     * on the window's header through PanelHost.setHeader; the action opens the editor, which Escape (its cancel action)
     * closes. A tunnel through a missing host fails at once, so its switch shows the failure without any network; a
     * closed panel ignores later state changes.
     */
    @Test void theTunnelsHeaderKeepsItsCountAndNewActionAndTheSwitchShowsConfirmedState(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            host.start(RemotePluginTest.INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var tunnels = plugin.tunnels();
            Kits.edt(() -> host.openPanel(TunnelsUi.PANEL));
            assertThat(host.panelHeader(TunnelsUi.PANEL).detail()).as("no count while empty").isEmpty();
            Tunnel db = Tunnel.create("db", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
            plugin.tunnelStore().put(db);
            RemotePluginTest.settle(host);
            var header = host.panelHeader(TunnelsUi.PANEL);
            assertThat(header.detail()).isEqualTo("1");
            assertThat(header.actions()).extracting(dev.jasper.sdk.ui.PanelHeader.Action::label).containsExactly("New Tunnel…");
            Kits.edt(() -> header.actions().getFirst().invoke().run());
            assertThat(host.windows()).containsExactly("dialog|New Tunnel|true");
            Kits.edt(() -> {
                var editor = tunnels.currentEditor();
                assertThat(Kits.form(editor.view()).submission()).as("Enter saves nothing").isEqualTo(dev.jasper.sdk.ui.Nodes.FormSubmission.NONE);
                Kits.press(editor.view(), Kits.form(editor.view()).cancelAction());
            });
            assertThat(host.windows()).as("Escape cancelled").isEmpty();
            var panel = Kits.value(() -> TunnelsTestAccess.panel(tunnels));
            String row = "tunnel:" + db.id();
            Kits.edt(() -> {
                var item = Kits.item(Kits.node(panel.view(), dev.jasper.remote.ui.tunnels.TunnelsPanel.ROWS, dev.jasper.sdk.ui.Nodes.ItemList.class), row);
                var toggle = (dev.jasper.sdk.ui.Nodes.Toggle) item.trailing().getFirst();
                assertThat(toggle.checked()).isFalse();
                Kits.emit(panel.view(), new dev.jasper.sdk.ui.UiEvent.Toggled(toggle.id(), true));
            });
            RemotePluginTest.settle(host);
            assertThat(tunnels.manager().state(db.id())).as("the host is missing, so it fails without a connection").isEqualTo(new TunnelState.Failed("Host missing"));
            Kits.edt(() -> {
                var item = Kits.item(Kits.node(panel.view(), dev.jasper.remote.ui.tunnels.TunnelsPanel.ROWS, dev.jasper.sdk.ui.Nodes.ItemList.class), row);
                var toggle = (dev.jasper.sdk.ui.Nodes.Toggle) item.trailing().getFirst();
                assertThat(toggle.checked()).as("the confirmed state, never the requested one").isFalse();
                assertThat(toggle.state().error()).isEqualTo("Host missing");
                assertThat(((dev.jasper.sdk.ui.Nodes.StatusDot) item.leading().orElseThrow()).kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.StatusKind.DANGER);
            });
            Kits.edt(() -> host.closePanel(TunnelsUi.PANEL));
            assertThat(Kits.closed(panel.view())).as("the host disposed the view").isTrue();
            Kits.edt(() -> tunnels.manager().stop(db.id()));
            RemotePluginTest.settle(host);
            assertThat(Kits.value(() -> TunnelsTestAccess.panel(tunnels))).as("the closed panel left the plugin's map").isNull();
            assertThat(host.failures()).as("no late update reached the closed view").isEmpty();
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
            Kits.edt(() -> host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "edit", started.window(), null));
            assertThat(host.windows()).as("the editor opens on the tunnel as saved now").containsExactly("dialog|Edit renamed|true");
            Kits.edt(() -> {
                var editor = started.plugin().tunnels().currentEditor();
                assertThat(Kits.node(editor.view(), dev.jasper.remote.ui.tunnels.TunnelEditor.NAME, dev.jasper.sdk.ui.Nodes.Field.class).value()).isEqualTo("renamed");
                TunnelsTestAccess.cancel(editor);
            });
            assertThat(host.windows()).as("Cancel closed the editor").isEmpty();
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
            assertThat(Kits.value(() -> host.invoke(TunnelsUi.FORWARD, started.window(), null))).isTrue();
            assertThat(host.windows()).containsExactly("dialog|Forward a Port|true");
            Kits.edt(() -> {
                var panel = tunnels.currentForward();
                TunnelsTestAccess.host(panel, started.prod());
                TunnelsTestAccess.targetHost(panel, "127.0.0.1");
                TunnelsTestAccess.remotePort(panel, String.valueOf(echo.port()));
                TunnelsTestAccess.forward(panel);
            });
            assertThat(host.windows()).as("the dialog closed").isEmpty();
            var manager = tunnels.manager();
            assertThat(manager.quickForwards()).singleElement().satisfies(tunnel -> assertThat(tunnel.quick()).isTrue());
            Tunnel quick = manager.quickForwards().getFirst();
            await(host, () -> manager.state(quick.id()) instanceof TunnelState.Up, "the quick forward to come up");
            int port = ((TunnelState.Up) manager.state(quick.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "quick")).isEqualTo("quick");
            assertThat(host.searchScope(TunnelsScope.ID, "", started.window(), null)).containsExactly("tunnel." + quick.id() + "|localhost:" + port + "|true");
            // Forwarding revealed the Tunnels panel, a kit view: stopping updates it, so on the EDT.
            Kits.edt(() -> host.executeInScope(TunnelsScope.ID, "tunnel." + quick.id(), "toggle", started.window(), null));
            assertThat(manager.quickForwards()).as("stopping a quick forward forgets it").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void deletingAHostNamesTheTunnelsThatUseIt(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            started.plugin().tunnelStore().put(Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db", 5432));
            RemotePluginTest.settle(host);
            // The Sessions panel and the confirmation are kit views (Task 8a): drive them on the EDT.
            var text = dev.jasper.remote.testsupport.Kits.value(() -> {
                host.openPanel(RemotePlugin.PANEL);
                HostsPanel hosts = started.plugin().hostsPanel();
                int row = -1;
                for (int i = 0; i < UiTestAccess.rows(hosts).size(); i++)
                    if (UiTestAccess.rows(hosts).get(i) instanceof HostRows.Host entry && entry.host().id().equals(started.prod().id())) row = i;
                assertThat(UiTestAccess.menu(hosts, row)).contains("Delete…");
                UiTestAccess.menu(hosts, row, "Delete…");
                return started.plugin().currentConfirm().text();
            });
            assertThat(text)
                .isEqualTo("Delete the saved host prod? Open sessions stay connected. 1 tunnel uses this host (db); they stay, marked Host missing.");
        }
    }

    @SuppressWarnings("try") // first is closed early on purpose
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
            first.close(); // both servers echo, so only a closed first proves the traffic now reaches the second
            assertThat(EchoServer.roundTrip(port, "moved")).isEqualTo("moved");
            Files.writeString(store.file(), TunnelFile.format(List.of()));
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(10)));
            store.poll();
            await(host, () -> manager.state(db.id()).equals(TunnelState.STOPPED), "a removed tunnel to stop");
        }
    }

    /** Rewrites tunnels.toml as an outside editor would and has the store notice. */
    private static void rewrite(RemotePlugin plugin, List<Tunnel> tunnels, int secondsLater) throws Exception {
        var store = plugin.tunnelStore();
        Files.writeString(store.file(), TunnelFile.format(tunnels));
        Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(secondsLater)));
        store.poll();
    }

    @Test void aSavedQuickForwardStopsWhenItsEntryIsRemoved(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            var manager = tunnels.manager();
            assertThat(Kits.value(() -> host.invoke(TunnelsUi.FORWARD, started.window(), null))).isTrue();
            Kits.edt(() -> {
                var forward = tunnels.currentForward();
                TunnelsTestAccess.host(forward, started.prod());
                TunnelsTestAccess.targetHost(forward, "127.0.0.1");
                TunnelsTestAccess.remotePort(forward, String.valueOf(echo.port()));
                TunnelsTestAccess.forward(forward);
            });
            Tunnel quick = manager.quickForwards().getFirst();
            await(host, () -> manager.state(quick.id()) instanceof TunnelState.Up, "the quick forward to come up");
            int port = ((TunnelState.Up) manager.state(quick.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "quick")).isEqualTo("quick");
            // Save as Tunnel...: the panel's route for a quick forward; the editor keeps the id and the forwarding.
            Kits.edt(() -> {
                host.openPanel(TunnelsUi.PANEL);
                TunnelsTestAccess.activate(TunnelsTestAccess.panel(tunnels), quick.id());
                assertThat(host.windows()).as("Save as Tunnel").contains("dialog|Save as Tunnel|true");
                TunnelsTestAccess.name(tunnels.currentEditor(), "kept");
                TunnelsTestAccess.save(tunnels.currentEditor());
            });
            RemotePluginTest.settle(host);
            assertThat(started.plugin().tunnelStore().tunnel(quick.id())).as("saved under the same id").isPresent();
            assertThat(manager.quickForwards()).as("it left the quick list").isEmpty();
            assertThat(manager.state(quick.id())).as("and keeps running").isInstanceOf(TunnelState.Up.class);
            Kits.edt(() -> rewrite(started.plugin(), List.of(), 5));
            await(host, () -> manager.state(quick.id()).equals(TunnelState.STOPPED), "the removed tunnel to stop");
            assertThat(manager.active()).isZero();
            assertThatThrownBy(() -> new Socket(InetAddress.ofLiteral("127.0.0.1"), port).close()).as("nothing listens any more").isInstanceOf(ConnectException.class);
            assertThat(host.status()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void aFailedTunnelRemovedFromTheFileReturnsToStopped(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1")); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var manager = started.plugin().tunnels().manager();
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", busy.getLocalPort(), "127.0.0.1", 9);
            started.plugin().tunnelStore().put(db);
            RemotePluginTest.settle(host);
            manager.start(db, null);
            assertThat(manager.state(db.id())).isEqualTo(new TunnelState.Failed("Port " + busy.getLocalPort() + " is already in use on this computer"));
            assertThat(manager.failed()).isEqualTo(1);
            assertThat(host.status()).as("the status item reports the failure").hasSize(1);
            rewrite(started.plugin(), List.of(), 5);
            await(host, () -> manager.state(db.id()).equals(TunnelState.STOPPED), "the removed tunnel's failure to clear");
            assertThat(manager.failed()).isZero();
            assertThat(host.status()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void deletingARunningTunnelStopsItAndRemovesItsEntry(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            var manager = tunnels.manager();
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
            started.plugin().tunnelStore().put(db);
            RemotePluginTest.settle(host);
            Kits.edt(() -> { host.openPanel(TunnelsUi.PANEL); manager.start(db, null); });
            var panel = Kits.value(() -> TunnelsTestAccess.panel(tunnels));
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "the tunnel to come up");
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            dev.jasper.remote.testsupport.Kits.edt(() -> {
                TunnelsTestAccess.delete(panel, db.id());
                dev.jasper.remote.testsupport.Kits.click(tunnels.currentConfirm().view(), dev.jasper.remote.ui.ConfirmPanel.CONFIRM);
            });
            RemotePluginTest.settle(host);
            assertThat(started.plugin().tunnelStore().tunnels()).isEmpty();
            assertThat(manager.state(db.id())).isEqualTo(TunnelState.STOPPED);
            assertThatThrownBy(() -> new Socket(InetAddress.ofLiteral("127.0.0.1"), port).close()).isInstanceOf(ConnectException.class);
            assertThat(host.windows()).as("the confirmation closed").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void deletingATunnelWhileTheFileIsBrokenLeavesItRunning(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            var manager = tunnels.manager();
            var store = started.plugin().tunnelStore();
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
            store.put(db);
            RemotePluginTest.settle(host);
            Kits.edt(() -> { host.openPanel(TunnelsUi.PANEL); manager.start(db, null); });
            var panel = Kits.value(() -> TunnelsTestAccess.panel(tunnels));
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "the tunnel to come up");
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            Files.writeString(store.file(), "[[tunnel]\nname = ");
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(5)));
            Kits.edt(store::poll);
            await(host, () -> store.error().isPresent(), "the broken file to be noticed");
            dev.jasper.remote.testsupport.Kits.edt(() -> {
                TunnelsTestAccess.delete(panel, db.id());
                dev.jasper.remote.testsupport.Kits.click(tunnels.currentConfirm().view(), dev.jasper.remote.ui.ConfirmPanel.CONFIRM);
            });
            RemotePluginTest.settle(host);
            assertThat(store.tunnels()).as("the removal was refused").containsExactly(db);
            assertThat(manager.state(db.id())).as("so the tunnel was not stopped").isInstanceOf(TunnelState.Up.class);
            assertThat(EchoServer.roundTrip(port, "still")).isEqualTo("still");
            assertThat(host.windows()).as("the confirmation closed").isEmpty();
        }
    }

    @Test void aRunningTunnelWithoutAnIdSurvivesSavingAnotherTunnel(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            var manager = tunnels.manager();
            var store = started.plugin().tunnelStore();
            Files.writeString(store.file(), TunnelFile.HEADER + "\n[[tunnel]]\nname = \"web\"\nhost = \"" + started.prod().id()
                + "\"\nbind_address = \"127.0.0.1\"\nbind_port = 0\ntarget_host = \"127.0.0.1\"\ntarget_port = " + echo.port() + "\n");
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(5)));
            store.poll();
            await(host, () -> store.tunnels().size() == 1, "the hand-written entry to load");
            Tunnel web = store.tunnels().getFirst();
            manager.start(web, null);
            await(host, () -> manager.state(web.id()) instanceof TunnelState.Up, "the tunnel to come up");
            int port = ((TunnelState.Up) manager.state(web.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "before")).isEqualTo("before");
            // An unrelated save through the New Tunnel dialog rewrites the whole file and re-reads it.
            assertThat(Kits.value(() -> host.invoke(TunnelsUi.NEW, started.window(), null))).isTrue();
            var editor = tunnels.currentEditor();
            Kits.edt(() -> {
                TunnelsTestAccess.name(editor, "other");
                TunnelsTestAccess.listenPort(editor, "0");
                TunnelsTestAccess.forwardPort(editor, "1");
                TunnelsTestAccess.save(editor);
            });
            await(host, () -> store.tunnels().size() == 2, "the unrelated save");
            assertThat(store.tunnels()).extracting(Tunnel::name).containsExactly("web", "other");
            assertThat(store.tunnels().getFirst().id()).as("the entry kept its identity").isEqualTo(web.id());
            assertThat(manager.state(web.id())).as("so the running tunnel was left alone").isInstanceOf(TunnelState.Up.class);
            assertThat(((TunnelState.Up) manager.state(web.id())).boundPort()).isEqualTo(port);
            assertThat(EchoServer.roundTrip(port, "after")).isEqualTo("after");
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void aSaveOverASkippedEntryIsRefusedAndTheEditorSaysWhy(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            var store = started.plugin().tunnelStore();
            String text = TunnelFile.HEADER + "\n[[tunnel]]\nname = \"bad\"\nhost = \"" + started.prod().id()
                + "\"\nbind_port = 70000\ntarget_host = \"x\"\ntarget_port = 1\n";
            Files.writeString(store.file(), text);
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(5)));
            store.poll();
            await(host, () -> !store.warnings().isEmpty(), "the skipped entry to be noticed");
            assertThat(Kits.value(() -> host.invoke(TunnelsUi.NEW, started.window(), null))).isTrue();
            var editor = tunnels.currentEditor();
            Kits.edt(() -> {
                TunnelsTestAccess.name(editor, "other");
                TunnelsTestAccess.listenPort(editor, "0");
                TunnelsTestAccess.forwardPort(editor, "1");
                TunnelsTestAccess.save(editor);
            });
            RemotePluginTest.settle(host);
            assertThat(Kits.value(() -> TunnelsTestAccess.message(editor))).startsWith("tunnels.toml has entries Remote cannot read; fix or remove them before saving: ")
                .contains("tunnel 1");
            assertThat(tunnels.currentEditor()).as("the editor stays open").isSameAs(editor);
            assertThat(Files.readString(store.file())).as("and the file is untouched").isEqualTo(text);
        }
    }
}
