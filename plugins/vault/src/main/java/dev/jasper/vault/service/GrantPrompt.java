package dev.jasper.vault.service;

import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.model.Grant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** "Allow SSH to use deploy@prod?" — one per (plugin, credential) pair, shared by every waiting fetch. */
public final class GrantPrompt {
    public enum Decision { ALLOW_ONCE, ALWAYS, DENY }

    private final Grant grant;
    private final String consumerName;
    private final CredentialDescriptor descriptor;
    private final WindowHandle owner;
    private final BiConsumer<GrantPrompt, Decision> onAnswer;
    private final List<CompletableFuture<Optional<Credential>>> waiters = new ArrayList<>();
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private boolean settled;

    GrantPrompt(Grant grant, String consumerName, CredentialDescriptor descriptor, WindowHandle owner, BiConsumer<GrantPrompt, Decision> onAnswer) {
        this.grant = grant; this.consumerName = consumerName; this.descriptor = descriptor; this.owner = owner; this.onAnswer = onAnswer;
    }

    public Grant grant() { return grant; }
    public String consumerName() { return consumerName; }
    public CredentialDescriptor descriptor() { return descriptor; }
    public WindowHandle owner() { return owner; }
    public boolean settled() { return settled; }
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    public void answer(Decision decision) { if (!settled) onAnswer.accept(this, decision); }
    /** Closing the dialog denies. */
    public void cancel() { answer(Decision.DENY); }

    void attach(CompletableFuture<Optional<Credential>> waiter) {
        waiters.add(waiter);
        waiter.whenComplete((ignored, failure) -> {
            if (!waiter.isCancelled()) return;
            waiters.remove(waiter);
            if (waiters.isEmpty() && !settled) onAnswer.accept(this, Decision.DENY);
        });
    }

    /** Completes every waiter with its own value and closes the dialog. */
    void settle(Supplier<Optional<Credential>> perWaiter) {
        settled = true;
        dismissListeners.forEach(Runnable::run);
        for (CompletableFuture<Optional<Credential>> waiter : List.copyOf(waiters)) if (!waiter.isDone()) waiter.complete(perWaiter.get());
        waiters.clear();
    }
}
