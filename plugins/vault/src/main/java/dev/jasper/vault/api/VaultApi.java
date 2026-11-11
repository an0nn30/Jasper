package dev.jasper.vault.api;

import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The Credential Vault as another plugin sees it. Obtain it with {@code services().find(VaultApi.class)} (or
 * {@code require}) after naming {@code dev.jasper.vault} in {@code requires}; each plugin gets its own instance, and
 * grants belong to that plugin. Every method is called on the UI thread and returns at once; every future completes on
 * the UI thread. Cancelling a future withdraws that request, and a prompt nobody waits for any more closes.
 */
public interface VaultApi {
    /**
     * The new state at every lock transition, including the lock when Vault stops; subscribe with
     * {@code context.events().subscribe}.
     */
    Topic<LockState> LOCK_STATE_CHANGED = Topic.of("dev.jasper.vault.lock-state", LockState.class);

    /**
     * Imports private key files into encrypted storage after the user reviews them in a dialog titled
     * "Import SSH keys". Vault opens first, creating a vault when there is none, and asks for each key's passphrase
     * when it needs one. On commit each key is stored in Vault (one already stored with the same fingerprint is
     * reused; a key file Vault refers to by path with the same fingerprint becomes a stored key with the same id and
     * the source's name) and this plugin gets a saved grant for every imported key and every selected credential. The
     * interface default fails with {@link UnsupportedOperationException}; Credential Vault implements it.
     *
     * @param owner the window the dialog and prompts open over
     * @param sources at most 4096 regular key files with distinct request ids, each at most 1 MiB holding one supported
     *     private key whose {@code .pub} file beside it, if present, matches
     * @param selectedCredentials existing credentials to allow as well: SSH keys stored in Vault or password accounts
     * @return what was stored and allowed; empty when the user cancels, or the owner closes or the vault locks before
     *     the save starts; cancelled when either happens during the save, which may still complete (Vault then posts
     *     "Host import was cancelled"). Fails with {@link IllegalArgumentException} when Vault cannot open, a key
     *     cannot be read or a selected credential is neither kind, and with {@link IllegalStateException} when saving
     *     fails
     * @throws IllegalArgumentException when there are more than 4096 sources or two share a request id
     */
    default CompletableFuture<Optional<SshKeyImportResult>> importSshKeys(WindowHandle owner,
            List<SshKeySource> sources, List<UUID> selectedCredentials) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("This Vault does not support managed key import"));
    }

    /**
     * Whether a vault exists and whether it is unlocked now. Needs no grant.
     *
     * @return the current state
     */
    LockState lockState();

    /**
     * Prompts for the master password over {@code owner} when the vault is locked. Every requester shares one unlock
     * prompt: a request made while the prompt is open joins it where it is, and while the Credential Vault manager is
     * open the prompt opens in it. Without a vault it posts a notification saying to create one and answers
     * {@code false}.
     *
     * @param owner the window the prompt opens over
     * @return {@code true} once unlocked; {@code false} when the user cancels or there is no vault
     */
    CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner);

    /**
     * Every account and SSH key, without secrets; secure notes are not listed. Needs no grant.
     *
     * @return the credentials; empty unless unlocked
     */
    List<CredentialDescriptor> credentials();

    /**
     * The secret for {@code id}: unlocks first when needed and asks the user to allow this plugin once or
     * always unless a grant exists. The prompts open over the window this plugin last passed to Vault while it is
     * open, else the active or first Jasper window, except where {@link #ensureUnlocked} says otherwise; a grant
     * prompt already open for this plugin and credential is joined. Locked with no window, Vault posts a notification
     * and answers empty. Close the result.
     *
     * @param id a credential's {@link CredentialDescriptor#id() id}
     * @return the secret; empty when denied, cancelled, the id is unknown or there is no vault (Vault then posts a
     *     notification saying to create one)
     */
    CompletableFuture<Optional<Credential>> credential(UUID id);

    /**
     * The secret for {@code id}, as {@link #credential(UUID)} fetches it, with its unlock and grant prompts over
     * {@code owner} except where {@link #ensureUnlocked} says otherwise. Empty at once when {@code owner} is null or
     * closed; a secret that arrives after {@code owner} closes is closed and the answer is empty. The interface default
     * ignores {@code owner}.
     *
     * @param owner the window the request comes from
     * @param id a credential's {@link CredentialDescriptor#id() id}
     * @return the secret; empty when denied, cancelled, the id is unknown, {@code owner} closed or there is no vault
     *     (Vault then posts a notification saying to create one)
     */
    default CompletableFuture<Optional<Credential>> credential(WindowHandle owner, UUID id) { return credential(id); }

    /**
     * Unlocks when needed, then lets the user choose a credential in a modal dialog titled "Choose a credential" over
     * {@code owner}. The choice is a one-time grant for this plugin.
     *
     * @param owner the window the dialog opens over
     * @return the chosen credential; empty when the user cancels, there is no vault or the vault locks
     */
    CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner);

    /**
     * Shows Vault's credential chooser in a popover on the request's anchor, after unlocking (the unlock prompt
     * attaches to the anchor too): a search field holding the request's search, a None row when the request allows
     * clearing and the search is empty, a row for each credential the search matches and New Credential…, which opens
     * Vault's own editor and then the chooser again without choosing or granting anything. Choosing a credential is a
     * one-time grant for this plugin; naming an id in the request grants nothing. The interface default shows
     * {@link #pick(WindowHandle)} instead, without the search, and answers Chosen or Cancelled.
     *
     * @param owner the window the request comes from
     * @param request the anchor, the current credential, whether None is offered and the starting search
     * @return the outcome
     * @throws IllegalArgumentException when the anchor is not a live anchor Jasper issued
     */
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

    /**
     * Saves a password login, unlocking over {@code owner} first when needed, and gives this plugin a saved grant for
     * it, all without a grant prompt. When this plugin's saved grant is the only grant on a password login named
     * exactly {@code name}, the last such login in the vault takes the new username and password instead, keeping its
     * id, grants and created time; a login of that name another plugin can read, or this plugin cannot, is left alone
     * and a new one is added. Vault keeps its own copy of {@code password}, so the caller still zeroes its array. The
     * interface default fails with {@link UnsupportedOperationException}; Credential Vault implements it.
     *
     * @param owner the window the unlock prompt opens over
     * @param name the login's name in Vault
     * @param username the login's username, which may be empty
     * @param password the login's password, which may be empty
     * @return the saved login; empty when {@code owner} is null or closes first, unlocking is cancelled, the vault locks
     *     before the save finishes (a save the lock interrupts while it writes may still be in the vault when it next
     *     unlocks) or there is no vault (Vault then posts a notification saying to create one). Fails with
     *     {@link IllegalStateException} ("Could not save the login") when the vault cannot be written
     * @throws IllegalArgumentException when {@code name} is null or blank, or {@code username} or {@code password} is
     *     null
     * @throws IllegalStateException when Credential Vault is called off the UI thread
     */
    default CompletableFuture<Optional<CredentialDescriptor>> saveLogin(WindowHandle owner, String name, String username,
            char[] password) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("This Vault does not save logins"));
    }

    /**
     * The secret for {@code id} without any prompt, only while the vault is unlocked and this plugin holds a saved grant
     * for {@code id}. A one-time grant from a pick is neither used nor spent. Close the result. The interface default
     * answers empty.
     *
     * @param id a credential's {@link CredentialDescriptor#id() id}
     * @return the secret; empty when the vault is locked or there is none, the id is unknown, or this plugin has no
     *     saved grant for it
     * @throws IllegalStateException when Credential Vault is called off the UI thread
     */
    default Optional<Credential> credentialIfGranted(UUID id) { return Optional.empty(); }

    /**
     * Opens the Credential Vault manager over {@code owner}, creating a vault or unlocking first as Open Vault... does;
     * Vault reports its failures in its own notifications, and does nothing when {@code owner} is null or closed. A
     * modal dialog over {@code owner}, such as Settings, keeps the manager and its prompts from taking input until it
     * closes, so close such a dialog first. The interface default does nothing.
     *
     * @param owner the window the manager opens over
     * @throws IllegalStateException when Credential Vault is called off the UI thread
     */
    default void openManager(WindowHandle owner) { }
}
