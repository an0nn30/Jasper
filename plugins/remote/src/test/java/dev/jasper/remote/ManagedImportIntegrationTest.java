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
import dev.jasper.remote.testsupport.Kits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;

class ManagedImportIntegrationTest {
    static final PluginInfo VAULT = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE));
    /** The plugin marshals completions to the EDT, where its kit views live; MINA threads never touch a view. */
    static RemotePlugin remote(Path sshDir) { return new TestRemotePlugin(Kits.UI, ignored -> Optional.empty(), (delay, action) -> () -> {}, sshDir); }

    /**
     * Vault's and Remote's surfaces are owned kit views (Plan 2 Tasks 7 and 8a), which the kit drives on the EDT. The body
     * stays on the test thread and hops to the EDT for each step; the loopback wait sleeps here, off the EDT, so the
     * connection's completions (queued to the EDT) run meanwhile and no worker/EDT hand-off can deadlock. The timeout
     * bounds a regression instead of hanging the build.
     */
    @Test @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void realImportSurvivesRestartAndSourceDeletion(@TempDir Path dir) throws Exception {
        assertThat(SwingUtilities.isEventDispatchThread()).as("the body runs off the EDT").isFalse();
        var key = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "shared", "fixture", "key-passphrase".toCharArray());
        var sshDir = Files.createDirectories(dir.resolve("ssh")); var persistedRoot = dir.resolve("plugins");
        UUID savedId;
        try (var server = new LoopbackServer()) {
            server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(key.publicPath()).strip()).resolvePublicKey(null, null, null));
            Files.writeString(sshDir.resolve("config"), "Host repaired other\n HostName 127.0.0.1\n Port " + server.port() + "\n User deploy\n IdentityFile \"" + key.privatePath() + "\"\n");
            FakePluginHost host = Kits.value(() -> new FakePluginHost(persistedRoot));
            try {
                VaultPlugin vault = VaultTestAccess.plugin(); RemotePlugin remote = remote(sshDir);
                var context = Kits.value(() -> { host.start(VAULT, Set.of(), Set.of(), vault); return host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote); });
                var old = RemoteHost.create("repaired", "old", 22, "old", Auth.AGENT, "Servers", Optional.empty()).withFavorite(true);
                savedId = old.id();
                Kits.edt(() -> remote.store().put(old)); Kits.settle(host);
                Kits.edt(() -> { UUID id = host.addTerminalWindow(); host.activateTerminalWindow(id); host.invoke(RemotePlugin.IMPORT, id, null); });
                Kits.settle(host);
                Kits.edt(() -> { importCheck(remote.currentImport(), 0, true); importButton(remote.currentImport()); });
                Kits.settle(host);
                Kits.edt(() -> {
                    assertThat(host.windows()).contains("dialog|Create Vault|true").doesNotContain("dialog|Import SSH keys|true");
                    var create = host.windowView("dev.jasper.vault", "Create Vault").orElseThrow();
                    create.setSecret("password", "test-password".toCharArray()); create.setSecret("confirm", "test-password".toCharArray());
                    create.emit(new dev.jasper.sdk.ui.UiEvent.Toggled("bind", false)); create.emit(new dev.jasper.sdk.ui.UiEvent.Action("submit"));
                });
                Kits.settle(host);
                Kits.edt(() -> {
                    var prompt = host.windowView("dev.jasper.vault", "Import SSH keys").orElseThrow();
                    prompt.setSecret("passphrase", "wrong".toCharArray()); prompt.emit(new dev.jasper.sdk.ui.UiEvent.Action("unlock-key"));
                });
                Kits.settle(host);
                Kits.edt(() -> {
                    var prompt = host.windowView("dev.jasper.vault", "Import SSH keys").orElseThrow();
                    assertThat(((dev.jasper.sdk.ui.Nodes.Form) prompt.spec().root()).actions().stream()
                        .filter(b -> b.id().equals("commit")).findFirst().orElseThrow().state().enabled()).isFalse();
                    prompt.setSecret("passphrase", "key-passphrase".toCharArray()); prompt.emit(new dev.jasper.sdk.ui.UiEvent.Action("unlock-key"));
                });
                Kits.settle(host);
                Kits.edt(() -> host.windowView("dev.jasper.vault", "Import SSH keys").orElseThrow().emit(new dev.jasper.sdk.ui.UiEvent.Action("commit")));
                Kits.settle(host);
                UUID saved = savedId;
                Kits.edt(() -> {
                    assertThat(VaultTestAccess.managedCount(vault)).isEqualTo(1);
                    assertThat(VaultTestAccess.grantedConsumers(vault)).containsExactly("dev.jasper.remote");
                    assertThat(remote.store().hosts()).hasSize(2);
                    var repaired = remote.store().host(saved).orElseThrow();
                    assertThat(repaired.group()).isEqualTo("Servers"); assertThat(repaired.favorite()).isTrue(); assertThat(repaired.auth()).isInstanceOf(Auth.VaultKeys.class);
                    assertThat(remote.store().hosts()).extracting(RemoteHost::auth).containsOnly(repaired.auth());
                    assertThat(host.failures()).isEmpty();
                });
                new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            } finally { Kits.edt(host::close); }
            Files.delete(key.privatePath()); Files.delete(key.publicPath());
            FakePluginHost restarted = Kits.value(() -> new FakePluginHost(persistedRoot));
            try {
                var vault = VaultTestAccess.plugin(); var remote = remote(sshDir);
                var context = Kits.value(() -> { restarted.start(VAULT, Set.of(), Set.of(), vault); return restarted.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), remote); });
                Kits.settle(restarted);
                UUID window = Kits.value(() -> { UUID id = restarted.addTerminalWindow(); restarted.activateTerminalWindow(id); restarted.invoke(VaultPlugin.OPEN, id, null); return id; });
                Kits.edt(() -> {
                    var unlock = restarted.windowView("dev.jasper.vault", "Unlock Vault").orElseThrow();
                    unlock.setSecret("password", "test-password".toCharArray()); unlock.emit(new dev.jasper.sdk.ui.UiEvent.Action("submit"));
                });
                Kits.settle(restarted);
                UUID saved = savedId;
                Kits.edt(() -> remote.openHost(context.terminals().window(window).orElseThrow(), remote.store().host(saved).orElseThrow()));
                Kits.settle(restarted);
                assertThat(Kits.value(() -> restarted.terminalPanes())).hasSize(1);
                UUID pane = Kits.value(() -> restarted.terminalPanes().getFirst());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                var output = new StringBuilder();
                while (!output.toString().startsWith("READY")) {
                    output.append(Kits.value(() -> restarted.sessionOutput(pane)));
                    if (System.nanoTime() >= deadline) throw new AssertionError("No loopback READY banner");
                    Kits.settle(restarted); Thread.sleep(10);
                }
                assertThat(output.toString()).startsWith("READY");
                assertThat(Kits.value(() -> restarted.failures())).isEmpty();
            } finally { Kits.edt(restarted::close); }
        }
    }
    static <T extends Component> List<T> fields(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) { if (type.isInstance(child)) result.add(type.cast(child)); if (child instanceof Container nested) result.addAll(fields(nested, type)); }
        return result;
    }
    static JButton button(Container root, String text) { return fields(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
}
