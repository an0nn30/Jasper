package dev.jasper.vault.service;

import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.crypto.CorruptVaultException;
import dev.jasper.vault.crypto.WrongPasswordException;
import dev.jasper.vault.lock.ForeignDeviceException;
import dev.jasper.vault.lock.LockManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** The one master-password prompt; the dialog drives it and every requester waits on it. */
public final class UnlockPrompt {
    private WindowHandle owner;
    private dev.jasper.sdk.WindowOwner presentationOwner;
    private Consumer<UnlockPrompt> represent = prompt -> {};
    private final List<Runnable> retire = new ArrayList<>();
    private record Request(WindowHandle owner,dev.jasper.sdk.WindowOwner presentation){}
    private final java.util.Map<CompletableFuture<Boolean>,Request> requests=new java.util.LinkedHashMap<>();
    private final LockManager lock;
    private final Runnable onSettled;
    private final List<CompletableFuture<Boolean>> waiters = new ArrayList<>();
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private Consumer<String> errorListener = message -> { };
    private String error;
    private boolean busy, settled;

    UnlockPrompt(WindowHandle owner, LockManager lock, Runnable onSettled) { this(owner,owner,lock,onSettled); }
    UnlockPrompt(WindowHandle owner,dev.jasper.sdk.WindowOwner presentationOwner,LockManager lock,Runnable onSettled){this.owner=owner;this.presentationOwner=presentationOwner;this.lock=lock;this.onSettled=onSettled;}
    void representWith(Consumer<UnlockPrompt> show){represent=show;}
    /** Retires only this presentation, before replacement; the shared request stays unsettled. */
    public void onPresentationRetired(Runnable cleanup){retire.add(cleanup);}
    public dev.jasper.sdk.WindowOwner presentationOwner(){return presentationOwner;}
    public boolean anchored(){return presentationOwner instanceof dev.jasper.sdk.ui.UiAnchor;}

    public WindowHandle owner() { return owner; }
    public boolean busy() { return busy; }
    public boolean settled() { return settled; }
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** The dialog shows what the last attempt said. */
    public void onError(Consumer<String> listener) { errorListener = listener; }
    /** The dialog closes itself here. */
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    CompletableFuture<Boolean> attach() {return attach(owner,presentationOwner);}
    CompletableFuture<Boolean> attach(WindowHandle requestOwner,dev.jasper.sdk.WindowOwner presentation) {
        var waiter = new CompletableFuture<Boolean>();
        waiters.add(waiter);requests.put(waiter,new Request(requestOwner,presentation));
        waiter.whenComplete((ignored, failure) -> { if (waiter.isCancelled()) detach(waiter); });
        return waiter;
    }

    private void detach(CompletableFuture<Boolean> waiter) {
        waiters.remove(waiter);requests.remove(waiter);
        if(settled)return;
        if(waiters.isEmpty()){cancel();return;}
        if(!live(owner,presentationOwner)){
            var survivor=List.copyOf(requests.values()).stream().filter(r->live(r.owner(),r.presentation())).findFirst();
            if(settled||live(owner,presentationOwner))return;
            if(survivor.isEmpty()){cancel();return;}
            owner=survivor.get().owner();presentationOwner=survivor.get().presentation();
            errorListener=message->{};
            var old=List.copyOf(retire);retire.clear();old.forEach(Runnable::run);
            if(!settled)represent.accept(this);
        }
    }

    private static boolean live(WindowHandle owner,dev.jasper.sdk.WindowOwner presentation){return (owner==null||owner.isOpen())&&(!(presentation instanceof dev.jasper.sdk.ui.UiAnchor anchor)||anchor.isOpen());}

    /** Tries {@code password} (zeroed by the lock manager); on success every waiter gets {@code true}. */
    public void submit(char[] password) {
        if (busy || settled) { java.util.Arrays.fill(password, (char) 0); return; }
        busy = true; error = null;
        lock.unlock(password).whenComplete((ignored, failure) -> {
            busy = false;
            if (settled) return;
            if (failure == null) { finish(true); return; }
            Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null ? wrapped.getCause() : failure;
            error = switch (cause) {
                case WrongPasswordException wrong -> "Wrong password";
                case ForeignDeviceException foreign -> foreign.getMessage();
                case CorruptVaultException corrupt -> corrupt.getMessage();
                default -> "Could not open the vault: " + cause.getMessage();
            };
            errorListener.accept(error);
            if (waiters.isEmpty()) finish(false);   // every requester left while the attempt ran
        });
    }

    /** The user closed the dialog: every waiter gets {@code false}. */
    public void cancel() {
        if (settled) return;
        if (busy) lock.lock();
        finish(false);
    }

    private void finish(boolean unlocked) {
        settled = true;
        onSettled.run();
        dismissListeners.forEach(Runnable::run);
        for (CompletableFuture<Boolean> waiter : List.copyOf(waiters)) waiter.complete(unlocked);
        waiters.clear();requests.clear();retire.clear();
    }
}
