package dev.jasper.remote;

import dev.jasper.remote.testsupport.Kits;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.*;
import dev.jasper.vault.VaultPlugin;
import dev.jasper.vault.VaultTestAccess;
import dev.jasper.vault.api.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.*;
import static org.assertj.core.api.Assertions.*;

/** Real producer and compact consumer, no SSH connections, key files or real home access. */
class AnchoredImportIntegrationTest {
 @Test void realCreationSelectionClearAndSeparateDurableReview(@TempDir Path dir)throws Exception{
  var syntheticKey=new dev.jasper.vault.keygen.KeyGenerator(dir.resolve("synthetic-keys")).generate(dev.jasper.vault.keygen.KeyAlgorithm.ED25519,"fixture path key","fixture");
  var ssh=Files.createDirectories(dir.resolve("ssh"));Files.writeString(ssh.resolve("config"),"Host picker-demo\n HostName picker-demo.invalid\n User fixture\nHost picker-second\n HostName picker-second.invalid\n User fixture\n");
  var host=Kits.value(()->new FakePluginHost(dir.resolve("plugins")));
  try{
   var vault=VaultTestAccess.plugin();var remote=ManagedImportIntegrationTest.remote(ssh);
   var context=Kits.value(()->{host.start(ManagedImportIntegrationTest.VAULT,Set.of(),Set.of(),vault);return host.start(RemotePluginTest.INFO,Set.of(),Set.of("dev.jasper.vault"),remote);});
   var window=Kits.value(()->{var id=host.addTerminalWindow();host.activateTerminalWindow(id);host.invoke(VaultPlugin.OPEN,id,null);return id;});
   Kits.edt(()->{var create=host.windowView("dev.jasper.vault","Create Vault").orElseThrow();create.setSecret("password","fixture-password".toCharArray());create.setSecret("confirm","fixture-password".toCharArray());create.emit(new UiEvent.Toggled("bind",false));create.emit(new UiEvent.Action("submit"));});Kits.settle(host);
   Kits.edt(()->{host.invoke(RemotePlugin.IMPORT,window,null);});Kits.settle(host);
   Kits.edt(()->{
    chooseCredential(remote.currentImport(),0);var menu=host.popoverViews("dev.jasper.vault").getFirst();
    assertThat(menu.spec().root()).isInstanceOf(Nodes.PickerMenu.class);menu.emit(new UiEvent.Edited("credential.menu","fixture"));
    assertThat(((Nodes.PickerMenu)menu.spec().root()).items()).isEmpty();menu.emit(new UiEvent.Action("credential.create"));
    assertThat(host.popoverViews("dev.jasper.vault")).isEmpty();host.windowView("dev.jasper.vault","New Credential").orElseThrow().emit(new UiEvent.Action("create.cancel"));
    assertThat(((Nodes.PickerMenu)host.popoverViews("dev.jasper.vault").getFirst().spec().root()).query()).isEqualTo("fixture");
    host.popoverViews("dev.jasper.vault").getFirst().emit(new UiEvent.Action("credential.create"));host.windowView("dev.jasper.vault","New Credential").orElseThrow().emit(new UiEvent.Action("create.login"));
    var editor=host.windowView("dev.jasper.vault","Add Login").orElseThrow();editor.emit(new UiEvent.Edited("name","fixture login"));editor.emit(new UiEvent.Edited("username","fixture"));editor.setSecret("password","synthetic-secret".toCharArray());editor.emit(new UiEvent.Action("save"));
   });Kits.settle(host);
   var api=Kits.value(()->context.services().require(VaultApi.class));
   Kits.edt(()->{
    var menu=host.popoverViews("dev.jasper.vault").getFirst();menu.emit(new UiEvent.Action("credential.create"));host.windowView("dev.jasper.vault","New Credential").orElseThrow().emit(new UiEvent.Action("create.key"));
    host.windowView("dev.jasper.vault","Add SSH Key").orElseThrow().emit(new UiEvent.Action("cancel"));
    assertThat(((Nodes.PickerMenu)host.popoverViews("dev.jasper.vault").getFirst().spec().root()).query()).isEqualTo("fixture");
    host.popoverViews("dev.jasper.vault").getFirst().emit(new UiEvent.Action("credential.create"));host.windowView("dev.jasper.vault","New Credential").orElseThrow().emit(new UiEvent.Action("create.key"));
    var key=host.windowView("dev.jasper.vault","Add SSH Key").orElseThrow();key.emit(new UiEvent.Edited("name","fixture path key"));key.emit(new UiEvent.Edited("private-path",syntheticKey.privatePath().toString()));key.emit(new UiEvent.Edited("public-path",syntheticKey.publicPath().toString()));key.emit(new UiEvent.Action("save"));
   });Kits.settle(host);
   Kits.edt(()->{assertThat(api.credentials()).hasSize(2);assertThat(VaultTestAccess.grantedConsumers(vault)).isEmpty();
    assertThat(Kits.node(remote.currentImport().view(),"import.choose.0",Nodes.Choice.class).face().orElseThrow().primary()).isEmpty();
   });
   var credential=Kits.value(()->{
    var menu=host.popoverViews("dev.jasper.vault").getFirst();assertThat(((Nodes.PickerMenu)menu.spec().root()).query()).isEqualTo("fixture");
    assertThat(VaultTestAccess.grantedConsumers(vault)).isEmpty();var d=api.credentials().getFirst();
    menu.emit(new UiEvent.Action(d.id().toString()));assertThat(host.popoverViews("dev.jasper.vault")).isEmpty();
    assertThat(Kits.node(remote.currentImport().view(),"import.choose.0",Nodes.Choice.class).face().orElseThrow().primary()).isEqualTo("fixture login");return d;
   });
   var first=Kits.value(()->api.credential(credential.id()));Kits.settle(host);try(var secret=first.join().orElseThrow()){assertThat(secret.username()).contains("fixture");}
   var second=Kits.value(()->api.credential(credential.id()));Kits.settle(host);assertThat(second).isNotDone();Kits.edt(()->second.cancel(false));
   // A real consumer may fetch synchronously from the Chosen completion; cancellation cannot steal it.
   var immediate=Kits.value(()->{
    var picked=api.pick(context.terminals().window(window).orElseThrow(),new CredentialPickRequest(remote.currentImport().view().anchor("import.choose.0"),Optional.empty(),true));
    var fetched=picked.thenCompose(choice->api.credential(((CredentialPickResult.Chosen)choice).credential().id()));
    host.popoverViews("dev.jasper.vault").getFirst().emit(new UiEvent.Action(credential.id().toString()));
    assertThat(picked.cancel(false)).isFalse();return fetched;
   });Kits.settle(host);try(var secret=immediate.join().orElseThrow()){assertThat(secret.username()).contains("fixture");}
   Kits.edt(()->{
    chooseCredential(remote.currentImport(),0);host.popoverViews("dev.jasper.vault").getFirst().emit(new UiEvent.Action("credential.none"));
    assertThat(Kits.node(remote.currentImport().view(),"import.choose.0",Nodes.Choice.class).face().orElseThrow().primary()).isEmpty();
    chooseCredential(remote.currentImport(),0);host.popoverViews("dev.jasper.vault").getFirst().emit(new UiEvent.Action(credential.id().toString()));
    importCheck(remote.currentImport(),1,false);importButton(remote.currentImport());
   });Kits.settle(host);
   Kits.edt(()->{
    var review=host.windowView("dev.jasper.vault","Import SSH keys").orElseThrow();
    assertThat(VaultTestAccess.grantedConsumers(vault)).isEmpty();assertThat(remote.store().hosts()).isEmpty();
    assertThat(Kits.node(remote.currentImport().view(),"import.choose.0",Nodes.Choice.class).state().enabled()).isFalse();
    review.emit(new UiEvent.Action("commit"));
   });Kits.settle(host);
   Kits.edt(()->{assertThat(remote.store().hosts()).hasSize(1);assertThat(VaultTestAccess.grantedConsumers(vault)).containsExactly("dev.jasper.remote");assertThat(Kits.node(remote.currentImport().view(),"import.choose.0",Nodes.Choice.class).face().orElseThrow().primary()).isEqualTo("fixture login");assertThat(host.failures()).isEmpty();});
   // Reopen the real Import route locked; invalidate its child anchor while another unlock waiter survives.
   Kits.edt(()->{cancelImport(remote.currentImport());host.windowView("dev.jasper.vault","Credential Vault").ifPresent(manager->manager.emit(new UiEvent.Action(((Nodes.Form)manager.spec().root()).cancelAction())));host.invoke(VaultPlugin.LOCK,window,null);host.invoke(RemotePlugin.IMPORT,window,null);});Kits.settle(host);
   var survivor=Kits.value(()->{chooseCredential(remote.currentImport(),0);var old=host.windowView("dev.jasper.vault","Unlock Vault").orElseThrow();
    old.setSecret("password","discard-me".toCharArray());var waiting=api.ensureUnlocked(context.terminals().window(window).orElseThrow());
    refreshImport(remote.currentImport());assertThat(waiting).isNotDone();assertThatThrownBy(old::spec).isInstanceOf(IllegalStateException.class);return waiting;
   });Kits.settle(host);
   Kits.edt(()->{var unlock=host.windowView("dev.jasper.vault","Unlock Vault").orElseThrow();unlock.withSecret("password",chars->assertThat(chars).isEmpty());unlock.setSecret("password","fixture-password".toCharArray());unlock.emit(new UiEvent.Action("submit"));});Kits.settle(host);
   assertThat(survivor.join()).isTrue();Kits.edt(()->{assertThat(host.popoverViews("dev.jasper.vault")).isEmpty();assertThat(host.failures()).isEmpty();});
  }finally{Kits.edt(host::close);}
 }
}
