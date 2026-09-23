package dev.jasper.remote;

import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.RemoteScope;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;

class RemotePluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.1.0",
        Set.of(Capabilities.TERMINAL_OPEN, Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));

    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new RemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    static void settle(FakePluginHost host) { for (int i = 0; i < 20; i++) { host.runBackground(); host.flush(); } }

    @Test void registersItsSurfaceAndConnectsThroughTheScope(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.remote.connect|Connect to SSH Host...|true", "dev.jasper.remote.hosts|SSH Hosts|true",
                "dev.jasper.remote.split|Split with Same Host|false", "dev.jasper.remote.import|Import from ~/.ssh/config...|true");
            assertThat(host.panels()).containsExactly("dev.jasper.remote.panel|SSH hosts|LEFT");
            assertThat(host.scopes()).containsExactly("dev.jasper.remote.scope|SSH|connect,split,edit");
            assertThat(host.menu("top:dev.jasper.remote.menu")).isNotEmpty();
            assertThat(host.status()).as("hidden at zero").isEmpty();
            assertThat(vault.consumers).containsExactly("dev.jasper.remote");

            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "Production", Optional.empty());
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            List<String> rows = host.searchScope(RemoteScope.ID, "", window, null);
            assertThat(rows).containsExactly("host." + prod.id() + "|prod|true");
            host.executeInScope(RemoteScope.ID, "host." + prod.id(), "connect", window, null);
            assertThat(host.openRequests()).containsExactly("session-tab|" + window + "|prod");
            UUID pane = host.terminalPanes().getFirst();
            settle(host);
            assertThat(host.sessionState(pane)).isEqualTo("RUNNING|");
            assertThat(host.status()).containsExactly("dev.jasper.remote.status|RIGHT|1 SSH session||dev.jasper.remote.hosts");
            Thread.sleep(300);
            assertThat(host.sessionOutput(pane)).startsWith("READY xterm-256color 80x24");
            assertThat(host.failures()).isEmpty();
            host.focusTerminalPane(pane);
            host.flush();
            assertThat(host.actions()).contains("dev.jasper.remote.split|Split with Same Host|true");
            assertThat(host.invoke(RemotePlugin.SPLIT, window, pane)).isTrue();
            settle(host);
            assertThat(host.openRequests()).hasSize(2).last().asString().startsWith("session-split");
            assertThat(host.terminalPanes()).hasSize(2);
            assertThat(host.status().getFirst()).contains("2 SSH sessions");
            host.closeTerminalPane(pane);
            host.flush();
            assertThat(host.status().getFirst()).contains("1 SSH session");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void hostKeyPromptIsAWindowModalDialog(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            host.addTerminalWindow();
            CompletableFuture<HostKeyVerifier.Decision> decision = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
            assertThat(host.windows()).containsExactly("dialog|Verify host key h|true");
            trust(plugin.currentHostKeyPanel()).doClick();
            assertThat(decision).isCompletedWithValue(HostKeyVerifier.Decision.TRUST);
            assertThat(host.windows()).isEmpty();
            CompletableFuture<HostKeyVerifier.Decision> closed = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
            host.requestClose("dialog");
            assertThat(closed).isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
        }
    }

    @Test void thePanelAddsEditsDeletesAndImports(@TempDir Path dir) throws Exception {
        Path sshDir = Files.createDirectories(dir.resolve("ssh"));
        Files.writeString(sshDir.resolve("config"), "Host imported\n  HostName imported.example\n  User me\n");
        try (var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(sshDir);
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            assertThat(panel).isNotNull();
            add(panel).doClick();
            assertThat(host.windows()).containsExactly("dialog|Add Host|true");
            name(plugin.currentEditor()).setText("new"); hostname(plugin.currentEditor()).setText("new.example"); agentAuth(plugin.currentEditor()).doClick();
            username(plugin.currentEditor()).setText("root");
            save(plugin.currentEditor()).doClick();
            settle(host);
            assertThat(host.windows()).isEmpty();
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
            assertThat(list(panel).getModel().getSize()).as("Other group + host").isEqualTo(2);
            panel.select(plugin.store().hosts().getFirst().id());
            assertThat(cardCredential(panel).getText()).isEqualTo("SSH agent");
            assertThat(host.invoke(RemotePlugin.IMPORT, window, null)).isTrue();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Import from ~/.ssh/config|true");
            importButton(plugin.currentImport()).doClick();
            settle(host);
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new", "imported");
            RemoteHost imported = plugin.store().hosts().get(1);
            javax.swing.JPopupMenu menu = menuFor(panel, indexOf(panel, imported));
            ((javax.swing.JMenuItem) menu.getComponent(4)).doClick();
            assertThat(host.windows()).containsExactly("dialog|Delete imported?|true");
            plugin.currentConfirm().confirm.doClick();
            settle(host);
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
            toggle(panel, 0);
            settle(host);
            assertThat(Files.readString(context.dataDirectory().resolve("panel-state.toml"))).contains("Other");
        }
    }

    static int indexOf(HostsPanel panel, RemoteHost host) {
        for (int i = 0; i < list(panel).getModel().getSize(); i++)
            if (list(panel).getModel().getElementAt(i) instanceof dev.jasper.remote.ui.HostRows.Host row && row.host().id().equals(host.id())) return i;
        throw new AssertionError("host row not shown");
    }
    @Test void cancellationClosesTheHostKeyDialog(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of(), plugin);
            host.addTerminalWindow();
            var pending = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-rsa", "SHA256:x"));
            pending.cancel(true);
            assertThat(host.windows()).isEmpty();
        }
    }

    @Test void hostsActionCreatesThePanelWithoutOpeningThePalette(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.invoke(RemotePlugin.HOSTS, window, null);
            assertThat(host.paletteOpens()).isEmpty();
            assertThat(host.openPanel(RemotePlugin.PANEL, window)).isNotNull();
        }
    }

    @Test void failedMutationsReportAfterTheEditorCloses(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            var plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            var saved = RemoteHost.create("saved", "example", 22, "me", Auth.AGENT, "", Optional.empty());
            plugin.store().put(saved); settle(host);
            Files.writeString(context.dataDirectory().resolve("hosts.toml"), "broken = [");
            var menu = menuFor(panel, indexOf(panel, saved));
            ((javax.swing.JMenuItem) menu.getComponent(3)).doClick();
            ((javax.swing.JMenuItem) menu.getComponent(5)).doClick();
            add(panel).doClick();
            name(plugin.currentEditor()).setText("new"); hostname(plugin.currentEditor()).setText("example");
            agentAuth(plugin.currentEditor()).doClick(); username(plugin.currentEditor()).setText("me");
            save(plugin.currentEditor()).doClick();
            host.requestClose("dialog");
            settle(host);
            assertThat(host.notices()).hasSize(3);
            assertThat(host.failures()).isEmpty();
        }
    }

}
