package dev.jasper.vault.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class VaultCompositionTest {
    @Test void detailCopySelectionRemovalLockAndCloseKeepSecretOwnershipBounded() throws Exception {
        edt(()->{ try (var host=new FakePluginHost()) {
            var context=host.start(new PluginInfo("dev.jasper.vault","Vault","0.5.0",Set.of()),Set.of(),Set.of(),started->{});
            var login=new Account(UUID.randomUUID(),"Login","user",new Auth.Password("password-draft".toCharArray()),Instant.EPOCH,Instant.EPOCH);
            var note=new Note(UUID.randomUUID(),"Note","line one\nUnicode: \u03bb".toCharArray(),Instant.EPOCH);
            Map<UUID,Object> values=Map.of(login.id(),login,note.id(),note);
            var panel=new ManagerPanel(new FakeKit("dev.jasper.vault"),id->Optional.ofNullable(values.get(id)),t->{},id->{},id->{},id->{},g->{},()->{},()->{},()->{},()->{});
            panel.copies(CopyCards.declare(context));
            var rows=List.of(new VaultManager.Row(login.id(),login.name(),login.username(),VaultManager.Type.LOGIN),new VaultManager.Row(note.id(),note.name(),"",VaultManager.Type.NOTE));
            var status=new VaultManagerDialog.Status("test","5 min","/synthetic/vault.jv");panel.refresh(rows,List.of(),status,true);panel.select(login.id());
            var view=panel.view();String password="detail.secret."+login.id(),text="detail.secret."+note.id();
            assertThat(secret(view,password)).isEqualTo("password-draft");press(view,"detail.username.copy");press(view,"detail.password.copy");
            panel.select(note.id());assertThatThrownBy(()->view.withSecret(password,c->{})).isInstanceOf(IllegalArgumentException.class);
            assertThat(secret(view,text)).contains("line one\nUnicode:");press(view,"detail.note.copy");
            assertThat(host.clipboard().copies()).as("secret copies").containsExactly("secret|dev.jasper.vault|user","secret|dev.jasper.vault|password-draft","secret|dev.jasper.vault|line one\nUnicode: \u03bb");
            assertThat(host.notifications().lines()).as("a card per copy, naming the entry, never the text").containsExactly(
                "dev.jasper.vault.copies|INFO|Copied the username for Login|Copied to Clipboard for 30 seconds",
                "dev.jasper.vault.copies|INFO|Copied the password for Login|Copied to Clipboard for 30 seconds",
                "dev.jasper.vault.copies|INFO|Copied the note for Note|Copied to Clipboard for 30 seconds");
            panel.refresh(List.of(rows.getFirst()),List.of(),status,true);assertThat(panel.selected()).isEmpty();assertThat(all(view)).noneMatch(n->n instanceof Nodes.Secret);
            panel.select(login.id());panel.refresh(rows,List.of(),status,false);assertThat(all(view)).noneMatch(n->n instanceof Nodes.Secret);
            panel.refresh(rows,List.of(),status,true);panel.select(login.id());panel.close();assertThat(closed(view)).isTrue();
            assertThat(login.auth()).isInstanceOfSatisfying(Auth.Password.class,p->assertThat(p.password()).isEqualTo("password-draft".toCharArray()));
            login.auth().zero();note.zero();
        } });
    }

    @Test void managerProjectsCategoriesMetadataAndSelectedSecretWithoutLeakingItIntoDescriptors() throws Exception {
        edt(() -> {
            var login = new Account(UUID.randomUUID(), "amp", "dustin", new Auth.Password("synthetic".toCharArray()), Instant.EPOCH, Instant.parse("2026-10-10T12:00:00Z"));
            var key = new SshKey(UUID.randomUUID(), "work", "ssh-ed25519", "SHA256:fixture", "Synthetic", Path.of("/synthetic/key"), Path.of("/synthetic/key.pub"), Instant.EPOCH);
            var note = new Note(UUID.randomUUID(), "Recovery", "fixture note".toCharArray(), Instant.EPOCH);
            Map<UUID,Object> values = Map.of(login.id(), login, key.id(), key, note.id(), note);
            var panel = new ManagerPanel(new FakeKit("dev.jasper.vault"), id -> Optional.ofNullable(values.get(id)), t -> {}, id -> {}, id -> {}, id -> {}, grant -> {}, () -> {}, () -> {}, () -> {}, () -> {});
            var rows = List.of(new VaultManager.Row(login.id(),login.name(),login.username(),VaultManager.Type.LOGIN), new VaultManager.Row(key.id(),key.name(),key.fingerprint(),VaultManager.Type.SSH_KEY), new VaultManager.Row(note.id(),note.name(),"",VaultManager.Type.NOTE));
            panel.refresh(rows,List.of(),new VaultManagerDialog.Status("test", "5 min", "/synthetic/vault.jv"),true);
            var view = panel.view();
            assertThat(node(view,ManagerPanel.ROOT,Nodes.Form.class).kind().name()).isEqualTo("WINDOW");
            assertThat(node(view,"columns",Nodes.Split.class).kind()).isEqualTo(Nodes.SplitKind.THREE_COLUMN);
            var categories = node(view,ManagerPanel.KIND,Nodes.ItemList.class);
            assertThat(categories.items()).hasSize(4);
            assertThat(categories.items().getFirst().leading()).hasValueSatisfying(n -> assertThat(((Nodes.Icon)n).name()).isEqualTo("menu"));
            assertThat(panel.visibleRows()).extracting(VaultManager.Row::type).containsExactly(VaultManager.Type.LOGIN, VaultManager.Type.SSH_KEY, VaultManager.Type.NOTE);
            assertThat(((Nodes.Text)categories.items().getFirst().trailing().getFirst()).text()).isEqualTo("3");
            assertThat(node(view,"row."+key.id()+".detail",Nodes.Text.class).text()).isEqualTo("ssh-ed25519 · Synthetic");
            panel.select(login.id());
            assertThat(node(view,"detail.modified",Nodes.Text.class).text()).contains("Oct 10, 2026");
            assertThat(view.spec().toString()).doesNotContain("synthetic\"").doesNotContain("Used by");
            assertThat(all(view)).anyMatch(n -> n instanceof Nodes.Secret secret && secret.kind() == Nodes.SecretKind.DETAIL);
            assertThat(button(view,ManagerPanel.OKAY).label()).isEqualTo("Done");
            emit(view,new UiEvent.Selected(ManagerPanel.KIND,Set.of(VaultManager.Type.SSH_KEY.name())));
            assertThat(panel.selected()).isEmpty();
            panel.select(key.id());
            assertThat(node(view,"detail.created",Nodes.Text.class).text()).contains(java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu",java.util.Locale.ENGLISH).withZone(java.time.ZoneId.systemDefault()).format(Instant.EPOCH));
            assertThat(all(view)).noneMatch(n -> n instanceof Nodes.Secret);
            var menu = node(panel.view(),ManagerPanel.ADD,Nodes.MenuButton.class);
            assertThat(menu.items().getFirst().icon()).isEqualTo("user");
            assertThat(menu.items().getLast().icon()).isEqualTo("sparkle");
            assertThat(menu.items()).filteredOn(i -> !i.separator()).allSatisfy(i -> assertThat(i.icon()).isNotBlank());
            panel.close();login.auth().zero();note.zero();
        });
    }
}
