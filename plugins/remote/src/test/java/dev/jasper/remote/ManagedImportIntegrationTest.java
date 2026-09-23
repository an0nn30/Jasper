package dev.jasper.remote;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.*;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.VaultPlugin;
import dev.jasper.vault.VaultTestAccess;
import dev.jasper.vault.keygen.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;

class ManagedImportIntegrationTest {
    static final PluginInfo VAULT = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE));
    static RemotePlugin remote(Path sshDir) { return new RemotePlugin(Runnable::run, ignored -> Optional.empty(), (delay, action) -> () -> {}, sshDir); }
    static void settle(FakePluginHost host) throws Exception {
        for (int i = 0; i < 6; i++) { host.runBackground(); host.flush(); SwingUtilities.invokeAndWait(() -> {}); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realImportSurvivesRestartAndSourceDeletion(boolean retro, @TempDir Path dir) throws Exception {
        var key = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "shared", "fixture", "key-passphrase".toCharArray());
        var sshDir = Files.createDirectories(dir.resolve("ssh")); var persistedRoot = dir.resolve("plugins");
        UUID savedId;
        try (var server = new LoopbackServer()) {
            server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(key.publicPath()).strip()).resolvePublicKey(null, null, null));
            Files.writeString(sshDir.resolve("config"), "Host repaired other\n HostName 127.0.0.1\n Port " + server.port() + "\n User deploy\n IdentityFile \"" + key.privatePath() + "\"\n");
            try (var host = new FakePluginHost(persistedRoot)) {
                host.setRetroIcons(retro);
                VaultPlugin vault = VaultTestAccess.plugin(); host.start(VAULT, Set.of(), Set.of(), vault);
                RemotePlugin remote = remote(sshDir); var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
                var old = RemoteHost.create("repaired", "old", 22, "old", Auth.AGENT, "Servers", Optional.empty()).withFavorite(true);
                savedId = old.id(); remote.store().put(old); settle(host);
                UUID window = host.addTerminalWindow(); host.activateTerminalWindow(window);
                host.invoke(RemotePlugin.IMPORT, window, null); settle(host);
                importCheck(remote.currentImport(), 0).setSelected(true); importButton(remote.currentImport()).doClick(); settle(host);
                assertThat(host.windows()).contains("dialog|Create Vault|true").doesNotContain("dialog|Import SSH keys|true");
                JComponent create = host.windowContent("dev.jasper.vault", "Create Vault").orElseThrow();
                fields(create, JPasswordField.class).forEach(f -> f.setText("test-password"));
                fields(create, JCheckBox.class).forEach(c -> c.setSelected(false)); button(create, "Create Vault").doClick(); settle(host);
                JComponent prompt = host.windowContent("dev.jasper.vault", "Import SSH keys").orElseThrow();
                fields(prompt, JPasswordField.class).getFirst().setText("wrong"); button(prompt, "Unlock key").doClick(); settle(host);
                assertThat(button(prompt, "Import and use").isEnabled()).isFalse();
                fields(prompt, JPasswordField.class).getFirst().setText("key-passphrase"); button(prompt, "Unlock key").doClick(); settle(host);
                button(prompt, "Import and use").doClick(); settle(host);
                assertThat(VaultTestAccess.managedCount(vault)).isEqualTo(1);
                assertThat(VaultTestAccess.grantedConsumers(vault)).containsExactly("dev.jasper.remote");
                assertThat(remote.store().hosts()).hasSize(2);
                var repaired = remote.store().host(savedId).orElseThrow();
                assertThat(repaired.group()).isEqualTo("Servers"); assertThat(repaired.favorite()).isTrue(); assertThat(repaired.auth()).isInstanceOf(Auth.VaultKeys.class);
                assertThat(remote.store().hosts()).extracting(RemoteHost::auth).containsOnly(repaired.auth());
                new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
                assertThat(host.failures()).isEmpty();
            }
            Files.delete(key.privatePath()); Files.delete(key.publicPath());
            try (var restarted = new FakePluginHost(persistedRoot)) {
                restarted.setRetroIcons(retro);
                var vault = VaultTestAccess.plugin(); restarted.start(VAULT, Set.of(), Set.of(), vault);
                var remote = remote(sshDir); var context = restarted.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote);
                settle(restarted); UUID window = restarted.addTerminalWindow(); restarted.activateTerminalWindow(window);
                restarted.invoke(VaultPlugin.OPEN, window, null);
                var unlock = restarted.windowContent("dev.jasper.vault", "Unlock Vault").orElseThrow();
                fields(unlock, JPasswordField.class).getFirst().setText("test-password"); button(unlock, "Unlock").doClick(); settle(restarted);
                var saved = remote.store().host(savedId).orElseThrow();
                remote.openHost(context.terminals().window(window).orElseThrow(), saved); settle(restarted);
                assertThat(restarted.terminalPanes()).hasSize(1);
                UUID pane = restarted.terminalPanes().getFirst();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                var output = new StringBuilder();
                while (!output.toString().startsWith("READY")) {
                    output.append(restarted.sessionOutput(pane));
                    if (System.nanoTime() >= deadline) throw new AssertionError("No loopback READY banner");
                    settle(restarted); Thread.sleep(10);
                }
                assertThat(output.toString()).startsWith("READY"); assertThat(restarted.failures()).isEmpty();
            }
        }
    }
    static <T extends Component> List<T> fields(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) { if (type.isInstance(child)) result.add(type.cast(child)); if (child instanceof Container nested) result.addAll(fields(nested, type)); }
        return result;
    }
    static JButton button(Container root, String text) { return fields(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
}
