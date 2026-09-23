package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
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
    static final String NO_VAULT_NOTICE = "Create a vault in Credential Vault first (Open Vault..., F8)";
    static final String NO_WINDOW_NOTICE = "Credential Vault has no window to ask in";

    private final LockManager lock;
    private final Supplier<Optional<WindowHandle>> fallbackOwner;
    private final Consumer<UnlockPrompt> showUnlock;
    private final Consumer<GrantPrompt> showGrant;
    private final Consumer<PickPrompt> showPick;
    private final Consumer<String> notice;
    private java.util.concurrent.Executor background, ui;
    private java.util.function.Function<WindowHandle, CompletableFuture<Boolean>> openForImport;
    private Consumer<KeyImportPrompt> showImport;
    private Runnable changed;
    private final Set<KeyImportPrompt> imports = new HashSet<>();
    private UnlockPrompt unlock;
    private final Map<Grant, GrantPrompt> grantPrompts = new HashMap<>();
    private final List<PickPrompt> pickPrompts = new ArrayList<>();
    private final Set<Grant> oneTime = new HashSet<>();
    private final Map<String, WindowHandle> lastOwner = new HashMap<>();

    public VaultService(LockManager lock, Supplier<Optional<WindowHandle>> fallbackOwner, Consumer<UnlockPrompt> showUnlock,
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, Consumer<String> notice) {
        this.lock = lock; this.fallbackOwner = fallbackOwner; this.showUnlock = showUnlock; this.showGrant = showGrant; this.showPick = showPick; this.notice = notice;
    }

    public VaultService(LockManager lock, Supplier<Optional<WindowHandle>> fallbackOwner, Consumer<UnlockPrompt> showUnlock,
                        Consumer<GrantPrompt> showGrant, Consumer<PickPrompt> showPick, Consumer<String> notice,
                        java.util.concurrent.Executor background, java.util.concurrent.Executor ui,
                        java.util.function.Function<WindowHandle, CompletableFuture<Boolean>> openForImport,
                        Consumer<KeyImportPrompt> showImport, Runnable changed) {
        this(lock, fallbackOwner, showUnlock, showGrant, showPick, notice);
        this.background = background; this.ui = ui; this.openForImport = openForImport;
        this.showImport = showImport; this.changed = changed;
    }
    public void closeImports() { for (KeyImportPrompt prompt : List.copyOf(imports)) prompt.cancel(); }

    public VaultApi forConsumer(PluginInfo consumer) { return new ConsumerApi(consumer); }

    /** Wire this to the lock manager's listener: a lock forgets one-time grants and closes every grant and pick prompt. */
    public void lockStateChanged(LockState state) {
        if (state == LockState.UNLOCKED) return;
        closeImports();
        oneTime.clear();
        for (GrantPrompt prompt : List.copyOf(grantPrompts.values())) prompt.answer(GrantPrompt.Decision.DENY);
        for (PickPrompt prompt : List.copyOf(pickPrompts)) prompt.cancel();
    }

    /** The unlock prompt over {@code owner}, shared with every other requester; {@code true} once unlocked. */
    public CompletableFuture<Boolean> requestUnlock(WindowHandle owner) {
        switch (lock.state()) {
            case UNLOCKED: return CompletableFuture.completedFuture(true);
            case NO_VAULT: notice.accept(NO_VAULT_NOTICE); return CompletableFuture.completedFuture(false);
            default:
        }
        if (unlock == null) {
            unlock = new UnlockPrompt(owner, lock, () -> unlock = null);
            CompletableFuture<Boolean> waiter = unlock.attach();
            showUnlock.accept(unlock);
            return waiter;
        }
        return unlock.attach();
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
                        if (failure != null) { notice.accept("Could not save the credential grant"); prompt.settle(Optional::empty); }
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
            KeyImportPrompt prompt = new KeyImportPrompt(consumer, owner, sources, selected, lock, background, ui, notice, changed);
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
                notice.accept(NO_WINDOW_NOTICE);
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
