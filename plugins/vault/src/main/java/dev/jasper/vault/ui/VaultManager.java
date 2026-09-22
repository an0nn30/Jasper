package dev.jasper.vault.ui;

import dev.jasper.vault.api.LockState;
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
    public enum Type { LOGIN, SSH_KEY, NOTE }
    public record Row(UUID id, String name, String subtitle, Type type) {
        @Override public String toString() { return name + " (" + type + ")"; }
    }
    private final LockManager lock;
    private final Executor background, ui;
    private final Runnable changed;
    private boolean busy;
    private long generation;
    private Runnable retainedCleanup = () -> { };

    public VaultManager(LockManager lock, Executor background, Executor ui, Runnable changed) {
        this.lock = lock; this.background = background; this.ui = ui; this.changed = changed;
    }
    public boolean busy() { return busy; }
    public List<Row> rows() {
        if (lock.state() != LockState.UNLOCKED) return List.of();
        var rows = new ArrayList<Row>();
        lock.vault().accounts().forEach(a -> rows.add(new Row(a.id(), a.name(), a.username(), Type.LOGIN)));
        lock.vault().keys().forEach(k -> rows.add(new Row(k.id(), k.name(), k.fingerprint(), Type.SSH_KEY)));
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
            .or(() -> v.notes().stream().filter(n -> n.id().equals(id)).map(n -> (Object) n).findFirst());
    }
    public CompletableFuture<Void> saveAccount(Account value) {
        if (!editable()) { value.auth().zero(); return rejected(); }
        Vault v = lock.vault();
        Account old = v.account(value.id()).orElse(null);
        return replace(v.accounts(), old, value, () -> { if (old != null) old.auth().zero(); }, value.auth()::zero);
    }
    public CompletableFuture<Void> saveNote(Note value) {
        if (!editable()) { value.zero(); return rejected(); }
        Vault v = lock.vault();
        Note old = v.notes().stream().filter(n -> n.id().equals(value.id())).findFirst().orElse(null);
        return replace(v.notes(), old, value, () -> { if (old != null) old.zero(); }, value::zero);
    }
    public CompletableFuture<Void> saveKey(SshKey value) {
        if (!editable()) return rejected();
        return replace(lock.vault().keys(), lock.vault().key(value.id()).orElse(null), value, () -> { }, () -> { });
    }
    private <T> CompletableFuture<Void> replace(List<T> list, T old, T value, Runnable oldCleanup, Runnable newCleanup) {
        int index = old == null ? list.size() : list.indexOf(old);
        return edit(v -> { if (old == null) list.add(value); else list.set(index, value); },
            () -> { if (old == null) list.remove(value); else list.set(index, old); }, oldCleanup, newCleanup);
    }
    public CompletableFuture<Void> revoke(Grant grant) {
        if (!editable()) return rejected();
        Vault v = lock.vault();
        boolean existed = v.grants().contains(grant);
        return edit(ignored -> v.grants().remove(grant), () -> { if (existed) v.grants().add(grant); }, () -> { }, () -> { });
    }
    public CompletableFuture<Void> delete(UUID id, boolean deleteFiles) {
        if (!editable()) return rejected();
        Vault v = lock.vault();
        Object value = entry(id).orElseThrow(() -> new IllegalArgumentException("The entry no longer exists"));
        List<Grant> grants = v.grants().stream().filter(g -> g.credentialId().equals(id)).toList();
        Runnable wipe = () -> { if (value instanceof Account a) a.auth().zero(); if (value instanceof Note n) n.zero(); };
        var saved = edit(ignored -> {
            v.accounts().remove(value); v.keys().remove(value); v.notes().remove(value); v.grants().removeAll(grants);
        }, () -> {
            if (value instanceof Account a) v.accounts().add(a);
            if (value instanceof SshKey k) v.keys().add(k);
            if (value instanceof Note n) v.notes().add(n);
            v.grants().addAll(grants);
        }, wipe, () -> { });
        if (!deleteFiles || !(value instanceof SshKey key)) return saved;
        return saved.thenCompose(ignored -> io(() -> {
            IOException failure = null;
            for (var path : List.of(key.privatePath(), key.publicPath())) {
                try { Files.deleteIfExists(path); }
                catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            }
            if (failure != null) throw new IOException("Entry removed; could not delete all key files: "
                + key.privatePath() + ", " + key.publicPath(), failure);
            return null;
        }));
    }
    public CompletableFuture<String> publicKey(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked"));
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
    public void invalidate() { generation++; retainedCleanup.run(); retainedCleanup = () -> { }; }
    private boolean editable() { return !busy && lock.state() == LockState.UNLOCKED; }
    private CompletableFuture<Void> rejected() {
        return CompletableFuture.failedFuture(new IllegalStateException(busy ? "An edit is still saving" : "The vault is locked"));
    }
    private CompletableFuture<Void> edit(Consumer<Vault> apply, Runnable undo, Runnable committed, Runnable rejected) {
        Vault original = lock.vault();
        long expected = generation;
        busy = true; retainedCleanup = committed;
        CompletableFuture<Void> save;
        try { apply.accept(original); save = lock.save(); }
        catch (RuntimeException failure) { save = CompletableFuture.failedFuture(failure); }
        return save.handle((ignored, failure) -> {
            boolean same = expected == generation && lock.state() == LockState.UNLOCKED && lock.vault() == original;
            if (failure == null) committed.run();
            else if (same) { undo.run(); rejected.run(); }
            else { committed.run(); rejected.run(); }
            retainedCleanup = () -> { }; busy = false;
            changed.run();
            if (failure != null) throw new java.util.concurrent.CompletionException(failure);
            return null;
        });
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
}
