package dev.jasper.vault.service;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.Subscription;
import dev.jasper.vault.api.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class AnchoredPickTest extends VaultServiceTest {
 static final class Anchor implements UiAnchor {boolean open=true;final List<Runnable> listeners=new ArrayList<>();public boolean isOpen(){return open;}public Subscription onClosed(Runnable r){listeners.add(r);return ()->listeners.remove(r);}public void close(){if(!open)return;open=false;List.copyOf(listeners).forEach(Runnable::run);}}
 @Test void workerCancellationBetweenLivenessAndMembershipCannotGrant(@TempDir Path dir){
  var api=service(dir).forConsumer(SSH);populate();var shown=new ArrayList<AnchoredPickPrompt>();service.anchoredPicker(shown::add,a->{});
  var base=new Anchor();Runnable[] checkpoint={()->{}};
  var anchor=new UiAnchor(){public boolean isOpen(){checkpoint[0].run();return base.isOpen();}public Subscription onClosed(Runnable r){return base.onClosed(r);}public void close(){base.close();}};
  var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
  checkpoint[0]=()->{checkpoint[0]=()->{};java.util.concurrent.CompletableFuture.runAsync(()->result.cancel(false)).join();};
  shown.getLast().choose(PROD);assertThat(result).isCancelled();
  var next=api.credential(PROD);assertThat(next).as("cancelled selection leaves no grant").isNotDone();grants.getLast().cancel();
 }
 @Test void chosenCompletionMayImmediatelyFetchItsOneUseGrant(@TempDir Path dir){
  var api=service(dir).forConsumer(SSH);populate();var shown=new ArrayList<AnchoredPickPrompt>();service.anchoredPicker(shown::add,a->{});
  var result=api.pick(window,new CredentialPickRequest(new Anchor(),Optional.empty(),true));
  var fetched=result.thenCompose(choice->{
   assertThat(java.util.concurrent.CompletableFuture.supplyAsync(()->result.cancel(false)).orTimeout(5,java.util.concurrent.TimeUnit.SECONDS).join()).isFalse();
   return api.credential(((CredentialPickResult.Chosen)choice).credential().id());
  });
  shown.getLast().choose(PROD);try(var credential=fetched.join().orElseThrow()){assertThat(credential.username()).contains("deploy");}
  assertThat(grants).isEmpty();var second=api.credential(PROD);assertThat(second).isNotDone();grants.getLast().cancel();
 }
 @Test void explicitSelectionGrantsExactlyOneUseAndClearDoesNotGrant(@TempDir Path dir){
  var api=service(dir).forConsumer(SSH);populate();var shown=new ArrayList<AnchoredPickPrompt>();service.anchoredPicker(shown::add,a->{});
  var result=api.pick(window,new CredentialPickRequest(new Anchor(),Optional.empty(),true));
  shown.getLast().choose(PROD);var unrelated=service.forConsumer(OTHER).credential(PROD);assertThat(unrelated).isNotDone();grants.getLast().cancel();grants.clear();assertThat(result.join()).isInstanceOf(CredentialPickResult.Chosen.class);
  try(var value=api.credential(PROD).join().orElseThrow()){assertThat(value.username()).contains("deploy");}
  var second=api.credential(PROD);assertThat(second).isNotDone();assertThat(grants).hasSize(1);grants.getLast().cancel();
  var cleared=api.pick(window,new CredentialPickRequest(new Anchor(),Optional.of(PROD),true));shown.getLast().clear();assertThat(cleared.join()).isInstanceOf(CredentialPickResult.Cleared.class);
  var next=api.credential(PROD);assertThat(next).isNotDone();grants.getLast().cancel();
 }
 @Test void losingAnchoredUnlockWithdrawsOnlyItsWaiterAndReparentsTheSurvivor(@TempDir Path dir){
  var api=service(dir).forConsumer(SSH);populate();lock.lock();service.anchoredPicker(p->{throw new AssertionError("cancelled picker reopened");},a->{});
  var anchor=new Anchor();var picked=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
  var otherOwner=window();var other=service.forConsumer(OTHER).ensureUnlocked(otherOwner);var prompt=unlocks.getFirst();
  anchor.close();assertThat(picked.join()).isInstanceOf(CredentialPickResult.Cancelled.class);assertThat(other).isNotDone();
  assertThat(unlocks).hasSize(2);assertThat(unlocks.getLast()).isSameAs(prompt);assertThat(prompt.presentationOwner()).isSameAs(otherOwner);
  prompt.submit("pw".toCharArray());assertThat(other.join()).isTrue();
 }
 @Test void joiningAnExistingUnlockDoesNotMoveItAndLastWaiterCancellationSettles(@TempDir Path dir){
  var api=service(dir).forConsumer(SSH);populate();lock.lock();service.anchoredPicker(p->{},a->{});
  var original=service.forConsumer(OTHER).ensureUnlocked(window);var prompt=unlocks.getFirst();var anchor=new Anchor();
  var picked=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));anchor.close();
  assertThat(picked.join()).isInstanceOf(CredentialPickResult.Cancelled.class);assertThat(unlocks).containsExactly(prompt);assertThat(prompt.presentationOwner()).isSameAs(window);assertThat(original).isNotDone();
  original.cancel(false);assertThat(prompt.settled()).isTrue();
 }
 @Test void pendingUnlockAttemptSurvivesReparentingAndCompletesOnlyLiveWaiters(@TempDir Path dir){
  var work=new ArrayList<Runnable>();boolean[] queued={false};lockBackground=task->{if(queued[0])work.add(task);else task.run();};
  var api=service(dir).forConsumer(SSH);populate();lock.lock();service.anchoredPicker(p->{throw new AssertionError("stale picker reopened");},a->{});
  var anchor=new Anchor();var picked=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));var other=service.forConsumer(OTHER).ensureUnlocked(window());var prompt=unlocks.getFirst();
  queued[0]=true;prompt.submit("pw".toCharArray());assertThat(prompt.busy()).isTrue();anchor.close();assertThat(prompt.busy()).isTrue();assertThat(other).isNotDone();
  work.removeFirst().run();assertThat(other.join()).isTrue();assertThat(picked.join()).isInstanceOf(CredentialPickResult.Cancelled.class);assertThat(prompt.settled()).isTrue();
 }
 @Test void sourceLossAndDeletedMembershipPreventAnyLateGrant(@TempDir Path dir){
  var api=service(dir).forConsumer(SSH);populate();var shown=new ArrayList<AnchoredPickPrompt>();service.anchoredPicker(shown::add,a->{});
  var anchor=new Anchor();var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));var prompt=shown.getLast();anchor.close();prompt.choose(PROD);
  assertThat(result.join()).isInstanceOf(CredentialPickResult.Cancelled.class);
  var pending=api.pick(window,new CredentialPickRequest(new Anchor(),Optional.empty(),true));lock.vault().accounts().clear();shown.getLast().choose(PROD);
  assertThat(pending).isNotDone();shown.getLast().cancel();assertThat(pending.join()).isInstanceOf(CredentialPickResult.Cancelled.class);
 }

 // Browser plan 8 Task 2: the chooser's starting search.
 static final dev.jasper.sdk.PluginInfo VAULT=new dev.jasper.sdk.PluginInfo("dev.jasper.vault","Credential Vault","0.9.0",Set.of());
 /** The real chooser on a host-issued anchor: a button in a tab of a fake host whose data lives under {@code dir}. */
 interface Chooser{void run(dev.jasper.sdk.testing.FakePluginHost host,UiAnchor anchor)throws Exception;}
 void withChooser(Path dir,Chooser body)throws Exception{
  dev.jasper.vault.testsupport.KitViews.edt(()->{
   try(var host=new dev.jasper.sdk.testing.FakePluginHost(dir.resolve("host"))){
    var context=host.start(VAULT,Set.of(),Set.of(),started->{});
    var manager=new dev.jasper.vault.ui.VaultManager(lock,Runnable::run,Runnable::run,()->{});
    service.anchoredPicker(prompt->new dev.jasper.vault.ui.AnchoredPicker(context,manager,prompt).show(),anchor->{});
    var view=context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Button("key",dev.jasper.sdk.ui.ControlState.READY,"Key","",dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST,"key","")),event->{});
    context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("dev.jasper.vault.page","Page"),null,view);
    body.run(host,view.anchor("key"));
   }
  });
 }
 static dev.jasper.sdk.ui.Nodes.PickerMenu menu(dev.jasper.sdk.testing.FakePluginHost host){return (dev.jasper.sdk.ui.Nodes.PickerMenu)host.popoverViews(VAULT.id()).getFirst().spec().root();}
 static List<String> rows(dev.jasper.sdk.testing.FakePluginHost host){return menu(host).items().stream().map(dev.jasper.sdk.ui.Nodes.PickerItem::id).toList();}
 @Test void aRequestsSearchStartsTheChooserFiltered(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();UUID site=UUID.randomUUID();
  lock.vault().accounts().add(new dev.jasper.vault.model.Account(site,"https://example.com (Corp)","u",new dev.jasper.vault.model.Auth.Password("pw".toCharArray()),java.time.Instant.EPOCH,java.time.Instant.EPOCH));lock.save().join();
  assertThat(new CredentialPickRequest(new Anchor(),Optional.empty(),true," example.com\n").search()).as("stripped").isEqualTo("example.com");
  assertThatThrownBy(()->new CredentialPickRequest(new Anchor(),Optional.empty(),true,null)).isInstanceOf(NullPointerException.class);
  withChooser(dir,(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true,"example.com"));
   assertThat(menu(host).query()).isEqualTo("example.com");
   assertThat(rows(host)).as("only the matching login, and no None row").containsExactly(site.toString());
   host.popoverViews(VAULT.id()).getFirst().emit(new dev.jasper.sdk.ui.UiEvent.Edited(dev.jasper.vault.ui.AnchoredPicker.MENU,""));
   assertThat(rows(host)).as("cleared: None and every credential").containsExactly(dev.jasper.vault.ui.AnchoredPicker.NONE,PROD.toString(),site.toString(),KEY.toString());
   result.cancel(false);assertThat(host.popoverViews(VAULT.id())).isEmpty();
  });
 }
 @Test void theThreeArgumentRequestStartsWithAnEmptySearch(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();
  var fake=new Anchor();
  assertThat(new CredentialPickRequest(fake,Optional.empty(),true)).isEqualTo(new CredentialPickRequest(fake,Optional.empty(),true,""));
  withChooser(dir,(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
   assertThat(menu(host).query()).isEmpty();
   assertThat(rows(host)).containsExactly(dev.jasper.vault.ui.AnchoredPicker.NONE,PROD.toString(),KEY.toString());
   result.cancel(false);
  });
 }
}
