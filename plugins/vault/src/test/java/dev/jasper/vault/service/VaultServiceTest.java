package dev.jasper.vault.service;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
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
    final FakeNotifications notifications = new FakeNotifications();
    final List<LockState> states = new ArrayList<>();
    final WindowHandle window = window();
    java.util.concurrent.Executor importBackground = Runnable::run;
    java.util.concurrent.Executor lockBackground = Runnable::run;
    java.util.concurrent.Executor lockUi = Runnable::run;
    LockManager lock;
    VaultService service;

    /** Inline executors: every future completes before the call returns. */
    VaultService service(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, dir.resolve("x"));
        var secrets = new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
        lock = new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets, lockBackground, lockUi, state -> { states.add(state); if (service != null) service.lockStateChanged(state); });
        service = new VaultService(lock, () -> Optional.of(window), unlocks::add, grants::add, picks::add, notifications.group("dev.jasper.vault"), importBackground, Runnable::run, owner -> CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED), imports::add, () -> {});
        return service;
    }

    /** A vault with one password account and one key, unlocked, password {@code pw}. */
    void populate() {
        lock.create("pw".toCharArray(), false).join();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().keys().add(new SshKey(KEY, "laptop", "ed25519", "SHA256:abc", "", Path.of("/k"), Path.of("/k.pub"), Instant.EPOCH));
        lock.save().join();
    }

    @Test void survivingCredentialRequestReopensPromptOnItsOwnWindow(@TempDir Path dir) {
        var api = service(dir).forConsumer(SSH); populate();
        boolean[] firstOpen = {true};
        var firstOwner = new WindowHandle() {
            final UUID id = UUID.randomUUID();
            public UUID id() { return id; }
            public List<TabHandle> tabs() { return List.of(); }
            public Optional<TabHandle> activeTab() { return Optional.empty(); }
            public boolean isActive() { return false; }
            public boolean isOpen() { return firstOpen[0]; }
            public void toFront() {}
        };
        var secondOwner = window();
        var first = api.credential(firstOwner, PROD);
        var second = api.credential(secondOwner, PROD);
        assertThat(grants).hasSize(1);
        firstOpen[0] = false;
        first.cancel(false);
        grants.getFirst().cancel();
        assertThat(grants).hasSize(2);
        assertThat(grants.getLast().owner()).isSameAs(secondOwner);
        grants.getLast().answer(GrantPrompt.Decision.ALLOW_ONCE);
        try (var secret = second.join().orElseThrow()) { assertThat(secret.username()).contains("deploy"); }
    }

    @Test void explicitCredentialOwnerOverridesActiveWindow(@TempDir Path dir) {
        var api = service(dir).forConsumer(SSH); populate();
        var requestedOwner = window();
        var requested = api.credential(requestedOwner, PROD);
        assertThat(grants.getFirst().owner()).isSameAs(requestedOwner);
        requested.cancel(false);
        lock.lock();
        var unlocking = api.credential(requestedOwner, PROD);
        assertThat(unlocks.getFirst().owner()).isSameAs(requestedOwner);
        unlocking.cancel(false);
    }

    @Test void consentNamesSelectedPasswordCredentials(@TempDir Path dir) {
        var api = service(dir).forConsumer(SSH); populate();
        api.importSshKeys(window, List.of(), List.of(PROD));
        assertThat(imports.getFirst().rows()).extracting(KeyImportPrompt.Row::name).containsExactly("prod");
        imports.getFirst().cancel();
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
        String noVault = "dev.jasper.vault|WARNING|No vault yet|Create a vault in Credential Vault first (Open Vault...)";
        assertThat(notifications.posts()).as("each posts it, counted on one notification").singleElement().satisfies(post -> {
            assertThat(post.line()).isEqualTo(noVault);
            assertThat(post.count()).isEqualTo(3);
        });
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
        service = new VaultService(lock, Optional::empty, unlocks::add, grants::add, picks::add, notifications.group("dev.jasper.vault"), importBackground, Runnable::run, owner -> CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED), imports::add, () -> {});
        populate();
        lock.lock();
        assertThat(service.forConsumer(SSH).credential(PROD)).isCompletedWithValue(Optional.empty());
        assertThat(notifications.lines()).containsExactly("dev.jasper.vault|WARNING|Credential Vault has no window to ask in|");
    }

    // Browser plan 8 Task 2: saveLogin, credentialIfGranted.

    static final String SITE = "https://intranet.example (Corp)";
    /** Every copy saveLogin took of a password, through the service's seam. */
    final List<char[]> copies = new ArrayList<>();

    VaultApi saver(Path dir) { var api = service(dir).forConsumer(SSH); service.saveCopies = copies::add; return api; }

    List<Account> logins(String name) { return lock.vault().accounts().stream().filter(a -> a.name().equals(name)).toList(); }

    void assertCopiesZeroed() {
        assertThat(copies).as("the Vault took its own copy").isNotEmpty();
        assertThat(copies).allSatisfy(copy -> assertThat(copy).as("zeroed").isEqualTo(new char[copy.length]));
    }

    /** Locks and unlocks again, so the vault reads what the file holds. */
    void reopen() { lock.lock(); lock.unlock("pw".toCharArray()).join(); }

    @Test void saveLoginStoresAPasswordLoginWithASavedGrant(@TempDir Path dir) {
        VaultApi api = saver(dir); populate();
        char[] password = "pw".toCharArray();
        CredentialDescriptor saved = api.saveLogin(window, SITE, "u", password).join().orElseThrow();
        assertThat(saved.kind()).isEqualTo(Kind.ACCOUNT_PASSWORD);
        assertThat(saved.name()).isEqualTo(SITE);
        assertThat(saved.subtitle()).isEqualTo("u");
        assertThat(api.credentials()).contains(saved);
        assertThat(lock.vault().grants()).contains(new Grant(SSH.id(), saved.id()));
        try (Credential credential = api.credentialIfGranted(saved.id()).orElseThrow()) {
            assertThat(credential.password()).isEqualTo("pw".toCharArray());
            assertThat(credential.username()).contains("u");
        }
        assertThat(password).as("the caller's array is untouched").isEqualTo("pw".toCharArray());
        assertCopiesZeroed();
        assertThat(unlocks).isEmpty();
        assertThat(grants).as("a saved grant asks nothing").isEmpty();
        assertThat(notifications.posts()).isEmpty();
        reopen();
        assertThat(lock.vault().account(saved.id())).as("written to the file").isPresent();
        assertThat(lock.vault().grants()).contains(new Grant(SSH.id(), saved.id()));
    }

    @Test void saveLoginRejectsABlankNameOrANullUsernameOrPassword(@TempDir Path dir) {
        VaultApi api = saver(dir); populate();
        assertThatThrownBy(() -> api.saveLogin(window, " ", "u", "pw".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.saveLogin(window, null, "u", "pw".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.saveLogin(window, SITE, null, "pw".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.saveLogin(window, SITE, "u", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(logins(SITE)).isEmpty();
        assertThat(api.saveLogin(window, SITE, "", "".toCharArray()).join()).as("an empty username and password are allowed").isPresent();
    }

    @Test void saveLoginReplacesTheGrantedLoginOfTheSameName(@TempDir Path dir) {
        VaultApi api = saver(dir); populate();
        CredentialDescriptor first = api.saveLogin(window, SITE, "u", "old".toCharArray()).join().orElseThrow();
        reopen();
        Account before = lock.vault().account(first.id()).orElseThrow();
        int position = lock.vault().accounts().indexOf(before);
        CredentialDescriptor second = api.saveLogin(window, SITE, "u2", "new".toCharArray()).join().orElseThrow();
        assertThat(second.id()).as("the same login").isEqualTo(first.id());
        assertThat(second.subtitle()).isEqualTo("u2");
        assertThat(logins(SITE)).as("one login of that name").hasSize(1);
        Account after = lock.vault().account(first.id()).orElseThrow();
        assertThat(lock.vault().accounts().indexOf(after)).as("in its place").isEqualTo(position);
        assertThat(after.username()).isEqualTo("u2");
        assertThat(after.created()).isEqualTo(before.created());
        assertThat(after.updated()).isAfterOrEqualTo(before.updated());
        assertThat(lock.vault().grants().stream().filter(g -> g.credentialId().equals(first.id())).toList())
            .as("its grant kept").containsExactly(new Grant(SSH.id(), first.id()));
        try (Credential credential = api.credentialIfGranted(first.id()).orElseThrow()) { assertThat(credential.password()).isEqualTo("new".toCharArray()); }
        assertCopiesZeroed();
        reopen();
        assertThat(logins(SITE)).singleElement().satisfies(a -> assertThat(((Auth.Password) a.auth()).password()).isEqualTo("new".toCharArray()));
    }

    @Test void saveLoginReplacesTheLastOfItsLoginsOfTheSameName(@TempDir Path dir) {
        VaultApi api = saver(dir); populate();
        UUID older = UUID.randomUUID(), newer = UUID.randomUUID();
        for (UUID id : List.of(older, newer)) {
            lock.vault().accounts().add(new Account(id, SITE, "u", new Auth.Password("old".toCharArray()), Instant.EPOCH, Instant.EPOCH));
            lock.vault().grants().add(new Grant(SSH.id(), id));
        }
        lock.save().join();
        assertThat(api.saveLogin(window, SITE, "u", "new".toCharArray()).join()).map(CredentialDescriptor::id).contains(newer);
        assertThat(((Auth.Password) lock.vault().account(older).orElseThrow().auth()).password()).isEqualTo("old".toCharArray());
        assertThat(((Auth.Password) lock.vault().account(newer).orElseThrow().auth()).password()).isEqualTo("new".toCharArray());
    }

    @Test void saveLoginLeavesAnUngrantedLoginOfTheSameNameAlone(@TempDir Path dir) {
        VaultApi api = saver(dir); populate();
        UUID ungranted = UUID.randomUUID(), others = UUID.randomUUID(), keyLogin = UUID.randomUUID(), otherCase = UUID.randomUUID();
        lock.vault().accounts().add(new Account(ungranted, SITE, "u", new Auth.Password("mine".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().accounts().add(new Account(others, SITE, "u", new Auth.Password("theirs".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().grants().add(new Grant(OTHER.id(), others));
        lock.vault().accounts().add(new Account(keyLogin, SITE, "u", new Auth.Key(Path.of("/k"), null), Instant.EPOCH, Instant.EPOCH));
        lock.vault().grants().add(new Grant(SSH.id(), keyLogin));
        lock.vault().accounts().add(new Account(otherCase, SITE.toUpperCase(java.util.Locale.ROOT), "u", new Auth.Password("case".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().grants().add(new Grant(SSH.id(), otherCase));
        lock.save().join();
        CredentialDescriptor saved = api.saveLogin(window, SITE, "u", "new".toCharArray()).join().orElseThrow();
        assertThat(saved.id()).as("a second login").isNotIn(ungranted, others, keyLogin, otherCase);
        assertThat(logins(SITE)).hasSize(4);
        assertThat(((Auth.Password) lock.vault().account(ungranted).orElseThrow().auth()).password()).isEqualTo("mine".toCharArray());
        assertThat(((Auth.Password) lock.vault().account(others).orElseThrow().auth()).password()).isEqualTo("theirs".toCharArray());
        assertThat(((Auth.Password) lock.vault().account(otherCase).orElseThrow().auth()).password()).isEqualTo("case".toCharArray());
        assertThat(lock.vault().grants()).contains(new Grant(SSH.id(), saved.id())).doesNotContain(new Grant(SSH.id(), ungranted));
    }

    @Test void saveLoginLeavesASharedLoginOfTheSameNameAlone(@TempDir Path dir) {
        VaultApi api = saver(dir); populate();
        UUID shared = UUID.randomUUID();
        lock.vault().accounts().add(new Account(shared, SITE, "u", new Auth.Password("shared".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().grants().add(new Grant(SSH.id(), shared));
        lock.vault().grants().add(new Grant(OTHER.id(), shared));
        lock.save().join();
        CredentialDescriptor saved = api.saveLogin(window, SITE, "u", "new".toCharArray()).join().orElseThrow();
        assertThat(saved.id()).isNotEqualTo(shared);
        assertThat(((Auth.Password) lock.vault().account(shared).orElseThrow().auth()).password()).as("another plugin reads it too").isEqualTo("shared".toCharArray());
        assertThat(lock.vault().grants()).contains(new Grant(SSH.id(), shared), new Grant(OTHER.id(), shared), new Grant(SSH.id(), saved.id()));
        assertThat(logins(SITE)).hasSize(2);
    }

    @Test void saveLoginUnlocksFirstAndAnswersEmptyWhenCancelled(@TempDir Path dir) {
        VaultApi api = saver(dir); populate(); lock.lock();
        var owner = window();
        var cancelled = api.saveLogin(owner, SITE, "u", "pw".toCharArray());
        assertThat(unlocks).singleElement().satisfies(prompt -> assertThat(prompt.owner()).isSameAs(owner));
        assertThat(cancelled).isNotDone();
        unlocks.getFirst().cancel();
        assertThat(cancelled).isCompletedWithValue(Optional.empty());
        assertThat(notifications.posts()).as("no failure").isEmpty();
        var saving = api.saveLogin(owner, SITE, "u", "pw".toCharArray());
        assertThat(unlocks).hasSize(2);
        unlocks.getLast().submit("pw".toCharArray());
        assertThat(saving.join()).isPresent();
        assertThat(logins(SITE)).as("the cancelled save wrote nothing").hasSize(1);
        assertCopiesZeroed();
    }

    @Test void saveLoginZeroesItsCopyWhenCancelled(@TempDir Path dir) {
        VaultApi api = saver(dir); populate(); lock.lock();
        var saving = api.saveLogin(window, SITE, "u", "pw".toCharArray());
        boolean[] dismissed = {false};
        unlocks.getFirst().onDismiss(() -> dismissed[0] = true);
        assertThat(copies).singleElement().satisfies(copy -> assertThat(copy).isEqualTo("pw".toCharArray()));
        saving.cancel(false);
        assertCopiesZeroed();
        assertThat(dismissed[0]).as("its unlock prompt had no other waiter").isTrue();
        assertThat(service.forConsumer(OTHER).ensureUnlocked(window)).isNotDone();
        unlocks.getLast().submit("pw".toCharArray());
        assertThat(lock.state()).isEqualTo(LockState.UNLOCKED);
        assertThat(logins(SITE)).as("nothing written").isEmpty();
        reopen();
        assertThat(logins(SITE)).isEmpty();
    }

    @Test void aSaveCancelledBeforeItsWriteWritesNothing(@TempDir Path dir) {
        var work = new java.util.ArrayDeque<Runnable>(); boolean[] queued = {false};
        lockBackground = task -> { if (queued[0]) work.add(task); else task.run(); };
        VaultApi api = saver(dir); populate();
        queued[0] = true;
        lock.save();
        var saving = api.saveLogin(window, SITE, "u", "pw".toCharArray());
        saving.cancel(false);
        assertCopiesZeroed();
        assertThat(work).as("only the earlier write waits").hasSize(1);
        work.removeFirst().run();
        assertThat(work).as("the save queued no write").isEmpty();
        assertThat(logins(SITE)).isEmpty();
        queued[0] = false;
        reopen();
        assertThat(logins(SITE)).isEmpty();
    }

    @Test void aLockBeforeTheWriteAnswersEmpty(@TempDir Path dir) {
        var work = new java.util.ArrayDeque<Runnable>(); boolean[] queued = {false};
        lockBackground = task -> { if (queued[0]) work.add(task); else task.run(); };
        VaultApi api = saver(dir); populate();
        queued[0] = true;
        lock.save();
        var beforeItsDraft = api.saveLogin(window, SITE, "u", "pw".toCharArray());
        lock.lock();
        while (!work.isEmpty()) work.removeFirst().run();
        assertThat(beforeItsDraft).as("locked before the save started").isCompletedWithValue(Optional.empty());
        queued[0] = false; lock.unlock("pw".toCharArray()).join(); queued[0] = true;
        var duringItsWrite = api.saveLogin(window, SITE, "u", "pw".toCharArray());
        assertThat(work).as("the write waits").hasSize(1);
        lock.lock();
        work.removeFirst().run();
        assertThat(duringItsWrite).as("locked while it wrote").isCompletedWithValue(Optional.empty());
        assertThat(notifications.posts()).as("no failure").isEmpty();
        assertThat(copies).hasSize(2);
        assertCopiesZeroed();
    }

    @Test void saveLoginWithNoVaultPostsNoVaultYet(@TempDir Path dir) {
        VaultApi api = saver(dir);
        assertThat(api.saveLogin(window, SITE, "u", "pw".toCharArray())).isCompletedWithValue(Optional.empty());
        assertThat(notifications.lines()).containsExactly("dev.jasper.vault|WARNING|No vault yet|Create a vault in Credential Vault first (Open Vault...)");
        assertThat(unlocks).isEmpty();
        assertThat(lock.state()).isEqualTo(LockState.NO_VAULT);
        assertCopiesZeroed();
    }

    @Test void saveLoginFailsWhenTheWriteFails(@TempDir Path dir) throws IOException {
        VaultApi api = saver(dir); populate();
        java.nio.file.Files.createDirectories(dir.resolve("vault.jv.tmp"));
        var saving = api.saveLogin(window, SITE, "user-synthetic-7", "pw-synthetic-7".toCharArray());
        assertThat(saving).isCompletedExceptionally();
        Throwable failure = catchThrowable(saving::join).getCause();
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("Could not save the login").hasCauseInstanceOf(IOException.class);
        for (Throwable t = failure; t != null; t = t.getCause())
            assertThat(String.valueOf(t.getMessage())).doesNotContain("pw-synthetic-7", "user-synthetic-7", SITE);
        assertThat(logins(SITE)).isEmpty();
        assertThat(notifications.posts()).isEmpty();
        assertCopiesZeroed();
    }

    @Test void saveLoginCompletesOnTheUiThread(@TempDir Path dir) throws Exception {
        var background = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            lockBackground = background; lockUi = javax.swing.SwingUtilities::invokeLater;
            VaultApi api = saver(dir); populate();
            var onUi = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Boolean>>();
            javax.swing.SwingUtilities.invokeAndWait(() -> onUi.set(api.saveLogin(window, SITE, "u", "pw".toCharArray())
                .handle((saved, failure) -> failure == null && saved.isPresent() && javax.swing.SwingUtilities.isEventDispatchThread())));
            assertThat(onUi.get().get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally { background.shutdownNow(); }
    }

    @Test void credentialIfGrantedNeedsASavedGrantAndNeverPrompts(@TempDir Path dir) {
        VaultApi api = saver(dir);
        assertThat(api.credentialIfGranted(PROD)).as("no vault").isEmpty();
        assertThat(notifications.posts()).as("and no notice").isEmpty();
        populate();
        var pick = api.pick(window);
        picks.getFirst().choose(PROD);
        assertThat(pick.join()).isPresent();
        assertThat(api.credentialIfGranted(PROD)).as("a one-time grant is not used").isEmpty();
        var once = api.credential(PROD);
        assertThat(once).as("nor spent: the next fetch still uses it").isDone();
        try (Credential credential = once.join().orElseThrow()) { assertThat(credential.username()).contains("deploy"); }
        assertThat(api.credentialIfGranted(UUID.randomUUID())).as("unknown").isEmpty();
        assertThat(api.credentialIfGranted(null)).isEmpty();
        assertThat(api.credentialIfGranted(KEY)).as("ungranted").isEmpty();
        lock.vault().grants().add(new Grant(OTHER.id(), PROD));
        assertThat(api.credentialIfGranted(PROD)).as("another plugin's grant").isEmpty();
        lock.vault().grants().add(new Grant(SSH.id(), PROD));
        try (Credential a = api.credentialIfGranted(PROD).orElseThrow(); Credential b = api.credentialIfGranted(PROD).orElseThrow()) {
            assertThat(a.password()).isEqualTo("s3cret".toCharArray()).isNotSameAs(b.password());
        }
        lock.lock();
        assertThat(api.credentialIfGranted(PROD)).as("locked").isEmpty();
        assertThat(unlocks).as("never an unlock prompt").isEmpty();
        assertThat(grants).as("never a grant prompt").isEmpty();
        assertThat(picks).as("never a pick").hasSize(1);
        assertThat(notifications.posts()).isEmpty();
    }
}
