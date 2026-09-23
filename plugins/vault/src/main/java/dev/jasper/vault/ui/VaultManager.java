package dev.jasper.vault.ui;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.crypto.SecureBytes;
import java.nio.file.Path;
import java.time.Instant;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.model.Vault;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import dev.jasper.vault.lock.LockManager;

/** UI-thread editing operations; asynchronous I/O completes on the supplied UI executor. */
public final class VaultManager {
    public enum Type {
        LOGIN("Login"), SSH_KEY("SSH Key"), NOTE("Secure Note");
        private final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Row(UUID id, String name, String subtitle, Type type) {
        @Override public String toString() { return name + " (" + type + ")"; }
    }
    private final LockManager lock;
    private final Executor background, ui;
    private final Runnable changed;
    private boolean busy;
    private long generation;
    private Runnable retainedCleanup = () -> {};

    public VaultManager(LockManager lock, Executor background, Executor ui, Runnable changed) {
        this.lock = lock; this.background = background; this.ui = ui; this.changed = changed;
    }
    public boolean busy() { return busy; }
    public List<Row> rows() {
        if (lock.state() != LockState.UNLOCKED) return List.of();
        var rows = new ArrayList<Row>();
        lock.vault().accounts().forEach(a -> rows.add(new Row(a.id(), a.name(), a.username(), Type.LOGIN)));
        lock.vault().keys().forEach(k -> rows.add(new Row(k.id(), k.name(), k.fingerprint(), Type.SSH_KEY)));
        lock.vault().managedKeys().forEach(k -> rows.add(new Row(k.id(), k.name(), "Stored in Vault · " + k.fingerprint(), Type.SSH_KEY)));
        lock.vault().notes().forEach(n -> rows.add(new Row(n.id(), n.name(), "Secure note", Type.NOTE)));
        return List.copyOf(rows);
    }
    public List<Grant> grants() {
        return lock.state() == LockState.UNLOCKED ? List.copyOf(lock.vault().grants()) : List.of();
    }
    /** Borrowed model value: use synchronously on the UI thread; never retain it in a window. */
    public Optional<Object> entry(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return Optional.empty();
        Vault v = lock.vault();
        return v.account(id).map(a -> (Object) a).or(() -> v.key(id).map(k -> (Object) k))
            .or(() -> v.managedKey(id).map(k -> (Object) k))
            .or(() -> v.notes().stream().filter(n -> n.id().equals(id)).map(n -> (Object) n).findFirst());
    }
    public CompletableFuture<Void> saveAccount(Account value) {
        if (!editable()) { value.auth().zero(); return rejected(); }
        retainedCleanup = value.auth()::zero;
        return transaction(v -> {
            Account old = v.account(value.id()).orElse(null);
            if (old != null) { v.accounts().remove(old); old.auth().zero(); }
            v.accounts().add(new Account(value.id(), value.name(), value.username(), copyAuth(value.auth()), value.created(), value.updated()));
        }).whenComplete((ignored, failure) -> value.auth().zero());
    }
    private static Auth copyAuth(Auth auth) {
        return switch (auth) {
            case Auth.Password p -> new Auth.Password(p.password().clone());
            case Auth.Key k -> new Auth.Key(k.keyPath(), k.passphrase() == null ? null : k.passphrase().clone());
            case Auth.KeyAndPassword k -> new Auth.KeyAndPassword(k.keyPath(), k.passphrase() == null ? null : k.passphrase().clone(), k.password().clone());
        };
    }
    public CompletableFuture<Void> saveNote(Note value) {
        if (!editable()) { value.zero(); return rejected(); }
        retainedCleanup = value::zero;
        return transaction(v -> {
            v.notes().removeIf(n -> { if (!n.id().equals(value.id())) return false; n.zero(); return true; });
            v.notes().add(new Note(value.id(), value.name(), value.text().clone(), value.updated()));
        }).whenComplete((ignored, failure) -> value.zero());
    }
    public CompletableFuture<Void> renameManaged(UUID id, String name) {
        if (!editable()) return rejected();
        return transaction(v -> {
            var old = v.managedKey(id).orElseThrow();
            var renamed = old.renamed(name);
            v.managedKeys().remove(old); old.close(); v.managedKeys().add(renamed);
        });
    }
    public CompletableFuture<Void> saveKey(SshKey value) {
        if (!editable()) return rejected();
        return transaction(v -> {
            if (v.managedKey(value.id()).isPresent()) throw new IllegalStateException("This key is now stored in Vault; reopen its editor");
            v.keys().removeIf(k -> k.id().equals(value.id())); v.keys().add(value);
        });
    }
    public CompletableFuture<Void> revoke(Grant grant) {
        if (!editable()) return rejected();
        return transaction(v -> v.grants().remove(grant));
    }
    public CompletableFuture<Void> delete(UUID id, boolean deleteFiles) {
        if (!editable()) return rejected();
        Object value = entry(id).orElseThrow(() -> new IllegalArgumentException("The entry no longer exists"));
        var saved = transaction(v -> v.remove(id));
        if (!deleteFiles || !(value instanceof SshKey key)) return saved;
        return saved.thenCompose(ignored -> removeGeneratedFiles(key));
    }
    public CompletableFuture<String> publicKey(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked"));
        var managed = lock.vault().managedKey(id);
        if (managed.isPresent()) return CompletableFuture.completedFuture(managed.get().publicKey());
        SshKey key = lock.vault().key(id).orElseThrow(() -> new IllegalArgumentException("Select an SSH key"));
        long expected = generation;
        return io(() -> {
            try (var in = Files.newInputStream(key.publicPath())) {
                byte[] bytes = in.readNBytes(65_537);
                if (bytes.length > 65_536) throw new IOException("Public key file is too large");
                return new String(bytes, StandardCharsets.US_ASCII);
            }
        }).thenApply(text -> {
            if (generation != expected || lock.state() != LockState.UNLOCKED) throw new IllegalStateException("The vault was locked meanwhile");
            return text;
        });
    }
    /** Called on lock/stop, even if a save is still pending. */
    public void invalidate() { generation++; retainedCleanup.run(); retainedCleanup = () -> {}; }
    private boolean editable() { return !busy && lock.state() == LockState.UNLOCKED; }
    private CompletableFuture<Void> rejected() {
        return CompletableFuture.failedFuture(new IllegalStateException(busy ? "An edit is still saving" : "The vault is locked"));
    }
    private CompletableFuture<Void> transaction(Consumer<Vault> mutation) {
        long expected = generation; busy = true;
        return lock.transact(v -> { mutation.accept(v); return (Void) null; }, () -> generation != expected)
            .whenComplete((ignored, failure) -> { busy = false; retainedCleanup = () -> {}; changed.run(); });
    }
    private <T> CompletableFuture<T> io(Callable<T> operation) {
        var result = new CompletableFuture<T>();
        try {
            background.execute(() -> {
                try { T value = operation.call(); ui.execute(() -> result.complete(value)); }
                catch (Exception failure) { ui.execute(() -> result.completeExceptionally(failure)); }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }
    public CompletableFuture<Void> importKey(SshKey selected) {
        if (!editable()) return rejected();
        long expected = generation;
        return io(() -> KeyFiles.inspect(selected)).thenCompose(key -> {
            if (expected != generation || lock.state() != LockState.UNLOCKED)
                return CompletableFuture.failedFuture(new IllegalStateException("The vault was locked meanwhile"));
            return saveKey(key);
        });
    }

    public CompletableFuture<Void> generate(Path directory, KeyAlgorithm algorithm, String name, String comment, Optional<String> username) {
        return generate(directory, algorithm, name, comment, username, null);
    }

    /** Consumes passphrase; only a successful optional account retains its own copy. */
    public CompletableFuture<Void> generate(Path directory, KeyAlgorithm algorithm, String name, String comment, Optional<String> username, char[] passphrase) {
        if (!editable()) { SecureBytes.zero(passphrase); return rejected(); }
        if (name.isBlank() || username.filter(String::isBlank).isPresent()) {
            SecureBytes.zero(passphrase);
            return CompletableFuture.failedFuture(new IllegalArgumentException("Enter a key name and, when selected, an account username"));
        }
        char[] phrase = passphrase == null || passphrase.length == 0 ? null : passphrase.clone();
        SecureBytes.zero(passphrase);
        long expected = generation;
        busy = true;
        CompletableFuture<Void> operation = io(() -> new KeyGenerator(directory).generate(algorithm, name, comment, phrase)).thenCompose(key -> {
            CompletableFuture<Void> save;
            if (generation != expected || lock.state() != LockState.UNLOCKED) {
                save = CompletableFuture.failedFuture(new IllegalStateException("The vault was locked during key generation"));
            } else {
                save = transaction(v -> {
                    v.keys().add(key);
                    username.ifPresent(user -> v.accounts().add(new Account(UUID.randomUUID(), name + " (" + user + ")", user,
                        new Auth.Key(key.privatePath(), phrase == null ? null : phrase.clone()), Instant.now(), Instant.now())));
                });
            }
            return save.handle((ignored, failure) -> failure).thenCompose(failure -> {
                if (failure == null) return CompletableFuture.completedFuture(null);
                return removeGeneratedFiles(key).handle((ignored, cleanupFailure) -> {
                    if (cleanupFailure != null) {
                        var combined = new IOException("Could not save generated key; file cleanup also failed. Check "
                            + key.privatePath() + " and " + key.publicPath(), failure);
                        combined.addSuppressed(cleanupFailure);
                        throw new java.util.concurrent.CompletionException(combined);
                    }
                    throw new java.util.concurrent.CompletionException(failure);
                });
            });
        });
        return operation.whenComplete((ignored, failure) -> { SecureBytes.zero(phrase); busy = false; retainedCleanup = () -> {}; changed.run(); });
    }
    private CompletableFuture<Void> removeGeneratedFiles(SshKey key) {
        return io(() -> {
            IOException failure = null;
            for (Path path : List.of(key.privatePath(), key.publicPath())) {
                try { Files.deleteIfExists(path); }
                catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            }
            if (failure != null) throw failure;
            return null;
        });
    }

}
