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
 void withChooser(Path dir,Chooser body)throws Exception{withChooser(dir,System::nanoTime,body);}
 /** As {@link #withChooser(Path,Chooser)}, the chooser timing a choice by {@code clock}, in nanoseconds. */
 void withChooser(Path dir,java.util.function.LongSupplier clock,Chooser body)throws Exception{
  dev.jasper.vault.testsupport.KitViews.edt(()->{
   try(var host=new dev.jasper.sdk.testing.FakePluginHost(dir.resolve("host"))){
    var context=host.start(VAULT,Set.of(),Set.of(),started->{});
    var manager=new dev.jasper.vault.ui.VaultManager(lock,Runnable::run,Runnable::run,()->{});
    service.anchoredPicker(prompt->new dev.jasper.vault.ui.AnchoredPicker(context,manager,prompt,clock).show(),anchor->{});
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

 // Browser plan 8's L20: a choice in the first 500 ms after the chooser shows is ignored, so a press meant for what was
 // under it, as a double-click's second press, chooses nothing; Cancel and the search never wait.
 static final long MS=1_000_000L;
 static dev.jasper.sdk.testing.FakeKitView chooser(dev.jasper.sdk.testing.FakePluginHost host){return host.popoverViews(VAULT.id()).getFirst();}
 @Test void aChoiceInTheFirst500MsIsIgnoredAndTheChooserStaysOpen(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();long[] now={7_000*MS};
  withChooser(dir,()->now[0],(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(PROD.toString()));
   assertThat(result).as("at 0 ms").isNotDone();
   now[0]+=499*MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(PROD.toString()));
   assertThat(result).as("at 499 ms").isNotDone();assertThat(host.popoverViews(VAULT.id())).as("still open").hasSize(1);
   now[0]+=MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(PROD.toString()));
   assertThat(result.join()).as("at 500 ms").isInstanceOfSatisfying(CredentialPickResult.Chosen.class,chosen->assertThat(chosen.credential().id()).isEqualTo(PROD));
   assertThat(host.popoverViews(VAULT.id())).isEmpty();
  });
 }
 @Test void noneAndNewCredentialWaitTooAndTheChoosersReturnStartsTheWaitAgain(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();long[] now={0};
  withChooser(dir,()->now[0],(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.NONE));
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.CREATE));
   assertThat(result).isNotDone();assertThat(host.windowView(VAULT.id(),"New Credential")).as("no editor").isEmpty();
   now[0]+=500*MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.CREATE));
   host.windowView(VAULT.id(),"New Credential").orElseThrow().emit(new dev.jasper.sdk.ui.UiEvent.Action("create.cancel"));
   assertThat(host.popoverViews(VAULT.id())).as("the chooser again").hasSize(1);
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(PROD.toString()));
   now[0]+=499*MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.NONE));
   assertThat(result).as("within 500 ms of its return").isNotDone();
   now[0]+=MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.NONE));
   assertThat(result.join()).isInstanceOf(CredentialPickResult.Cleared.class);
  });
 }
 // Browser plan 8's L36: a chooser opened with a starting search (a page's) activates no row until the user moves to one,
 // so Enter on what the user types chooses nothing; one without keeps its first row active.
 /** Adds a login a page's search ({@code example.com}) matches. */
 void addSite(UUID site){lock.vault().accounts().add(new dev.jasper.vault.model.Account(site,"https://example.com (Corp)","u",new dev.jasper.vault.model.Auth.Password("pw".toCharArray()),java.time.Instant.EPOCH,java.time.Instant.EPOCH));lock.save().join();}
 @Test void aStartingSearchActivatesNoRowUntilTheUserMovesToOne(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();UUID site=UUID.randomUUID();addSite(site);long[] now={0};
  withChooser(dir,()->now[0],(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true,"example.com"));
   assertThat(rows(host)).containsExactly(site.toString());
   assertThat(menu(host).activeId()).as("no row active, New Credential… included").isEmpty();
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Selected(dev.jasper.vault.ui.AnchoredPicker.MENU,Set.of(site.toString())));
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(site.toString()));
   assertThat(result).as("the 500 ms wait still holds").isNotDone();
   now[0]+=500*MS;
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Edited(dev.jasper.vault.ui.AnchoredPicker.MENU,"example.co"));
   assertThat(menu(host).activeId()).as("typing leaves no row active").isEmpty();
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(site.toString()));
   assertThat(result).as("a row the user has not moved to since typing").isNotDone();
   assertThat(host.popoverViews(VAULT.id())).as("still open").hasSize(1);
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Selected(dev.jasper.vault.ui.AnchoredPicker.MENU,Set.of(site.toString())));
   assertThat(menu(host).activeId()).as("the row the user moved to, in the menu").isEqualTo(site.toString());
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(site.toString()));
   assertThat(result.join()).isInstanceOfSatisfying(CredentialPickResult.Chosen.class,chosen->assertThat(chosen.credential().id()).isEqualTo(site));
  });
 }
 @Test void aStartingSearchMatchingNothingLeavesNewCredentialInactiveToo(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();long[] now={0};
  withChooser(dir,()->now[0],(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true,"nothing.example"));
   assertThat(rows(host)).isEmpty();assertThat(menu(host).activeId()).isEmpty();
   now[0]+=500*MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.CREATE));
   assertThat(host.windowView(VAULT.id(),"New Credential")).as("no editor").isEmpty();
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Edited(dev.jasper.vault.ui.AnchoredPicker.MENU,""));
   assertThat(rows(host)).as("cleared: None and every credential").containsExactly(dev.jasper.vault.ui.AnchoredPicker.NONE,PROD.toString(),KEY.toString());
   assertThat(menu(host).activeId()).as("None is not active either").isEmpty();
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Selected(dev.jasper.vault.ui.AnchoredPicker.MENU,Set.of(dev.jasper.vault.ui.AnchoredPicker.CREATE)));
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.CREATE));
   assertThat(host.windowView(VAULT.id(),"New Credential")).as("the footer the user moved to").isPresent();
   result.cancel(false);
  });
 }
 @Test void withoutAStartingSearchTheFirstRowIsActiveAsBefore(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();long[] now={0};
  withChooser(dir,()->now[0],(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
   assertThat(menu(host).activeId()).isEqualTo(dev.jasper.vault.ui.AnchoredPicker.NONE);
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Edited(dev.jasper.vault.ui.AnchoredPicker.MENU,"prod"));
   assertThat(menu(host).activeId()).as("the first match").isEqualTo(PROD.toString());
   now[0]+=500*MS;chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(PROD.toString()));
   assertThat(result.join()).as("Enter on the active row").isInstanceOfSatisfying(CredentialPickResult.Chosen.class,chosen->assertThat(chosen.credential().id()).isEqualTo(PROD));
  });
 }
 @Test void cancelAndTheSearchNeverWait(@TempDir Path dir)throws Exception{
  var api=service(dir).forConsumer(SSH);populate();long[] now={0};
  withChooser(dir,()->now[0],(host,anchor)->{
   var result=api.pick(window,new CredentialPickRequest(anchor,Optional.empty(),true));
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Edited(dev.jasper.vault.ui.AnchoredPicker.MENU,"prod"));
   assertThat(menu(host).query()).as("the search at 0 ms").isEqualTo("prod");
   chooser(host).emit(new dev.jasper.sdk.ui.UiEvent.Action(dev.jasper.vault.ui.AnchoredPicker.CANCEL));
   assertThat(result.join()).as("Cancel at 0 ms").isInstanceOf(CredentialPickResult.Cancelled.class);
   assertThat(host.popoverViews(VAULT.id())).isEmpty();
  });
 }
}
