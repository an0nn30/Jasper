package dev.jasper.remote;

import static dev.jasper.remote.RemotePluginTest.settle;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.testing.FakePresentations;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 14.4, end to end through RemotePlugin under Enterprise: the main window's toolbar view, the Sessions tree and its
 * Properties show one row (the window's choice, else its active SSH pane's host, else the first), whichever of them
 * changed it. The Sessions panel is the main window's (workspace tabs spec 14), so another window's row leaves it alone.
 */
class ClassicSessionsWiringTest {
    static final Set<String> OPTIONAL = Set.of("dev.jasper.vault", "dev.jasper.editor");
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final RemoteHost minecraft = RemoteHost.create("minecraft-local", "192.168.1.124", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());

    static RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(Kits.UI, context -> Optional.empty(), (delay, task) -> () -> { }, sshDir);
    }

    static FakeKitView toolbar(FakePluginHost host, UUID window) throws Exception { return Kits.value(() -> host.toolbarViews(window).getFirst()); }
    static String combo(FakePluginHost host, UUID window) throws Exception {
        var view = toolbar(host, window);
        return Kits.value(() -> Kits.node(view, SessionsToolbar.CHOICE, Nodes.Choice.class).selectedId());
    }
    static String tree(RemotePlugin plugin) throws Exception {
        return Kits.value(() -> plugin.hostsPanel().selectedRow().map(HostsPanel::rowId).orElse("none"));
    }
    static Optional<String> properties(FakePluginHost host) throws Exception {
        return Kits.value(() -> Kits.find(host.panelView(RemotePlugin.PANEL).orElseThrow(), HostsPanel.PROPERTIES)
            .map(node -> ((Nodes.Properties) node).title()));
    }
    String row(RemoteHost host) { return "host:" + host.id(); }

    @Test void theTreeTheComboAndThePropertiesShareTheWindowsRow(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            host.setPresentation(FakePresentations.CLASSIC);
            var plugin = plugin(dir.resolve("ssh"));
            host.start(RemotePluginTest.INFO, Set.of(), OPTIONAL, plugin);
            settle(host);
            plugin.store().put(mac); plugin.store().put(minecraft);
            settle(host);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            Kits.edt(() -> host.openPanel(RemotePlugin.PANEL));
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(combo(host, window)).as("nothing chosen: the first host").isEqualTo(row(mac));
            assertThat(tree(plugin)).isEqualTo(row(mac));

            var panel = Kits.value(() -> host.panelView(RemotePlugin.PANEL).orElseThrow());
            Kits.edt(() -> Kits.emit(panel, new UiEvent.Selected(HostsPanel.ROWS, Set.of(row(minecraft)))));
            assertThat(combo(host, window)).as("the tree's choice reaches the combo").isEqualTo(row(minecraft));

            var view = toolbar(host, window);
            Kits.edt(() -> Kits.emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(row(mac)))));
            assertThat(tree(plugin)).as("the combo's choice reaches the tree").isEqualTo(row(mac));
            assertThat(properties(host)).contains("Properties — Mac");

            // Hidden and shown again, the tree keeps the window's row rather than the first host.
            Kits.edt(() -> Kits.emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(row(minecraft)))));
            for (int i = 0; i < 2; i++) assertThat(Kits.value(() -> host.invoke(RemotePlugin.HOSTS, window, null))).isTrue();
            settle(host);
            assertThat(tree(plugin)).as("shown again").isEqualTo(row(minecraft));
            assertThat(properties(host)).contains("Properties — minecraft-local");

            // A filter that hides the chosen row selects another, which the combo follows.
            Kits.edt(() -> Kits.type(panel, HostsPanel.SEARCH, "192.168.1.119"));
            assertThat(tree(plugin)).isEqualTo(row(mac));
            assertThat(combo(host, window)).as("filtered out").isEqualTo(row(mac));
            Kits.edt(() -> Kits.type(panel, HostsPanel.SEARCH, ""));

            // The chosen host deleted: the tree, its Properties and the combo fall back together.
            Kits.edt(() -> Kits.emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(row(minecraft)))));
            plugin.store().remove(minecraft.id());
            settle(host);
            assertThat(combo(host, window)).isEqualTo(row(mac));
            assertThat(tree(plugin)).isEqualTo(row(mac));
            assertThat(properties(host)).contains("Properties — Mac");

            plugin.store().remove(mac.id());
            settle(host);
            assertThat(combo(host, window)).isEqualTo(SessionsToolbar.NONE_OPTION);
            assertThat(tree(plugin)).isEqualTo("none");
            assertThat(properties(host)).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theComboAndTheTreeFollowTheActiveSshPaneWhileNothingIsChosen(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            host.setPresentation(FakePresentations.CLASSIC);
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin(dir.resolve("ssh"));
            var context = host.start(RemotePluginTest.INFO, Set.of(), OPTIONAL, plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost alpha = RemoteHost.create("alpha", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            RemoteHost beta = RemoteHost.create("beta", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(alpha); plugin.store().put(beta);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var handle = context.terminals().window(window).orElseThrow();
            Kits.edt(() -> host.openPanel(RemotePlugin.PANEL));
            settle(host);

            // Opened in its own tab, beta's pane becomes the active one: the combo shows beta once it attaches.
            Kits.edt(() -> plugin.openHost(handle, beta));
            await(host, () -> combo(host, window).equals(row(beta)), "the combo follows beta's new pane");
            Kits.edt(() -> plugin.openHost(handle, alpha));
            await(host, () -> combo(host, window).equals(row(alpha)), "the combo follows alpha's new pane");
            assertThat(tree(plugin)).isEqualTo(row(alpha));

            UUID betaPane = host.terminalPanes().getFirst();
            Kits.edt(() -> { host.focusTerminalPane(betaPane); host.flush(); });
            await(host, () -> combo(host, window).equals(row(beta)), "the combo follows the focused pane without a manual refresh");
            assertThat(tree(plugin)).as("the tree follows it too").isEqualTo(row(beta));
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void anotherWindowsChoiceLeavesTheMainWindowsTreeAlone(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            host.setPresentation(FakePresentations.CLASSIC);
            var plugin = plugin(dir.resolve("ssh"));
            host.start(RemotePluginTest.INFO, Set.of(), OPTIONAL, plugin);
            settle(host);
            plugin.store().put(mac); plugin.store().put(minecraft);
            settle(host);
            UUID main = host.addTerminalWindow(), child = host.addTerminalWindow();
            Kits.edt(() -> host.openPanel(RemotePlugin.PANEL));
            settle(host);
            assertThat(tree(plugin)).isEqualTo(row(mac));

            var view = toolbar(host, child);
            Kits.edt(() -> Kits.emit(view, new UiEvent.Selected(SessionsToolbar.CHOICE, Set.of(row(minecraft)))));
            settle(host);
            assertThat(combo(host, child)).as("the other window's own row").isEqualTo(row(minecraft));
            assertThat(tree(plugin)).as("the main window's tree keeps its row").isEqualTo(row(mac));
            assertThat(combo(host, main)).isEqualTo(row(mac));
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static void await(FakePluginHost host, Callable<Boolean> condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.call() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
        assertThat(condition.call()).as(description).isTrue();
    }
}
