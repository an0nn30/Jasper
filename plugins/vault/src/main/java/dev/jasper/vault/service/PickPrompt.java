package dev.jasper.vault.service;

import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.CredentialDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** The account picker for one {@code pick} call. */
public final class PickPrompt {
    private final WindowHandle owner;
    private final List<CredentialDescriptor> choices;
    private final CompletableFuture<Optional<CredentialDescriptor>> waiter;
    private final Consumer<UUID> onChosen;
    private final List<Runnable> dismissListeners = new ArrayList<>();
    private boolean settled;

    PickPrompt(WindowHandle owner, List<CredentialDescriptor> choices, CompletableFuture<Optional<CredentialDescriptor>> waiter, Consumer<UUID> onChosen) {
        this.owner = owner; this.choices = List.copyOf(choices); this.waiter = waiter; this.onChosen = onChosen;
        waiter.whenComplete((ignored, failure) -> { if (waiter.isCancelled()) dismiss(); });
    }

    public WindowHandle owner() { return owner; }
    public List<CredentialDescriptor> choices() { return choices; }
    public boolean settled() { return settled; }
    public void onDismiss(Runnable listener) { dismissListeners.add(listener); }

    public void choose(UUID id) {
        if (settled) return;
        Optional<CredentialDescriptor> chosen = choices.stream().filter(choice -> choice.id().equals(id)).findFirst();
        chosen.ifPresent(choice -> onChosen.accept(choice.id()));
        dismiss();
        waiter.complete(chosen);
    }

    public void cancel() { if (settled) return; dismiss(); waiter.complete(Optional.empty()); }

    void dismiss() { if (settled) return; settled = true; dismissListeners.forEach(Runnable::run); }
}
