package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerAddresses;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ServerEditorTest {
    static final UUID ACCOUNT = UUID.randomUUID();
    @TempDir Path folder;
    final List<ServerDefinition> saved = new ArrayList<>();
    boolean cancelled;

    ServerEditor editor(Optional<ServerDefinition> initial, boolean existing, boolean vaultPresent, boolean linux, List<ServerDefinition> others) throws Exception {
        var en0 = new ServerAddresses.Nic("en0", true, false, false, List.of(InetAddress.getByName("192.168.1.20")));
        return new ServerEditor(others, initial, existing, Set.of(Protocol.HTTP), List.of(en0), vaultPresent,
            id -> id.equals(ACCOUNT) ? Optional.of("Share login") : Optional.empty(),
            () -> CompletableFuture.completedFuture(Optional.of(new ServerEditor.Account(ACCOUNT, "Share login"))),
            () -> Optional.of(folder), linux, saved::add, () -> cancelled = true);
    }

    @Test void createsAnHttpServerWithSafeDefaults() throws Exception {
        var editor = editor(Optional.empty(), false, true, false, List.of());
        assertThat(editor.save.getText()).isEqualTo("Add Server");
        assertThat(editor.protocol.getItemCount()).isEqualTo(1);
        assertThat(editor.port.getText()).isEqualTo("8080");
        assertThat(editor.anonymous.isSelected()).isTrue();
        assertThat(editor.reachable.getSelectedItem()).hasToString("This computer only");
        assertThat(editor.reachable.getItemCount()).as("loopback, all, en0").isEqualTo(3);
        assertThat(editor.reachable.getItemAt(2)).hasToString("en0 (192.168.1.20)");
        assertThat(editor.writable.isSelected()).isFalse();
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("needs a name");
        editor.name.setText("docs");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("Choose a folder to share");
        editor.folder.setText(folder.resolve("missing").toString());
        editor.save.doClick();
        assertThat(editor.message.getText()).endsWith("does not exist");
        editor.chooseFolder.doClick();
        assertThat(editor.folder.getText()).isEqualTo(folder.toString());
        editor.port.setText("http");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("1 to 65535");
        editor.port.setText("8081");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("docs");
            assertThat(d.protocol()).isEqualTo(Protocol.HTTP);
            assertThat(d.folder()).isEqualTo(folder.toAbsolutePath().normalize());
            assertThat(d.bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(d.port()).isEqualTo(8081);
            assertThat(d.writable()).isFalse();
            assertThat(d.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(d.autostart()).isFalse();
        });
        editor.cancel.doClick();
        assertThat(cancelled).isTrue();
    }

    @Test void aVaultLoginNeedsAnAccountAndRiskyChoicesWarn() throws Exception {
        var editor = editor(Optional.empty(), false, true, false, List.of());
        editor.name.setText("vaulted");
        editor.folder.setText(folder.toString());
        editor.vault.doClick();
        assertThat(editor.warnings.getText()).contains("Plain HTTP sends the password unencrypted");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("Choose a Vault account");
        editor.chooseAccount.doClick();
        assertThat(editor.accountLabel.getText()).isEqualTo("Share login");
        editor.writable.doClick();
        editor.reachable.setSelectedIndex(1);
        assertThat(editor.warnings.getText()).contains("Writable and reachable from other computers");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(d -> {
            assertThat(d.login()).isEqualTo(new Login.Vault(ACCOUNT));
            assertThat(d.writable()).isTrue();
            assertThat(d.bind()).isEqualTo(Bind.ALL);
        });
    }

    @Test void withoutTheVaultASessionPasswordIsOffered() throws Exception {
        var editor = editor(Optional.empty(), false, false, false, List.of());
        assertThat(editor.vault.isEnabled()).isFalse();
        assertThat(editor.vault.getText()).contains("install or enable the Credential Vault");
        assertThat(editor.chooseAccount.isEnabled()).isFalse();
        editor.name.setText("guarded");
        editor.folder.setText(folder.toString());
        editor.session.doClick();
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(d -> assertThat(d.login()).isEqualTo(Login.SESSION));
    }

    @Test void editingKeepsTheIdAndHiddenSettingsAndRefusesTakenNames() throws Exception {
        var other = ServerDefinition.create("taken", Protocol.HTTP, folder);
        var original = new ServerDefinition(UUID.randomUUID(), "docs", Protocol.HTTP, folder, new Bind.Interface("en9"), 8443,
            Optional.empty(), true, new Login.Vault(ACCOUNT), List.of(), true, false, true);
        var editor = editor(Optional.of(original), true, true, false, List.of(other, original));
        assertThat(editor.save.getText()).isEqualTo("Save");
        assertThat(editor.protocol.isEnabled()).isFalse();
        assertThat(editor.reachable.getSelectedItem()).hasToString("en9 (not available)");
        assertThat(editor.accountLabel.getText()).isEqualTo("Share login");
        assertThat(editor.autostart.isSelected()).isTrue();
        editor.name.setText("TAKEN");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A server named taken exists");
        editor.name.setText("docs");
        editor.save.doClick();
        assertThat(saved).singleElement().isEqualTo(original);
    }

    @Test void onLinuxAPrivilegedPortGetsAHint() throws Exception {
        var linux = editor(Optional.empty(), false, true, true, List.of());
        linux.port.setText("80");
        assertThat(linux.portHint.getText()).isEqualTo("Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try 8080");
        linux.port.setText("8080");
        assertThat(linux.portHint.getText().isBlank()).isTrue();
        var mac = editor(Optional.empty(), false, true, false, List.of());
        mac.port.setText("80");
        assertThat(mac.portHint.getText().isBlank()).isTrue();
    }

    ServerEditor ftpEditor(Optional<ServerDefinition> initial, boolean existing) {
        return new ServerEditor(List.of(), initial, existing, Set.of(Protocol.HTTP, Protocol.FTP), List.of(), false,
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> Optional.of(folder), false,
            saved::add, () -> cancelled = true);
    }

    @Test void ftpOffersTlsAndAPassiveRange() {
        var editor = ftpEditor(Optional.empty(), false);
        assertThat(editor.protocol.getSelectedItem()).isEqualTo(Protocol.HTTP);
        assertThat(editor.useTls.isEnabled()).as("HTTP can use TLS").isTrue();
        assertThat(editor.requireTls.isEnabled()).as("only FTP requires TLS").isFalse();
        assertThat(editor.passive.isEnabled()).as("only FTP has passive ports").isFalse();
        editor.protocol.setSelectedItem(Protocol.FTP);
        assertThat(editor.port.getText()).isEqualTo("2121");
        assertThat(editor.session.isSelected()).as("no Vault: a session password").isTrue();
        assertThat(editor.passive.isEnabled()).isTrue();
        assertThat(editor.warnings.getText()).isEqualTo("\u26a0 Plain FTP sends the password unencrypted");
        assertThat(editor.requireTls.isEnabled()).as("needs Use TLS").isFalse();
        editor.useTls.doClick();
        assertThat(editor.requireTls.isEnabled()).isTrue();
        assertThat(editor.warnings.getText()).isEqualTo(" ");
        editor.requireTls.doClick();
        editor.name.setText("drop");
        editor.folder.setText(folder.toString());
        editor.passive.setText("60000-50000");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A port range runs from a lower to a higher port, 1 to 65535");
        assertThat(saved).isEmpty();
        editor.passive.setText(" 51000-51010 ");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(definition -> {
            assertThat(definition.protocol()).isEqualTo(Protocol.FTP);
            assertThat(definition.tls()).isTrue();
            assertThat(definition.requireTls()).isTrue();
            assertThat(definition.passiveRange()).contains(new PortRange(51000, 51010));
            assertThat(definition.login()).isEqualTo(Login.SESSION);
        });
        editor.useTls.doClick();
        assertThat(editor.requireTls.isSelected()).as("Require TLS goes with Use TLS").isFalse();
        assertThat(editor.requireTls.isEnabled()).isFalse();
    }

    @Test void anEmptyPassiveRangeUsesTheSetting() {
        var editor = ftpEditor(Optional.empty(), false);
        editor.protocol.setSelectedItem(Protocol.FTP);
        editor.name.setText("plain");
        editor.folder.setText(folder.toString());
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(definition -> {
            assertThat(definition.passiveRange()).isEmpty();
            assertThat(definition.tls()).isFalse();
        });
    }

    @Test void editingAnFtpServerKeepsItsTlsAndRange() {
        var original = new ServerDefinition(UUID.randomUUID(), "drop", Protocol.FTP, folder, Bind.LOOPBACK, 2121,
            Optional.of(new PortRange(52000, 52005)), true, Login.SESSION, List.of(), true, true, false);
        var editor = ftpEditor(Optional.of(original), true);
        assertThat(editor.passive.getText()).isEqualTo("52000-52005");
        assertThat(editor.useTls.isSelected()).isTrue();
        assertThat(editor.requireTls.isSelected()).isTrue();
        assertThat(editor.requireTls.isEnabled()).isTrue();
        editor.save.doClick();
        assertThat(saved).singleElement().isEqualTo(original);
    }
}
