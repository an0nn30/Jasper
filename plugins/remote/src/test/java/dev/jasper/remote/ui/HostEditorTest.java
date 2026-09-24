package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostEditorTest {
    final List<RemoteHost> saved = new ArrayList<>();
    final RemoteHost bastion = RemoteHost.create("bastion", "b.example", 22, "ops", Auth.AGENT, "Homelab", Optional.empty());
    final CredentialDescriptor login = new CredentialDescriptor(UUID.randomUUID(), "Work login", "deploy", Kind.ACCOUNT_PASSWORD);

    @Test void createsAHostWithAVaultCredentialAndAJump() {
        var editor = new HostEditor(List.of(bastion), Optional.empty(), true, id -> id.equals(login.id()) ? Optional.of(login.name()) : Optional.empty(),
            () -> CompletableFuture.completedFuture(Optional.of(login)), saved::add, () -> { });
        assertThat(editor.port.getText()).isEqualTo("22");
        assertThat(editor.vaultAuth.isSelected()).isTrue();
        assertThat(editor.jump.getItemCount()).as("none + bastion").isEqualTo(2);
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("name");
        editor.name.setText("prod"); editor.hostname.setText("api.example"); editor.port.setText("2222");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("credential");
        editor.choose.doClick();
        assertThat(editor.credentialLabel.getText()).isEqualTo("Work login");
        editor.group.setSelectedItem("Production");
        editor.jump.setSelectedIndex(1);
        editor.favorite.setSelected(true);
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(host -> {
            assertThat(host.name()).isEqualTo("prod");
            assertThat(host.port()).isEqualTo(2222);
            assertThat(host.auth()).isEqualTo(new Auth.Vault(login.id()));
            assertThat(host.username()).as("the login supplies it").isEmpty();
            assertThat(host.group()).isEqualTo("Production");
            assertThat(host.jump()).contains(bastion.id());
            assertThat(host.favorite()).isTrue();
        });
    }

    @Test void editsKeepTheIdAndAgentNeedsAUsername() {
        boolean[] cancelled = {false};
        var editor = new HostEditor(List.of(), Optional.of(bastion), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), saved::add, () -> cancelled[0] = true);
        assertThat(editor.name.getText()).isEqualTo("bastion");
        assertThat(editor.vaultAuth.isEnabled()).as("no Vault installed").isFalse();
        assertThat(editor.agentAuth.isSelected()).isTrue();
        assertThat(editor.group.getSelectedItem()).isEqualTo("Homelab");
        editor.username.setText("");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("username");
        editor.username.setText("root"); editor.port.setText("abc");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("port");
        editor.port.setText("22");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(host -> { assertThat(host.id()).isEqualTo(bastion.id()); assertThat(host.username()).isEqualTo("root"); assertThat(host.created()).isEqualTo(bastion.created()); });
        editor.cancel.doClick();
        assertThat(cancelled[0]).isTrue();
    }
    @Test void preservesAndReordersManagedKeys() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        RemoteHost host = bastion.withEdited("managed", "server", 22, "ops", new Auth.VaultKeys(List.of(a, b)), "", Optional.empty());
        var editor = new HostEditor(List.of(), Optional.of(host), true, id -> Optional.of(id.equals(a) ? "first" : "second"),
            () -> CompletableFuture.completedFuture(Optional.of(login)), saved::add, () -> {});
        editor.hostname.setText("changed"); editor.save.doClick();
        assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(a, b)));
        editor.keyList.setSelectedIndex(1); editor.moveUp.doClick(); editor.save.doClick();
        assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(b, a)));
        editor.addKey.doClick();
        assertThat(editor.message.getText()).contains("managed SSH key");
        editor.removeKey.doClick(); editor.save.doClick();
        assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(a)));
    }

    @Test void followDirectoryIsOnForNewHostsAndKeptWhenEditing() {
        var editor = new HostEditor(List.of(), Optional.empty(), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), saved::add, () -> { });
        assertThat(editor.followDirectory.isSelected()).isTrue();
        assertThat(editor.followDirectory.getText()).isEqualTo("Track shell folder for SFTP follow");
        editor.name.setText("n"); editor.hostname.setText("h"); editor.username.setText("u");
        editor.followDirectory.setSelected(false);
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(host -> assertThat(host.followDirectory()).isFalse());
        var edit = new HostEditor(List.of(), Optional.of(saved.getFirst()), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), saved::add, () -> { });
        assertThat(edit.followDirectory.isSelected()).isFalse();
        edit.followDirectory.setSelected(true);
        edit.save.doClick();
        assertThat(saved.getLast().followDirectory()).isTrue();
        assertThat(saved.getLast().id()).isEqualTo(saved.getFirst().id());
    }

}
