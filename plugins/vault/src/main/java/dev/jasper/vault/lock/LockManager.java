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
        return onBackground(() -> {
            byte[] device = bind ? secrets.getOrCreate() : null;
            try {
                byte[] fresh = VaultCipher.randomSalt();
                return new Opened(new Vault(), KeyDerivation.derive(material, device, fresh), fresh, bind);
            } finally { SecureBytes.zero(device); SecureBytes.zero(material); }
        }).thenCompose(opened -> { install(opened); return save(); });
    }

    public CompletableFuture<Void> unlock(char[] password) {
        byte[] material = SecureBytes.utf8(password);
        SecureBytes.zero(password);
        if (state() != LockState.LOCKED) { SecureBytes.zero(material); return CompletableFuture.failedFuture(new IllegalStateException("No locked vault to unlock")); }
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
        }).thenAccept(this::install);
    }

    /** Zeroes everything and publishes {@link LockState#LOCKED}; a no-op when already locked. */
    public void lock() {
        if (vault == null) return;
        vault.zero();
        SecureBytes.zero(key); SecureBytes.zero(salt);
        vault = null; key = null; salt = null;
        listener.accept(state());
    }

    /** Encrypts the open vault now (on the UI thread) and writes it in the background, writes in order. */
    public CompletableFuture<Void> save() {
        if (vault == null) return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked"));
        byte[] plain = VaultCodec.encode(vault);
        byte[] bytes;
        try { bytes = VaultCipher.seal(key, salt, bound, plain); } finally { SecureBytes.zero(plain); }
        lastWrite = lastWrite.handle((ignored, failure) -> null).thenCompose(ignored -> onBackground(() -> { file.write(bytes); return null; }));
        return lastWrite;
    }

    public CompletableFuture<Void> changePassword(char[] current, char[] replacement) {
        byte[] old = SecureBytes.utf8(current), fresh = SecureBytes.utf8(replacement);
        SecureBytes.zero(current); SecureBytes.zero(replacement);
        if (vault == null) { SecureBytes.zero(old); SecureBytes.zero(fresh); return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked")); }
        byte[] currentKey = key.clone(), currentSalt = salt.clone();
        boolean isBound = bound;
        return onBackground(() -> {
            byte[] device = isBound ? secrets.existing().orElseThrow(ForeignDeviceException::new) : null;
            try {
                byte[] check = KeyDerivation.derive(old, device, currentSalt);
                boolean matches = MessageDigest.isEqual(check, currentKey);
                SecureBytes.zero(check);
                if (!matches) throw new WrongPasswordException();
                byte[] newSalt = VaultCipher.randomSalt();
                return new Rekey(KeyDerivation.derive(fresh, device, newSalt), newSalt);
            } finally { SecureBytes.zero(device); SecureBytes.zero(old); SecureBytes.zero(fresh); SecureBytes.zero(currentKey); }
        }).thenCompose(rekey -> {
            if (vault == null) { SecureBytes.zero(rekey.key()); return CompletableFuture.failedFuture(new IllegalStateException("The vault was locked meanwhile")); }
            SecureBytes.zero(key); SecureBytes.zero(salt);
            key = rekey.key(); salt = rekey.salt();
            return save();
        });
    }

    private void install(Opened opened) {
        vault = opened.vault(); key = opened.key(); salt = opened.salt(); bound = opened.bound();
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
