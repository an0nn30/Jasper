package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.*;
import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** One import's UI-thread state machine. Prepared secrets stay private and die with the request. */
public final class KeyImportPrompt {
    public enum Phase { OPENING, READING, PASSPHRASE, REVIEW, COMMITTING, COMPLETE, CANCELLED }
    public record Row(String name, String fingerprint, boolean reused) {}
    private final PluginInfo consumer;
    private final WindowHandle owner;
    private final List<SshKeySource> sources;
    private final List<UUID> selected;
    private final LockManager lock;
    private final Executor background, ui;
    private final Consumer<String> notice;
    private final Runnable changed;
    private final Map<Path, PreparedKey> prepared = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final CompletableFuture<Optional<SshKeyImportResult>> result = new CompletableFuture<>();
    private CompletableFuture<Boolean> opening;
    private Phase phase = Phase.OPENING;
    private String status = "Opening Vault...";
    private int index;
    private long generation;
    private boolean dismissed;
    public KeyImportPrompt(PluginInfo consumer, WindowHandle owner, List<SshKeySource> sources, List<UUID> selected,
                           LockManager lock, Executor background, Executor ui, Consumer<String> notice, Runnable changed) {
        this.consumer = consumer; this.owner = Objects.requireNonNull(owner); this.sources = List.copyOf(sources);
        this.selected = List.copyOf(new LinkedHashSet<>(selected)); this.lock = lock; this.background = background; this.ui = ui;
        this.notice = notice; this.changed = changed;
        if (sources.size() > 4096 || sources.stream().map(SshKeySource::requestId).distinct().count() != sources.size())
            throw new IllegalArgumentException("Invalid key import batch");
        result.whenComplete((answer, failure) -> { if (result.isCancelled()) ui.execute(this::cancel); });
    }
    public WindowHandle owner() { return owner; }
    public String consumerName() { return consumer.name(); }
    public Phase phase() { return phase; }
    public String status() { return status; }
    public CompletableFuture<Optional<SshKeyImportResult>> result() { return result; }
    public Subscription onChanged(Runnable callback) { listeners.add(callback); return () -> listeners.remove(callback); }
    public List<Row> rows() {
        var rows = new ArrayList<Row>();
        for (PreparedKey p : prepared.values()) rows.add(new Row(p.name(), p.fingerprint(), lock.state() == LockState.UNLOCKED &&
            (lock.vault().managedKeys().stream().anyMatch(k -> k.fingerprint().equals(p.fingerprint())) ||
             lock.vault().keys().stream().anyMatch(k -> k.fingerprint().equals(p.fingerprint())))));
        if (lock.state() == LockState.UNLOCKED) for (UUID id : selected) {
            var key = lock.vault().managedKey(id).orElse(null);
            if (key != null) rows.add(new Row(key.name(), key.fingerprint(), true));
            else lock.vault().account(id).ifPresent(account -> rows.add(new Row(account.name(), "Password account: " + account.username(), true)));
        }
        return List.copyOf(rows);
    }
    public void start(Function<WindowHandle, CompletableFuture<Boolean>> open) {
        try { opening = open.apply(owner); }
        catch (RuntimeException failure) { fail("Could not open Vault"); return; }
        opening.whenComplete((ok, failure) -> ui.execute(() -> {
            if (dismissed || !owner.isOpen()) { cancel(); return; }
            if (failure != null) { fail("Could not open Vault"); return; }
            if (!Boolean.TRUE.equals(ok) || lock.state() != LockState.UNLOCKED) { cancel(); return; }
            generation = lock.generation(); next(null);
        }));
    }
    private boolean live() {
        return !dismissed && owner.isOpen() && lock.state() == LockState.UNLOCKED && generation == lock.generation();
    }
    private void next(char[] phrase) {
        if (!live()) { SecureBytes.zero(phrase); cancel(); return; }
        while (index < sources.size() && prepared.containsKey(sources.get(index).path())) index++;
        if (index == sources.size()) {
            SecureBytes.zero(phrase);
            try { selectedDescriptors(lock.vault()); }
            catch (IllegalArgumentException invalid) { fail(invalid.getMessage()); return; }
            phase = Phase.REVIEW; status = "Import and allow " + consumer.name() + " to use these credentials"; fire(); return;
        }
        SshKeySource source = sources.get(index);
        phase = Phase.READING; status = "Reading " + source.path().getFileName(); fire();
        try {
            background.execute(() -> {
                PreparedKey value = null; Exception failure = null;
                try { value = KeyInspector.read(source.name(), source.path(), phrase); }
                catch (Exception problem) { failure = problem; }
                finally { SecureBytes.zero(phrase); }
                PreparedKey read = value; Exception error = failure;
                ui.execute(() -> {
                    if (!live()) { if (read != null) read.close(); cancel(); return; }
                    if (error instanceof KeyInspector.PassphraseRequired || error instanceof KeyInspector.InvalidPassphrase) {
                        phase = Phase.PASSPHRASE; status = source.path().getFileName() + ": " + error.getMessage(); fire(); return;
                    }
                    if (error != null) { fail("Could not import " + source.path().getFileName() + ": unreadable, unsupported or mismatched key"); return; }
                    prepared.put(source.path(), read); index++; next(null);
                });
            });
        } catch (RuntimeException rejected) { SecureBytes.zero(phrase); fail("Could not start key import"); }
    }
    public void submitPassphrase(char[] phrase) {
        if (phase != Phase.PASSPHRASE) { SecureBytes.zero(phrase); return; }
        next(phrase);
    }
    public void commit() {
        if (phase != Phase.REVIEW) return;
        if (!live()) { cancel(); return; }
        phase = Phase.COMMITTING; status = "Saving keys in Vault..."; fire();
        lock.transact(v -> {
            var bindings = new LinkedHashMap<UUID, CredentialDescriptor>();
            for (SshKeySource source : sources) {
                PreparedKey p = prepared.get(source.path());
                ManagedSshKey key = v.managedKeys().stream().filter(k -> k.fingerprint().equals(p.fingerprint())).findFirst().orElse(null);
                if (key == null) {
                    SshKey legacy = v.keys().stream().filter(k -> k.fingerprint().equals(p.fingerprint())).findFirst().orElse(null);
                    key = p.toManaged(legacy == null ? UUID.randomUUID() : legacy.id(), legacy == null ? Instant.now() : legacy.created());
                    if (legacy != null) v.keys().remove(legacy);
                    v.managedKeys().add(key); v.requireManagedFormat();
                }
                v.grants().add(new Grant(consumer.id(), key.id()));
                bindings.put(source.requestId(), descriptor(key));
            }
            List<CredentialDescriptor> choices = selectedDescriptors(v);
            choices.forEach(d -> v.grants().add(new Grant(consumer.id(), d.id())));
            return new SshKeyImportResult(bindings, choices);
        }, () -> !live()).whenComplete((answer, failure) -> {
            clear(); phase = Phase.COMPLETE;
            if (failure != null) result.completeExceptionally(new IllegalStateException("Could not finish saving imported credentials; check Vault before retrying"));
            else {
                changed.run();
                if (dismissed || !owner.isOpen()) notice.accept("Keys were saved in Vault; host import was cancelled. Re-import to finish.");
                result.complete(Optional.of(answer));
            }
            fire();
        });
    }
    static CredentialDescriptor descriptor(ManagedSshKey k) { return new CredentialDescriptor(k.id(), k.name(), k.fingerprint(), Kind.SSH_KEY, true); }
    private List<CredentialDescriptor> selectedDescriptors(Vault vault) {
        var out = new ArrayList<CredentialDescriptor>();
        for (UUID id : selected) {
            ManagedSshKey key = vault.managedKey(id).orElse(null);
            if (key != null) { out.add(descriptor(key)); continue; }
            Account account = vault.account(id).orElse(null);
            if (account != null && account.auth() instanceof Auth.Password) {
                out.add(new CredentialDescriptor(id, account.name(), account.username(), Kind.ACCOUNT_PASSWORD)); continue;
            }
            throw new IllegalArgumentException("Choose a stored Vault key or password; import file-based keys first");
        }
        return List.copyOf(out);
    }
    public void cancel() {
        dismissed = true;
        if (phase == Phase.COMMITTING) { clear(); result.cancel(false); return; }
        if (phase == Phase.COMPLETE || phase == Phase.CANCELLED) return;
        if (opening != null && !opening.isDone()) opening.cancel(false);
        clear(); phase = Phase.CANCELLED; result.complete(Optional.empty()); fire();
    }
    private void fail(String message) { clear(); phase = Phase.COMPLETE; result.completeExceptionally(new IllegalArgumentException(message)); fire(); }
    private void clear() { prepared.values().forEach(PreparedKey::close); prepared.clear(); }
    private void fire() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
