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
}
