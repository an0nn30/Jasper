package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.TabHandle;
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
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultServiceTest {
    static final PluginInfo SSH = new PluginInfo("dev.jasper.ssh", "SSH", "0.1.0", Set.of());
    static final PluginInfo OTHER = new PluginInfo("dev.jasper.other", "Other", "0.1.0", Set.of());
    static final UUID PROD = UUID.randomUUID(), KEY = UUID.randomUUID();

    /** A window handle is only an owner here. */
    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void toFront() { }
        };
    }

    final List<KeyImportPrompt> imports = new ArrayList<>();
    final List<UnlockPrompt> unlocks = new ArrayList<>();
    final List<GrantPrompt> grants = new ArrayList<>();
    final List<PickPrompt> picks = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    final List<LockState> states = new ArrayList<>();
    final WindowHandle window = window();
    java.util.concurrent.Executor importBackground = Runnable::run;
    LockManager lock;
    VaultService service;

    /** Inline executors: every future completes before the call returns. */
    VaultService service(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        lock = new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets, Runnable::run, Runnable::run, state -> { states.add(state); if (service != null) service.lockStateChanged(state); });
        service = new VaultService(lock, () -> Optional.of(window), unlocks::add, grants::add, picks::add, notices::add, importBackground, Runnable::run, owner -> CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED), imports::add, () -> {});
        return service;
    }

    /** A vault with one password account and one key, unlocked, password {@code pw}. */
    void populate() {
        lock.create("pw".toCharArray(), false).join();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().keys().add(new SshKey(KEY, "laptop", "ed25519", "SHA256:abc", "", Path.of("/k"), Path.of("/k.pub"), Instant.EPOCH));
        lock.save().join();
    }

    @Test void cancelledQueuedReadNeverPublishesItsLateResult(@TempDir Path dir) throws Exception {
        var work = new java.util.ArrayDeque<Runnable>(); importBackground = work::add;
        VaultApi api = service(dir).forConsumer(SSH); populate();
        var key = new dev.jasper.vault.keygen.KeyGenerator(dir.resolve("keys")).generate(dev.jasper.vault.keygen.KeyAlgorithm.ED25519, "fixture", "test");
        var pending = api.importSshKeys(window, List.of(new dev.jasper.vault.api.SshKeySource(UUID.randomUUID(), "key", key.privatePath())), List.of());
        imports.getFirst().cancel(); work.removeFirst().run();
        assertThat(pending.join()).isEmpty(); assertThat(lock.vault().managedKeys()).isEmpty(); assertThat(lock.vault().grants()).isEmpty();
    }
    @Test void twoReviewedImportsDeduplicateAgainstCommitState(@TempDir Path dir) throws Exception {
        VaultApi api = service(dir).forConsumer(SSH); populate();
        var key = new dev.jasper.vault.keygen.KeyGenerator(dir.resolve("keys")).generate(dev.jasper.vault.keygen.KeyAlgorithm.ED25519, "fixture", "test");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var one = api.importSshKeys(window, List.of(new dev.jasper.vault.api.SshKeySource(a, "one", key.privatePath())), List.of());
        var two = api.importSshKeys(window, List.of(new dev.jasper.vault.api.SshKeySource(b, "two", key.privatePath())), List.of());
        imports.get(0).commit(); imports.get(1).commit();
        assertThat(one.join().orElseThrow().keys().get(a).id()).isEqualTo(two.join().orElseThrow().keys().get(b).id());
        assertThat(lock.vault().managedKeys()).hasSize(1);
    }
    @Test void managedImportDeduplicatesAndGrantsOnlyOnCommit(@TempDir Path dir) throws Exception {
        VaultApi api = service(dir).forConsumer(SSH); populate();
        var generated = new dev.jasper.vault.keygen.KeyGenerator(dir.resolve("keys")).generate(dev.jasper.vault.keygen.KeyAlgorithm.ED25519, "fixture", "test");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var imported = api.importSshKeys(window, List.of(
            new dev.jasper.vault.api.SshKeySource(a, "a", generated.privatePath()),
            new dev.jasper.vault.api.SshKeySource(b, "b", generated.privatePath())), List.of());
        assertThat(imported).isNotDone(); assertThat(lock.vault().managedKeys()).isEmpty();
        imports.getFirst().commit();
        var bindings = imported.join().orElseThrow().keys();
        assertThat(bindings.get(a).id()).isEqualTo(bindings.get(b).id());
        assertThat(lock.vault().managedKeys()).hasSize(1);
        assertThat(lock.vault().grants()).contains(new Grant(SSH.id(), bindings.get(a).id()));
        java.nio.file.Files.delete(generated.privatePath());
        try (Credential credential = api.credential(bindings.get(a).id()).join().orElseThrow()) {
            assertThat(credential.keyBytes()).isPresent(); assertThat(credential.keyPath()).isEmpty();
        }
        assertThat(service.forConsumer(OTHER).credential(bindings.get(a).id())).isNotDone();
    }
    @Test void cancellingPreparedImportMakesNoChanges(@TempDir Path dir) throws Exception {
        VaultApi api = service(dir).forConsumer(SSH); populate();
        var generated = new dev.jasper.vault.keygen.KeyGenerator(dir.resolve("keys")).generate(dev.jasper.vault.keygen.KeyAlgorithm.ED25519, "fixture", "test");
        var result = api.importSshKeys(window, List.of(new dev.jasper.vault.api.SshKeySource(UUID.randomUUID(), "a", generated.privatePath())), List.of());
        imports.getFirst().cancel();
        assertThat(result.join()).isEmpty(); assertThat(lock.vault().managedKeys()).isEmpty();
    }
    @Test void descriptorsCarryNoSecretsAndNeedNoGrant(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        assertThat(api.lockState()).isEqualTo(LockState.NO_VAULT);
        assertThat(api.credentials()).isEmpty();
        populate();
        assertThat(api.lockState()).isEqualTo(LockState.UNLOCKED);
        assertThat(api.credentials()).containsExactly(
            new CredentialDescriptor(PROD, "prod", "deploy", Kind.ACCOUNT_PASSWORD),
            new CredentialDescriptor(KEY, "laptop", "SHA256:abc", Kind.SSH_KEY));
        assertThat(api.ensureUnlocked(window)).isCompletedWithValue(true);
        assertThat(unlocks).isEmpty();
    }

    @Test void aGrantPromptServesEveryWaiterAndAlwaysIsRemembered(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        CompletableFuture<Optional<Credential>> first = api.credential(PROD), second = api.credential(PROD);
        assertThat(grants).as("one prompt for one (plugin, credential) pair").hasSize(1);
        GrantPrompt prompt = grants.getFirst();
        assertThat(prompt.consumerName()).isEqualTo("SSH");
        assertThat(prompt.descriptor().name()).isEqualTo("prod");
        assertThat(prompt.owner()).isSameAs(window);
        assertThat(first).isNotDone();
        boolean[] dismissed = {false};
        prompt.onDismiss(() -> dismissed[0] = true);
        prompt.answer(GrantPrompt.Decision.ALWAYS);
        assertThat(dismissed[0]).isTrue();
        try (Credential a = first.join().orElseThrow(); Credential b = second.join().orElseThrow()) {
            assertThat(a.password()).isEqualTo("s3cret".toCharArray()).isNotSameAs(b.password());
            assertThat(a.username()).contains("deploy");
            assertThat(a.kind()).isEqualTo(Kind.ACCOUNT_PASSWORD);
        }
        assertThat(((Auth.Password) lock.vault().account(PROD).get().auth()).password()).as("the vault's copy is untouched by close").isEqualTo("s3cret".toCharArray());
        assertThat(lock.vault().grants()).containsExactly(new Grant("dev.jasper.ssh", PROD));
        assertThat(api.credential(PROD)).as("granted: no prompt").isDone();
        assertThat(grants).hasSize(1);

        VaultApi other = service.forConsumer(OTHER);
        CompletableFuture<Optional<Credential>> denied = other.credential(PROD);
        assertThat(grants).as("another plugin gets its own prompt").hasSize(2);
        grants.get(1).answer(GrantPrompt.Decision.DENY);
        assertThat(denied.join()).isEmpty();
        assertThat(lock.vault().grants()).hasSize(1);

        CompletableFuture<Optional<Credential>> once = other.credential(KEY);
        grants.get(2).answer(GrantPrompt.Decision.ALLOW_ONCE);
        try (Credential key = once.join().orElseThrow()) {
            assertThat(key.kind()).isEqualTo(Kind.SSH_KEY);
            assertThat(key.keyPath()).contains(Path.of("/k"));
            assertThat(key.username()).isEmpty();
        }
        assertThat(other.credential(KEY)).as("once means once").isNotDone();
        assertThat(api.credential(UUID.randomUUID())).as("unknown id").isCompletedWithValue(Optional.empty());
    }

    @Test void oneUnlockPromptServesEveryRequesterAndReportsErrors(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        lock.vault().grants().add(new Grant("dev.jasper.ssh", PROD));
        lock.save().join();
        lock.lock();
        VaultApi other = service.forConsumer(OTHER);
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Boolean> ensure = other.ensureUnlocked(window);
        assertThat(unlocks).hasSize(1);
        UnlockPrompt prompt = unlocks.getFirst();
        List<String> errors = new ArrayList<>();
        prompt.onError(errors::add);
        prompt.submit("wrong".toCharArray());
        assertThat(errors).containsExactly("Wrong password");
        assertThat(prompt.error()).contains("Wrong password");
        assertThat(fetch).isNotDone();
        prompt.submit("pw".toCharArray());
        assertThat(ensure).isCompletedWithValue(true);
        try (Credential credential = fetch.join().orElseThrow()) { assertThat(credential.name()).isEqualTo("prod"); }
        assertThat(grants).as("the stored grant needed no prompt").isEmpty();
        assertThat(states).containsExactly(LockState.UNLOCKED, LockState.LOCKED, LockState.UNLOCKED);
    }

    @Test void cancellingTheLastWaiterDismissesThePrompt(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        lock.lock();
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Boolean> ensure = service.forConsumer(OTHER).ensureUnlocked(window);
        UnlockPrompt prompt = unlocks.getFirst();
        boolean[] dismissed = {false};
        prompt.onDismiss(() -> dismissed[0] = true);
        fetch.cancel(false);
        assertThat(dismissed[0]).as("another requester still waits").isFalse();
        ensure.cancel(false);
        assertThat(dismissed[0]).isTrue();
        api.credential(PROD);
        assertThat(unlocks).as("a new request opens a new prompt").hasSize(2);
        unlocks.get(1).cancel();
        assertThat(unlocks.get(1).busy()).isFalse();
    }

    @Test void userCancelAnswersFalseAndEmpty(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        lock.lock();
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Boolean> ensure = api.ensureUnlocked(window);
        unlocks.getFirst().cancel();
        assertThat(ensure).isCompletedWithValue(false);
        assertThat(fetch).isCompletedWithValue(Optional.empty());
        assertThat(service.requestUnlock(window)).isNotDone();
        assertThat(unlocks).hasSize(2);
    }

    @Test void pickIsAOneTimeGrant(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        populate();
        CompletableFuture<Optional<CredentialDescriptor>> pick = api.pick(window);
        assertThat(picks).hasSize(1);
        assertThat(picks.getFirst().choices()).extracting(CredentialDescriptor::name).containsExactly("prod", "laptop");
        picks.getFirst().choose(KEY);
        assertThat(pick.join()).map(CredentialDescriptor::id).contains(KEY);
        assertThat(api.credential(KEY)).as("the pick granted this fetch").isDone();
        assertThat(api.credential(KEY)).as("only once").isNotDone();
        CompletableFuture<Optional<CredentialDescriptor>> cancelled = api.pick(window);
        boolean[] dismissed = {false};
        picks.get(1).onDismiss(() -> dismissed[0] = true);
        cancelled.cancel(false);
        assertThat(dismissed[0]).isTrue();
        assertThat(api.pick(window)).isNotDone();
        picks.get(2).cancel();
        assertThat(picks.get(2).settled()).isTrue();
    }

    @Test void noVaultAnswersFalseWithANoticeAndLockingCancelsPrompts(@TempDir Path dir) {
        VaultApi api = service(dir).forConsumer(SSH);
        assertThat(api.ensureUnlocked(window)).isCompletedWithValue(false);
        assertThat(api.credential(PROD)).isCompletedWithValue(Optional.empty());
        assertThat(api.pick(window)).isCompletedWithValue(Optional.empty());
        assertThat(notices).hasSize(3).allSatisfy(notice -> assertThat(notice).contains("Create a vault"));
        assertThat(unlocks).isEmpty();
        populate();
        CompletableFuture<Optional<Credential>> fetch = api.credential(PROD);
        CompletableFuture<Optional<CredentialDescriptor>> pick = api.pick(window);
        lock.lock();
        assertThat(fetch).isCompletedWithValue(Optional.empty());
        assertThat(pick).isCompletedWithValue(Optional.empty());
        assertThat(grants.getFirst().settled()).isTrue();
    }

    @Test void withoutAnyWindowARequestFails(@TempDir Path dir) {
        service(dir);
        service = new VaultService(lock, Optional::empty, unlocks::add, grants::add, picks::add, notices::add, importBackground, Runnable::run, owner -> CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED), imports::add, () -> {});
        populate();
        lock.lock();
        assertThat(service.forConsumer(SSH).credential(PROD)).isCompletedWithValue(Optional.empty());
        assertThat(notices).singleElement().satisfies(notice -> assertThat(notice).contains("window"));
    }
}
