package dev.jasper.vault.api;

import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The Credential Vault as another plugin sees it. Obtain it with {@code services().require(VaultApi.class)}
 * after declaring {@code requires = [{ id = "dev.jasper.vault", version = ">=0.1" }]}. Every method is called on
 * the UI thread and returns at once; every future completes on the UI thread. Cancelling a future withdraws
 * that request, and a prompt nobody waits for any more closes.
 */
public interface VaultApi {
    /** Every lock transition, including the lock at plugin stop. */
    Topic<LockState> LOCK_STATE_CHANGED = Topic.of("dev.jasper.vault.lock-state", LockState.class);

    /** Imports selected keys into encrypted storage after Vault-owned review and consent; empty on cancellation. */
    default CompletableFuture<Optional<SshKeyImportResult>> importSshKeys(WindowHandle owner,
            List<SshKeySource> sources, List<UUID> selectedCredentials) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("This Vault does not support managed key import"));
    }

    LockState lockState();

    /** Prompts for the master password over {@code owner} when locked; {@code true} once unlocked, {@code false} on cancel or without a vault. */
    CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner);

    /** Every account and key, without secrets; empty unless unlocked. Needs no grant. */
    List<CredentialDescriptor> credentials();

    /**
     * The secret for {@code id}: unlocks first when needed and asks the user to allow this plugin once or
     * always unless a grant exists. Empty when denied, cancelled, or the id is unknown. Close the result.
     */
    CompletableFuture<Optional<Credential>> credential(UUID id);

    /** Secret request whose unlock/grant prompts belong to the captured window. */
    default CompletableFuture<Optional<Credential>> credential(WindowHandle owner, UUID id) { return credential(id); }

    /** The user's choice from a picker over {@code owner}; the choice is a one-time grant for this plugin. */
    CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner);

    /** Anchored chooser. Older implementations retain their modal picker and map empty to Cancelled (no clear).
     * Current Vault validates attachment and owns the chooser and consent; callers cannot authorize by UUID. */
    default CompletableFuture<CredentialPickResult> pick(WindowHandle owner, CredentialPickRequest request) {
        var result=new CompletableFuture<CredentialPickResult>();
        var lifetime=request.anchor().onClosed(() -> result.cancel(false));
        if(result.isDone()){lifetime.close();return result;}
        CompletableFuture<Optional<CredentialDescriptor>> legacy;
        try{legacy=pick(owner);}catch(RuntimeException failure){lifetime.close();throw failure;}
        result.whenComplete((answer,failure)->{lifetime.close();if(result.isCancelled())legacy.cancel(false);});
        legacy.whenComplete((answer,failure)->{if(failure!=null)result.completeExceptionally(failure);else result.complete(answer.<CredentialPickResult>map(CredentialPickResult.Chosen::new).orElseGet(CredentialPickResult.Cancelled::new));});
        return result;
    }
}
