package dev.jasper.vault.lock;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.crypto.KeyDerivation;
import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.crypto.VaultCipher;
import dev.jasper.vault.crypto.VaultFileFormat;
import dev.jasper.vault.crypto.WrongPasswordException;
import dev.jasper.vault.model.Vault;
import dev.jasper.vault.model.VaultCodec;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.VaultFile;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Owns the open {@link Vault}, the derived key and the salt for one unlock session. Key derivation and
 * file I/O run on {@code background}; every future completes and every listener call happens on {@code ui},
 * which is also the only thread that may call these methods.
 */
public final class LockManager {
    private final VaultFile file;
    private final DeviceSecrets secrets;
    private final Executor background, ui;
    private final Consumer<LockState> listener;
    private Vault vault;
    private byte[] key, salt;
    private boolean bound;
    private long generation;
    private boolean changingPassword;
    private int mutations;
    private final java.util.Set<Vault> staged = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private int persistedVersion = 1;
    public boolean busy() { return changingPassword || mutations != 0; }
    public long generation() { return generation; }
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    private record Opened(Vault vault, byte[] key, byte[] salt, boolean bound) { }
    private record Rekey(byte[] key, byte[] salt) { }

    public LockManager(VaultFile file, DeviceSecrets secrets, Executor background, Executor ui, Consumer<LockState> listener) {
        this.file = file; this.secrets = secrets; this.background = background; this.ui = ui; this.listener = listener;
    }

    public LockState state() { return vault != null ? LockState.UNLOCKED : file.exists() ? LockState.LOCKED : LockState.NO_VAULT; }
    public boolean bound() { return bound; }
    public String deviceSecretSource() { return secrets.source(); }

    /** The open vault; edit it on the UI thread and call {@link #save()}. */
    public Vault vault() {
        if (vault == null) throw new IllegalStateException("The vault is locked");
        return vault;
    }

    public CompletableFuture<Void> create(char[] password, boolean bind) {
        byte[] material = SecureBytes.utf8(password);
        SecureBytes.zero(password);
        if (state() != LockState.NO_VAULT) { SecureBytes.zero(material); return CompletableFuture.failedFuture(new IllegalStateException("A vault already exists")); }
        long attempt = ++generation;
        return onBackground(() -> {
            byte[] device = bind ? secrets.getOrCreate() : null;
            try {
                byte[] fresh = VaultCipher.randomSalt();
                return new Opened(new Vault(), KeyDerivation.derive(material, device, fresh), fresh, bind);
            } finally { SecureBytes.zero(device); SecureBytes.zero(material); }
        }).thenCompose(opened -> { install(opened, attempt); return save(); });
    }

    public CompletableFuture<Void> unlock(char[] password) {
        byte[] material = SecureBytes.utf8(password);
        SecureBytes.zero(password);
        if (state() != LockState.LOCKED) { SecureBytes.zero(material); return CompletableFuture.failedFuture(new IllegalStateException("No locked vault to unlock")); }
        long attempt = ++generation;
        return onBackground(() -> {
            VaultFileFormat.Parsed parsed = VaultFileFormat.parse(file.read());
            byte[] device = parsed.header().bound() ? secrets.existing().orElseThrow(ForeignDeviceException::new) : null;
            byte[] derived = null;
            try {
                derived = KeyDerivation.derive(material, device, parsed.header().salt());
                byte[] plain = VaultCipher.open(derived, parsed);
                try { return new Opened(VaultCodec.decode(plain), derived, parsed.header().salt(), parsed.header().bound()); }
                finally { SecureBytes.zero(plain); }
            } catch (RuntimeException failure) {
                SecureBytes.zero(derived);
                throw failure;
            } finally { SecureBytes.zero(device); SecureBytes.zero(material); }
        }).thenAccept(opened -> install(opened, attempt));
    }

    /** Zeroes everything and publishes {@link LockState#LOCKED}; a no-op when already locked. */
    public void lock() {
        generation++;
        staged.forEach(Vault::zero); staged.clear();
        if (vault == null) return;
        vault.zero();
        SecureBytes.zero(key); SecureBytes.zero(salt);
        vault = null; key = null; salt = null;
        listener.accept(state());
    }

    /** Snapshots on the UI thread and writes in order; encryption waits for any pending password change. */
    public CompletableFuture<Void> save() {
        if (vault == null) return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked"));
        byte[] plain = VaultCodec.encode(vault);
        if (changingPassword) {
            long session = generation;
            // Rekey commits first. Seal this snapshot with whichever key actually survived it.
            lastWrite = lastWrite.handle((ignored, failure) -> null).thenCompose(ignored -> {
                try {
                    if (vault == null || generation != session)
                        return CompletableFuture.failedFuture(new IllegalStateException("The vault was locked meanwhile"));
                    byte[] bytes = VaultCipher.seal(key, salt, bound, plain);
                    return onBackground(() -> { file.write(bytes); return null; });
                } finally { SecureBytes.zero(plain); }
            });
            return lastWrite;
        }
        byte[] bytes;
        try { bytes = VaultCipher.seal(key, salt, bound, plain); } finally { SecureBytes.zero(plain); }
        lastWrite = lastWrite.handle((ignored, failure) -> null).thenCompose(ignored -> onBackground(() -> { file.write(bytes); return null; }));
        return lastWrite;
    }

    /** Stages an isolated edit and publishes it only after the ordered durable write. */
    public <T> CompletableFuture<T> transact(java.util.function.Function<Vault, T> mutation, BooleanSupplier cancelled) {
        var result = new CompletableFuture<T>();
        long expected = generation;
        mutations++;
        lastWrite = lastWrite.handle((ignored, failure) -> null).thenCompose(ignored -> {
            if (vault == null || expected != generation || cancelled.getAsBoolean() || result.isCancelled())
                return CompletableFuture.<Void>failedFuture(new IllegalStateException("The vault operation was cancelled"));
            Vault original = vault;
            byte[] snapshot = VaultCodec.encode(original);
            Vault draft;
            try { draft = VaultCodec.decode(snapshot); } finally { SecureBytes.zero(snapshot); }
            staged.add(draft);
            T value; byte[] encrypted;
            try {
                value = mutation.apply(draft);
                VaultCodec.validateManaged(draft);
                if (cancelled.getAsBoolean() || result.isCancelled()) throw new IllegalStateException("The vault operation was cancelled");
                byte[] plain = VaultCodec.encode(draft);
                try { encrypted = VaultCipher.seal(key, salt, bound, plain); } finally { SecureBytes.zero(plain); }
            } catch (Throwable failure) { staged.remove(draft); draft.zero(); return CompletableFuture.<Void>failedFuture(failure); }
            int version = draft.payloadVersion();
            boolean upgrade = persistedVersion == 1 && version == 2;
            return onBackground(() -> {
                if (upgrade && file.exists()) file.backupVersionOne(file.read());
                file.write(encrypted); return (Void) null;
            }).handle((nothing, failure) -> {
                staged.remove(draft);
                if (failure == null) persistedVersion = version;
                if (failure == null && expected == generation && vault == original) {
                    vault = draft; original.zero(); result.complete(value);
                } else {
                    draft.zero();
                    result.completeExceptionally(failure == null ? new IllegalStateException("Vault saved; the vault was locked meanwhile") : failure);
                }
                return (Void) null;
            });
        }).whenComplete((ignored, failure) -> {
            mutations--;
            if (failure != null) result.completeExceptionally(failure);
        });
        return result;
    }

    public CompletableFuture<Void> changePassword(char[] current, char[] replacement) {
        return changePassword(current, replacement, () -> false, () -> { });
    }

    /** Cancels before the commit callback; once called, the ordered file write finishes even if the UI closes. */
    public CompletableFuture<Void> changePassword(char[] current, char[] replacement,
                                                   BooleanSupplier cancelled, Runnable committing) {
        byte[] old = SecureBytes.utf8(current), fresh = SecureBytes.utf8(replacement);
        SecureBytes.zero(current); SecureBytes.zero(replacement);
        if (vault == null) { SecureBytes.zero(old); SecureBytes.zero(fresh); return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked")); }
        if (changingPassword) { SecureBytes.zero(old); SecureBytes.zero(fresh); return CompletableFuture.failedFuture(new IllegalStateException("A password change is already running")); }
        changingPassword = true;
        byte[] currentKey = key.clone(), currentSalt = salt.clone();
        boolean isBound = bound;
        long attempt = generation;
        CompletableFuture<Rekey> derived = onBackground(() -> {
            byte[] device = null;
            try {
                device = isBound ? secrets.existing().orElseThrow(ForeignDeviceException::new) : null;
                byte[] check = KeyDerivation.derive(old, device, currentSalt);
                boolean matches = MessageDigest.isEqual(check, currentKey);
                SecureBytes.zero(check);
                if (!matches) throw new WrongPasswordException();
                byte[] newSalt = VaultCipher.randomSalt();
                return new Rekey(KeyDerivation.derive(fresh, device, newSalt), newSalt);
            } finally { SecureBytes.zero(device); SecureBytes.zero(old); SecureBytes.zero(fresh); SecureBytes.zero(currentKey); SecureBytes.zero(currentSalt); }
        });
        lastWrite = lastWrite.handle((ignored, failure) -> null).thenCompose(ignored -> derived).thenCompose(rekey -> {
            CompletableFuture<Void> written;
            try {
                if (vault == null || attempt != generation || cancelled.getAsBoolean())
                    throw new IllegalStateException("The password change was cancelled");
                byte[] plain = VaultCodec.encode(vault);
                byte[] bytes;
                try { bytes = VaultCipher.seal(rekey.key(), rekey.salt(), bound, plain); }
                finally { SecureBytes.zero(plain); }
                committing.run();
                written = onBackground(() -> { file.write(bytes); return null; });
            } catch (RuntimeException failure) { written = CompletableFuture.failedFuture(failure); }
            return written.whenComplete((ignored, failure) -> {
                if (failure == null && vault != null && attempt == generation) {
                    SecureBytes.zero(key); SecureBytes.zero(salt);
                    key = rekey.key(); salt = rekey.salt();
                } else { SecureBytes.zero(rekey.key()); SecureBytes.zero(rekey.salt()); }
            });
        }).whenComplete((ignored, failure) -> changingPassword = false);
        return lastWrite;
    }

    private void install(Opened opened, long attempt) {
        if (attempt != generation) {
            opened.vault().zero(); SecureBytes.zero(opened.key()); SecureBytes.zero(opened.salt());
            throw new IllegalStateException("The vault operation was cancelled");
        }
        vault = opened.vault(); persistedVersion = vault.payloadVersion(); key = opened.key(); salt = opened.salt(); bound = opened.bound();
        listener.accept(LockState.UNLOCKED);
    }

    /** Runs {@code work} on the background executor and completes the result on the UI executor. */
    private <T> CompletableFuture<T> onBackground(Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> {
            T value;
            try { value = work.call(); }
            catch (Throwable failure) { ui.execute(() -> result.completeExceptionally(failure)); return; }
            ui.execute(() -> result.complete(value));
        });
        return result;
    }
}
