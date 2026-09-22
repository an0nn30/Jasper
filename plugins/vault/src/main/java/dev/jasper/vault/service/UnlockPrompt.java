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
    private final WindowHandle owner;
    private final LockManager lock;
    private final Runnable onSettled;
    private final List<CompletableFuture<Boolean>> waiters = new ArrayList<>();
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private Consumer<String> errorListener = message -> { };
    private String error;
    private boolean busy, settled;

    UnlockPrompt(WindowHandle owner, LockManager lock, Runnable onSettled) { this.owner = owner; this.lock = lock; this.onSettled = onSettled; }

    public WindowHandle owner() { return owner; }
    public boolean busy() { return busy; }
    public boolean settled() { return settled; }
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** The dialog shows what the last attempt said. */
    public void onError(Consumer<String> listener) { errorListener = listener; }
    /** The dialog closes itself here. */
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    CompletableFuture<Boolean> attach() {
        var waiter = new CompletableFuture<Boolean>();
        waiters.add(waiter);
        waiter.whenComplete((ignored, failure) -> { if (waiter.isCancelled()) detach(waiter); });
        return waiter;
    }

    private void detach(CompletableFuture<Boolean> waiter) {
        waiters.remove(waiter);
        if (waiters.isEmpty() && !settled && !busy) finish(false);
    }

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
    public void cancel() { if (!settled) finish(false); }

    private void finish(boolean unlocked) {
        settled = true;
        onSettled.run();
        dismissListeners.forEach(Runnable::run);
        for (CompletableFuture<Boolean> waiter : List.copyOf(waiters)) waiter.complete(unlocked);
        waiters.clear();
    }
}
