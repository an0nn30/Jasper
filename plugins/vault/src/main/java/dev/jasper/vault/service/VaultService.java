package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.SshKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One per plugin instance; {@link #forConsumer} hands each requiring plugin its own view. */
public final class VaultService {
    static final String NO_VAULT_TITLE = "No vault yet";
    static final String NO_VAULT_NOTICE = "Create a vault in Credential Vault first (Open Vault...)";
    static final String NO_WINDOW_NOTICE = "Credential Vault has no window to ask in";

    private final LockManager lock;
    private final Supplier<Optional<WindowHandle>> fallbackOwner;
    private final Consumer<UnlockPrompt> showUnlock;
    private final Consumer<GrantPrompt> showGrant;
    private final Consumer<PickPrompt> showPick;
    private final NotificationGroup notifications;
    private java.util.concurrent.Executor background, ui;
    private java.util.function.Function<WindowHandle, CompletableFuture<Boolean>> openForImport;
    private Consumer<KeyImportPrompt> showImport;
    private Runnable changed;
    private final Set<KeyImportPrompt> imports = new HashSet<>();
    private UnlockPrompt unlock;
    private final Map<Grant, GrantPrompt> grantPrompts = new HashMap<>();
    private final List<PickPrompt> pickPrompts = new ArrayList<>();
    private Consumer<AnchoredPickPrompt> showAnchored;
    private Consumer<dev.jasper.sdk.ui.UiAnchor> validateAnchor;
    private final Set<AnchoredPickPrompt> anchored=new HashSet<>();
    /** Installs the producer and host capability validation before the API is published. */
    public void anchoredPicker(Consumer<AnchoredPickPrompt> show,Consumer<dev.jasper.sdk.ui.UiAnchor> validate){showAnchored=java.util.Objects.requireNonNull(show);validateAnchor=java.util.Objects.requireNonNull(validate);}
    private final Set<Grant> oneTime = new HashSet<>();
    private final Map<String, WindowHandle> lastOwner = new HashMap<>();

    public VaultService(LockManager lock, Supplier<Optional<WindowHandle>> fallbackOwner, Consumer<UnlockPrompt> showUnlock,
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, NotificationGroup notifications) {
        this.lock = lock; this.fallbackOwner = fallbackOwner; this.showUnlock = showUnlock; this.showGrant = showGrant; this.showPick = showPick;
        this.notifications = notifications;
    }

    public VaultService(LockManager lock, Supplier<Optional<WindowHandle>> fallbackOwner, Consumer<UnlockPrompt> showUnlock,
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, NotificationGroup notifications,
                        java.util.concurrent.Executor background, java.util.concurrent.Executor ui,
                        java.util.function.Function<WindowHandle, CompletableFuture<Boolean>> openForImport,
                        Consumer<KeyImportPrompt> showImport, Runnable changed) {
        this(lock, fallbackOwner, showUnlock, showGrant, showPick, notifications);
        this.background = background; this.ui = ui; this.openForImport = openForImport;
        this.showImport = showImport; this.changed = changed;
    }
    public void closeImports() { for (KeyImportPrompt prompt : List.copyOf(imports)) prompt.cancel(); }

    public VaultApi forConsumer(PluginInfo consumer) { return new ConsumerApi(consumer); }

    /** Wire this to the lock manager's listener: a lock forgets one-time grants and closes every grant and pick prompt. */
    public void lockStateChanged(LockState state) {
        if (state == LockState.UNLOCKED) return;
        closeImports();
        for(var prompt:List.copyOf(anchored))prompt.cancel();
        oneTime.clear();
        for (GrantPrompt prompt : List.copyOf(grantPrompts.values())) prompt.answer(GrantPrompt.Decision.DENY);
        for (PickPrompt prompt : List.copyOf(pickPrompts)) prompt.cancel();
    }

    /** The unlock prompt over {@code owner}, shared with every other requester; {@code true} once unlocked. */
    public CompletableFuture<Boolean> requestUnlock(WindowHandle owner) {return requestUnlock(owner,owner,future -> {});}
    private CompletableFuture<Boolean> requestUnlock(WindowHandle owner,dev.jasper.sdk.WindowOwner presentation,Consumer<CompletableFuture<Boolean>> attached) {
        switch (lock.state()) {
            case UNLOCKED: return CompletableFuture.completedFuture(true);
            case NO_VAULT: notifications.warning(NO_VAULT_TITLE, NO_VAULT_NOTICE); return CompletableFuture.completedFuture(false);
            default:
        }
        if (unlock == null) {
            unlock = new UnlockPrompt(owner, presentation, lock, () -> unlock = null);
            unlock.representWith(showUnlock);
            CompletableFuture<Boolean> waiter = unlock.attach(owner,presentation);
            attached.accept(waiter);
            if(!waiter.isDone())showUnlock.accept(unlock);
            return waiter;
        }
        var waiter=unlock.attach(owner,presentation);attached.accept(waiter);return waiter;
    }

    /** Every account and key without secrets; empty unless unlocked. */
    public List<CredentialDescriptor> descriptors() {
        if (lock.state() != LockState.UNLOCKED) return List.of();
        List<CredentialDescriptor> out = new ArrayList<>();
        for (Account account : lock.vault().accounts()) out.add(describe(account));
        for (SshKey key : lock.vault().keys()) out.add(describe(key));
        for (var key : lock.vault().managedKeys()) out.add(KeyImportPrompt.descriptor(key));
        return List.copyOf(out);
    }

    static CredentialDescriptor describe(Account account) {
        Kind kind = switch (account.auth()) {
            case Auth.Password password -> Kind.ACCOUNT_PASSWORD;
            case Auth.Key key -> Kind.ACCOUNT_KEY;
            case Auth.KeyAndPassword both -> Kind.ACCOUNT_KEY_AND_PASSWORD;
        };
        return new CredentialDescriptor(account.id(), account.name(), account.username(), kind);
    }

    static CredentialDescriptor describe(SshKey key) { return new CredentialDescriptor(key.id(), key.name(), key.fingerprint(), Kind.SSH_KEY); }

    private Optional<CredentialDescriptor> descriptor(UUID id) { return descriptors().stream().filter(d -> d.id().equals(id)).findFirst(); }

    /** A fresh copy for one caller; empty when locked or unknown. */
    private Optional<Credential> copyOf(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return Optional.empty();
        Optional<Account> account = lock.vault().account(id);
        if (account.isPresent()) {
            Account a = account.get();
            return Optional.of(switch (a.auth()) {
                case Auth.Password p -> new Credential(a.id(), a.name(), Kind.ACCOUNT_PASSWORD, a.username(), p.password().clone(), null, null);
                case Auth.Key k -> new Credential(a.id(), a.name(), Kind.ACCOUNT_KEY, a.username(), null, k.keyPath(), k.passphrase() == null ? null : k.passphrase().clone());
                case Auth.KeyAndPassword b -> new Credential(a.id(), a.name(), Kind.ACCOUNT_KEY_AND_PASSWORD, a.username(), b.password().clone(), b.keyPath(), b.passphrase() == null ? null : b.passphrase().clone());
            });
        }
        var managed = lock.vault().managedKey(id);
        if (managed.isPresent()) {
            var k = managed.get();
            return Optional.of(new Credential(k.id(), k.name(), Kind.SSH_KEY, null, null, null,
                k.privateKey().clone(), k.passphrase() == null ? null : k.passphrase().clone()));
        }
        return lock.vault().key(id).map(key -> new Credential(key.id(), key.name(), Kind.SSH_KEY, null, null, key.privatePath(), null));
    }

    private void answered(GrantPrompt prompt, GrantPrompt.Decision decision) {
        grantPrompts.remove(prompt.grant());
        switch (decision) {
            case DENY -> prompt.settle(Optional::empty);
            case ALLOW_ONCE -> prompt.settle(() -> copyOf(prompt.grant().credentialId()));
            case ALWAYS -> {
                lock.transact(v -> { v.grants().add(prompt.grant()); return (Void) null; }, () -> false)
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) { notifications.error("Could not save the credential grant", failure); prompt.settle(Optional::empty); }
                        else prompt.settle(() -> copyOf(prompt.grant().credentialId()));
                    });
            }
        }
    }

    private Optional<WindowHandle> ownerFor(PluginInfo consumer) {
        WindowHandle named = lastOwner.get(consumer.id());
        return named != null && named.isOpen() ? Optional.of(named) : fallbackOwner.get();
    }

    private final class ConsumerApi implements VaultApi {
        private final PluginInfo consumer;
        ConsumerApi(PluginInfo consumer) { this.consumer = consumer; }

        @Override public CompletableFuture<Optional<dev.jasper.vault.api.SshKeyImportResult>> importSshKeys(
                WindowHandle owner, List<dev.jasper.vault.api.SshKeySource> sources, List<UUID> selected) {
            if (showImport == null) return VaultApi.super.importSshKeys(owner, sources, selected);
            lastOwner.put(consumer.id(), owner);
            KeyImportPrompt prompt = new KeyImportPrompt(consumer, owner, sources, selected, lock, background, ui, notifications, changed);
            imports.add(prompt);
            prompt.result().whenComplete((value, failure) -> imports.remove(prompt));
            showImport.accept(prompt); prompt.start(openForImport);
            return prompt.result();
        }

        @Override public LockState lockState() { return lock.state(); }

        @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) {
            lastOwner.put(consumer.id(), owner);
            return requestUnlock(owner);
        }

        @Override public List<CredentialDescriptor> credentials() { return descriptors(); }

        @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
            return credentialForOwner(ownerFor(consumer), id, ignored -> {});
        }

        @Override public CompletableFuture<Optional<Credential>> credential(WindowHandle owner, UUID id) {
            if (owner == null || !owner.isOpen()) return CompletableFuture.completedFuture(Optional.empty());
            lastOwner.put(consumer.id(), owner);
            var result = new CompletableFuture<Optional<Credential>>();
            var active = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Optional<Credential>>>();
            result.whenComplete((v, failure) -> { if (result.isCancelled() && active.get() != null) active.get().cancel(false); });
            requestOwned(owner, id, result, active);
            return result;
        }

        private void requestOwned(WindowHandle owner, UUID id, CompletableFuture<Optional<Credential>> result,
                java.util.concurrent.atomic.AtomicReference<CompletableFuture<Optional<Credential>>> active) {
            if (result.isDone()) return;
            if (!owner.isOpen()) { result.complete(Optional.empty()); return; }
            WindowHandle[] promptOwner = {owner};
            var request = credentialForOwner(Optional.of(owner), id, shown -> promptOwner[0] = shown);
            active.set(request);
            request.whenComplete((value, failure) -> {
                if (result.isDone()) { if (value != null) value.ifPresent(Credential::close); return; }
                if (failure == null && value.isEmpty() && owner.isOpen() && promptOwner[0] != null && !promptOwner[0].isOpen()) {
                    requestOwned(owner, id, result, active); return;
                }
                if (failure != null) result.completeExceptionally(failure);
                else if (!owner.isOpen()) { value.ifPresent(Credential::close); result.complete(Optional.empty()); }
                else result.complete(value);
            });
        }

        private CompletableFuture<Optional<Credential>> credentialForOwner(Optional<WindowHandle> owner, UUID id,
                java.util.function.Consumer<WindowHandle> observedOwner) {
            var result = new CompletableFuture<Optional<Credential>>();
            if (lock.state() != LockState.UNLOCKED && lock.state() != LockState.NO_VAULT && owner.isEmpty()) {
                notifications.warning(NO_WINDOW_NOTICE, "");
                result.complete(Optional.empty());
                return result;
            }
            observedOwner.accept(unlock == null ? owner.orElse(null) : unlock.owner());
            CompletableFuture<Boolean> unlocked = requestUnlock(owner.orElse(null));
            result.whenComplete((ignored, failure) -> { if (result.isCancelled()) unlocked.cancel(false); });
            unlocked.thenAccept(ok -> {
                if (result.isDone()) return;
                if (!ok || owner.filter(window -> !window.isOpen()).isPresent()) { result.complete(Optional.empty()); return; }
                Optional<CredentialDescriptor> described = descriptor(id);
                if (described.isEmpty()) { result.complete(Optional.empty()); return; }
                Grant grant = new Grant(consumer.id(), id);
                if (lock.vault().grants().contains(grant) || oneTime.remove(grant)) { result.complete(copyOf(id)); return; }
                GrantPrompt prompt = grantPrompts.get(grant);
                if (prompt == null) {
                    prompt = new GrantPrompt(grant, consumer.name(), described.get(), owner.orElse(null), VaultService.this::answered);
                    grantPrompts.put(grant, prompt);
                    observedOwner.accept(prompt.owner());
                    prompt.attach(result);
                    showGrant.accept(prompt);
                } else { observedOwner.accept(prompt.owner()); prompt.attach(result); }
            });
            return result;
        }

        @Override public CompletableFuture<dev.jasper.vault.api.CredentialPickResult> pick(WindowHandle owner,dev.jasper.vault.api.CredentialPickRequest request) {
            if(showAnchored==null)throw new UnsupportedOperationException("Anchored picker is unavailable");
            validateAnchor.accept(request.anchor());lastOwner.put(consumer.id(),owner);
            var result=new AnchoredPickPrompt.PickFuture();
            var prompt=new AnchoredPickPrompt(owner,request,result,VaultService.this::descriptors,
                chosen -> oneTime.add(new Grant(consumer.id(),chosen)),ui==null?Runnable::run:ui);
            anchored.add(prompt);prompt.onDismiss(() -> anchored.remove(prompt));
            var lifetime=request.anchor().onClosed(prompt::cancel);prompt.onDismiss(lifetime::close);
            if(!prompt.settled()) {
                var unlocked=requestUnlock(owner,request.anchor(),waiter -> prompt.onDismiss(() -> waiter.cancel(false)));
                unlocked.whenComplete((ok,failure) -> {
                    if(prompt.settled())return;
                    if(failure!=null||!Boolean.TRUE.equals(ok)||!request.anchor().isOpen()){prompt.cancel();return;}
                    try{showAnchored.accept(prompt);}catch(RuntimeException problem){prompt.cancel();throw problem;}
                });
            }
            return result;
        }

        @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) {
            lastOwner.put(consumer.id(), owner);
            var result = new CompletableFuture<Optional<CredentialDescriptor>>();
            CompletableFuture<Boolean> unlocked = requestUnlock(owner);
            result.whenComplete((ignored, failure) -> { if (result.isCancelled()) unlocked.cancel(false); });
            unlocked.thenAccept(ok -> {
                if (result.isDone()) return;
                if (!ok) { result.complete(Optional.empty()); return; }
                var prompt = new PickPrompt(owner, descriptors(), result, chosen -> oneTime.add(new Grant(consumer.id(), chosen)));
                pickPrompts.add(prompt);
                prompt.onDismiss(() -> pickPrompts.remove(prompt));
                showPick.accept(prompt);
            });
            return result;
        }
    }
}
