package dev.jasper.vault.service;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
/** Vault-owned, single settlement consent. Fresh membership is checked at the moment of choice. */
public final class AnchoredPickPrompt {
 private final WindowHandle owner;
 private final CredentialPickRequest request;
 /** Reserve one terminal outcome before granting, without running completion callbacks under a lock. */
 static final class PickFuture extends CompletableFuture<CredentialPickResult> {
  private final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
  boolean chosen(CredentialPickResult.Chosen value,Runnable grant){
   if(!claimed.compareAndSet(false,true))return false;
   grant.run(); // visible before completion invokes a reentrant first fetch
   return super.complete(value);
  }
  @Override public boolean cancel(boolean interrupt){return claimed.compareAndSet(false,true)?super.cancel(interrupt):isCancelled();}
  @Override public boolean complete(CredentialPickResult value){return claimed.compareAndSet(false,true)&&super.complete(value);}
  @Override public boolean completeExceptionally(Throwable failure){return claimed.compareAndSet(false,true)&&super.completeExceptionally(failure);}
 }
 private final PickFuture result;
 private final Supplier<List<CredentialDescriptor>> choices;
 private final Consumer<UUID> grant;
 private final List<Runnable> dismiss=new ArrayList<>();
 private boolean settled;
 AnchoredPickPrompt(WindowHandle owner,CredentialPickRequest request,PickFuture result,
       Supplier<List<CredentialDescriptor>> choices,Consumer<UUID> grant,Executor ui){
  this.owner=owner;this.request=request;this.result=result;this.choices=choices;this.grant=grant;
  result.whenComplete((value,failure)->{if(result.isCancelled())ui.execute(this::cancel);});
 }
 public WindowHandle owner(){return owner;}
 public CredentialPickRequest request(){return request;}
 public List<CredentialDescriptor> choices(){return settled?List.of():choices.get();}
 public boolean settled(){return settled||result.isDone();}
 public void onDismiss(Runnable listener){if(settled)listener.run();else dismiss.add(listener);}
 public void choose(UUID id){
  if(settled()||!request.anchor().isOpen())return;
  var chosen=choices.get().stream().filter(d->d.id().equals(id)).findFirst();if(chosen.isEmpty())return;
  if(result.chosen(new CredentialPickResult.Chosen(chosen.orElseThrow()),()->{settled=true;grant.accept(id);}))dismiss();
 }
 public void clear(){if(!settled()&&request.allowClear()&&request.anchor().isOpen())finish(new CredentialPickResult.Cleared());}
 public void cancel(){if(!settled)finish(new CredentialPickResult.Cancelled());}
 private void finish(CredentialPickResult value){settled=true;result.complete(value);dismiss();}
 private void dismiss(){for(var listener:List.copyOf(dismiss))listener.run();dismiss.clear();}
}
