package dev.jasper.browser.logins;

import dev.jasper.browser.store.CapturedLog;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.CredentialPickRequest;
import dev.jasper.vault.api.CredentialPickResult;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.awt.EventQueue;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan 8 Task 3: {@link VaultLogins}, the Browser's one way to the Credential Vault, over {@link FakeVault}. A pick
 * asks the Vault's picker on the anchor with the starting search, always fetches the chosen login (F15) and hands its
 * username and password to the caller before the credential closes; a silent fill takes the newest login of the name
 * the Vault lets the Browser read without a prompt (F11); a save forwards and is withdrawn with its future (F10); and
 * a Vault older than 0.9.0 answers nothing chosen, nothing filled and not saved (F20). Run on the UI thread, where the
 * Vault is called.
 */
class VaultLoginsTest {
    private static final String NAME = "https://intranet.example (Corp)";
    private final FakeVault fake = new FakeVault();
    private final VaultLogins logins = new VaultLogins(fake.api());
    private final WindowHandle owner = new Window();
    private final UiAnchor anchor = new Anchor();
    /**
     * What each {@code use} saw: the username and the password as a string while it was handed over, its array, and
     * whether the credential it came from was still open.
     */
    private final List<String> seen = new CopyOnWriteArrayList<>();
    private final List<char[]> handed = new CopyOnWriteArrayList<>();
    private final List<Boolean> openWhileUsed = new CopyOnWriteArrayList<>();
    private final BiConsumer<String, char[]> use = (username, password) -> {
        seen.add(username);
        seen.add(new String(password));
        handed.add(password);
        openWhileUsed.add(!fake.issued.isEmpty() && !FakeVault.closed(fake.issued.getLast()));
    };

    @Test void aPickPassesTheAnchorAndSearchAndUsesTheChosenLoginBeforeClosingIt() throws Exception {
        onUi(() -> {
            fake.login(NAME, "u", "pw");
            CompletableFuture<Boolean> picked = logins.pick(owner, anchor, "intranet.example", use);
            assertThat(picked).isCompletedWithValue(true);
            assertThat(fake.picks).singleElement().satisfies(request -> {
                assertThat(request.anchor()).isSameAs(anchor);
                assertThat(request.search()).isEqualTo("intranet.example");
                assertThat(request.current()).isEmpty();
                assertThat(request.allowClear()).as("no None row").isFalse();
            });
            assertThat(fake.pickOwners).containsExactly(owner);
            assertThat(fake.fetchOwners).as("fetched over the same window").containsExactly(owner);
            assertThat(seen).containsExactly("u", "pw");
            assertThat(openWhileUsed).as("used before the credential closed").containsExactly(true);
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).as("closed after").isTrue());
            assertThat(handed.getFirst()).as("the credential's own array, zeroed as it closed").containsOnly((char) 0);
        });
    }

    @Test void aCancelledOrClearedPickAnswersFalseAndFetchesNothing() throws Exception {
        onUi(() -> {
            fake.login(NAME, "u", "pw");
            fake.pickAnswer = CompletableFuture.completedFuture(new CredentialPickResult.Cancelled());
            assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
            fake.pickAnswer = CompletableFuture.completedFuture(new CredentialPickResult.Cleared());
            assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
            assertThat(fake.calls).as("nothing fetched").isEmpty();
            assertThat(seen).isEmpty();
        });
    }

    @Test void aChosenLoginTheVaultDoesNotGiveAnswersFalse() throws Exception {
        onUi(() -> {
            fake.login(NAME, "u", "pw");
            fake.credentialAnswer = CompletableFuture.completedFuture(Optional.empty());
            assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
            assertThat(fake.calls).containsExactly("credential " + NAME);
            assertThat(seen).isEmpty();
        });
    }

    /** The plan's text: the username and the password, each empty when the credential has none. */
    @Test void aChosenAccountWithoutAPasswordIsUsedWithAnEmptyOne() throws Exception {
        onUi(() -> {
            fake.keyAccount("deploy key", "deploy");
            assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(true);
            assertThat(seen).containsExactly("deploy", "");
            assertThat(fake.issued).allMatch(FakeVault::closed);
        });
    }

    /** F20: a Vault older than 0.9.0 has no four-argument request, so its picker opens without the starting search. */
    @Test void aPickFromAnOlderVaultFallsBackToTheThreeArgumentRequest() throws Exception {
        onUi(() -> {
            fake.login(NAME, "u", "pw");
            var older = new VaultLogins(fake.api(), (anchor, search) -> {
                throw new NoSuchMethodError("dev.jasper.vault.api.CredentialPickRequest.<init>");
            });
            assertThat(older.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(true);
            assertThat(fake.picks).singleElement().satisfies(request -> {
                assertThat(request.anchor()).isSameAs(anchor);
                assertThat(request.search()).as("no starting search").isEmpty();
                assertThat(request.current()).isEmpty();
                assertThat(request.allowClear()).isFalse();
            });
            assertThat(seen).containsExactly("u", "pw");
        });
    }

    /**
     * F15: a chosen login is always fetched, so the pick's one-time grant is spent, and closed: here the pick answers
     * Chosen after its caller has gone, from a Vault whose pick cannot be withdrawn, so the login is closed unused.
     */
    @Test void aChosenLoginIsAlwaysFetchedAndClosed() throws Exception {
        onUi(() -> {
            UUID id = fake.login(NAME, "u", "pw");
            var answer = new Unwithdrawable<CredentialPickResult>();
            fake.pickAnswer = answer;
            CompletableFuture<Boolean> picked = logins.pick(owner, anchor, "intranet.example", use);
            picked.cancel(false);
            assertThat(answer.cancels).as("the pick was withdrawn").isEqualTo(1);
            answer.complete(new CredentialPickResult.Chosen(fake.descriptor(id)));
            assertThat(fake.calls).as("fetched all the same").containsExactly("credential " + NAME);
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
            assertThat(seen).as("used by nobody").isEmpty();
            assertThat(picked).isCancelled();
        });
    }

    @Test void cancellingThePickCancelsThePickAndThenTheFetch() throws Exception {
        onUi(() -> {
            UUID id = fake.login(NAME, "u", "pw");
            fake.pickAnswer = new CompletableFuture<>();
            CompletableFuture<Boolean> picking = logins.pick(owner, anchor, "intranet.example", use);
            picking.cancel(false);
            assertThat(fake.pickAnswer).as("the Vault's pick").isCancelled();
            assertThat(fake.calls).isEmpty();

            fake.pickAnswer = CompletableFuture.completedFuture(new CredentialPickResult.Chosen(fake.descriptor(id)));
            fake.credentialAnswer = new CompletableFuture<>();
            CompletableFuture<Boolean> fetching = logins.pick(owner, anchor, "intranet.example", use);
            assertThat(fetching).isNotDone();
            fetching.cancel(false);
            assertThat(fake.credentialAnswer).as("the Vault's fetch").isCancelled();
            assertThat(seen).isEmpty();
        });
    }

    /** A credential that arrives after the cancel, from a Vault whose fetch cannot be withdrawn, is closed unused. */
    @Test void aCredentialThatArrivesAfterTheCancelIsClosedUnused() throws Exception {
        onUi(() -> {
            UUID id = fake.login(NAME, "u", "pw");
            var answer = new Unwithdrawable<Optional<Credential>>();
            fake.credentialAnswer = answer;
            CompletableFuture<Boolean> picked = logins.pick(owner, anchor, "intranet.example", use);
            picked.cancel(false);
            assertThat(answer.cancels).isEqualTo(1);
            Credential late = fake.issue(id);
            answer.complete(Optional.of(late));
            assertThat(FakeVault.closed(late)).isTrue();
            assertThat(seen).isEmpty();
        });
    }

    /** F29: a pick or a fetch that fails answers nothing chosen, logged with the exception alone. */
    @Test void aFailedPickOrFetchAnswersFalseAndIsLoggedWithTheExceptionAlone() throws Exception {
        try (var log = new CapturedLog(VaultLogins.class.getName())) {
            onUi(() -> {
                fake.login(NAME, "u", "pw");
                var refused = new IllegalStateException("refused");
                fake.pickAnswer = CompletableFuture.failedFuture(refused);
                assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
                fake.pickAnswer = null;
                var unreadable = new IllegalStateException("unreadable");
                fake.credentialAnswer = CompletableFuture.failedFuture(unreadable);
                assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
                assertThat(log.records()).extracting(LogRecord::getThrown).containsExactly(refused, unreadable);
            });
            assertThat(log.records()).allSatisfy(record -> assertThat(record.getMessage()).doesNotContain("intranet", "pw", NAME));
            assertThat(seen).isEmpty();
        }
    }

    /** A Vault that throws as the pick is asked, as for an anchor that is no longer live, answers nothing chosen. */
    @Test void aPickTheVaultRefusesAtOnceAnswersFalse() throws Exception {
        try (var log = new CapturedLog(VaultLogins.class.getName())) {
            onUi(() -> {
                var thrower = new VaultLogins(new ForwardingVault(fake.api()) {
                    @Override public CompletableFuture<CredentialPickResult> pick(WindowHandle owner, CredentialPickRequest request) {
                        throw new IllegalArgumentException("not a live anchor");
                    }
                });
                assertThat(thrower.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
            });
            assertThat(log.records()).singleElement().satisfies(record ->
                assertThat(record.getThrown()).isInstanceOf(IllegalArgumentException.class));
        }
    }

    /** F11: the newest login of the name, as the Vault appends a login it saves. */
    @Test void useGrantedTakesTheNewestOfTheName() throws Exception {
        onUi(() -> {
            fake.login(NAME, "old", "pw1");
            fake.login("https://other.example", "x", "y");
            fake.login(NAME, "new", "pw2");
            assertThat(logins.useGranted(NAME, use)).isTrue();
            assertThat(seen).containsExactly("new", "pw2");
            assertThat(openWhileUsed).containsExactly(true);
            assertThat(fake.calls).containsExactly("credentialIfGranted " + NAME);
            assertThat(fake.issued).singleElement().satisfies(credential -> assertThat(FakeVault.closed(credential)).isTrue());
            assertThat(handed.getFirst()).containsOnly((char) 0);
        });
    }

    @Test void useGrantedSkipsASameNamedSshKeyAndALoginTheVaultRefuses() throws Exception {
        onUi(() -> {
            fake.login(NAME, "granted", "pw");
            UUID refused = fake.login(NAME, "refused", "pw");
            fake.ungranted.add(refused);
            fake.sshKey(NAME);
            fake.keyAccount(NAME, "keyed");
            assertThat(logins.useGranted(NAME, use)).isTrue();
            assertThat(seen).containsExactly("granted", "pw");
            assertThat(fake.calls).as("only password logins, newest first").containsExactly("credentialIfGranted " + NAME, "credentialIfGranted " + NAME);
            assertThat(fake.issued).allMatch(FakeVault::closed);
        });
    }

    @Test void useGrantedAnswersFalseWhileLockedWithoutAVaultOrWithoutALoginOfTheName() throws Exception {
        onUi(() -> {
            UUID id = fake.login(NAME, "u", "pw");
            assertThat(logins.useGranted("https://intranet.example", use)).as("another name").isFalse();
            assertThat(logins.useGranted(NAME.toUpperCase(java.util.Locale.ROOT), use)).as("exactly the name").isFalse();
            fake.state = LockState.LOCKED;
            assertThat(logins.useGranted(NAME, use)).isFalse();
            fake.state = LockState.NO_VAULT;
            assertThat(logins.useGranted(NAME, use)).isFalse();
            assertThat(fake.calls).as("nothing asked while locked").isEmpty();
            fake.state = LockState.UNLOCKED;
            fake.ungranted.add(id);
            assertThat(logins.useGranted(NAME, use)).as("refused").isFalse();
            assertThat(seen).isEmpty();
        });
    }

    @Test void saveForwardsAndAnswersWhetherTheLoginWasSaved() throws Exception {
        onUi(() -> {
            char[] password = "pw".toCharArray();
            assertThat(logins.save(owner, NAME, "u", password)).isCompletedWithValue(true);
            assertThat(fake.calls).containsExactly("saveLogin " + NAME + " u");
            assertThat(fake.saveOwners).containsExactly(owner);
            assertThat(fake.savedPasswords).containsExactly("pw");
            assertThat(password).as("the caller zeroes its own array").containsExactly('p', 'w');

            fake.saveAnswer = CompletableFuture.completedFuture(Optional.empty());
            assertThat(logins.save(owner, NAME, "u", password)).as("unlocking cancelled, or no vault").isCompletedWithValue(false);

            var failure = new IllegalStateException("Could not save the login");
            fake.saveAnswer = CompletableFuture.failedFuture(failure);
            CompletableFuture<Boolean> failed = logins.save(owner, NAME, "u", password);
            assertThat(failed).isCompletedExceptionally();
            assertThatThrownBy(failed::join).hasCause(failure);
        });
    }

    /** F10: cancelling the save withdraws the Vault's, which then zeroes its copy and writes nothing. */
    @Test void cancellingTheSaveCancelsTheVaults() throws Exception {
        onUi(() -> {
            fake.saveAnswer = new CompletableFuture<>();
            CompletableFuture<Boolean> saving = logins.save(owner, NAME, "u", "pw".toCharArray());
            assertThat(saving).isNotDone();
            saving.cancel(false);
            assertThat(fake.saveAnswer).isCancelled();
        });
    }

    @Test void openManagerAndUnlockedForward() throws Exception {
        onUi(() -> {
            logins.openManager(owner);
            assertThat(fake.managerOwners).containsExactly(owner);
            assertThat(logins.unlocked()).isTrue();
            fake.state = LockState.LOCKED;
            assertThat(logins.unlocked()).isFalse();
            fake.state = LockState.NO_VAULT;
            assertThat(logins.unlocked()).isFalse();
        });
    }

    /** F20: a Vault older than 0.9.0 answers nothing chosen, nothing filled and not saved, and opens no manager. */
    @Test void anOlderVaultAnswersNothingChosenNothingFilledAndNotSaved() throws Exception {
        onUi(() -> {
            fake.login(NAME, "u", "pw");
            fake.missing.addAll(Set.of("pick", "saveLogin", "credentialIfGranted", "openManager"));
            assertThat(logins.pick(owner, anchor, "intranet.example", use)).isCompletedWithValue(false);
            assertThat(logins.useGranted(NAME, use)).isFalse();
            assertThat(logins.save(owner, NAME, "u", "pw".toCharArray())).isCompletedWithValue(false);
            logins.openManager(owner);
            assertThat(fake.managerOwners).isEmpty();
            fake.missing.clear();
            fake.missing.add("credential");
            assertThat(logins.pick(owner, anchor, "intranet.example", use)).as("no owned fetch").isCompletedWithValue(false);
            assertThat(seen).isEmpty();
            assertThat(fake.savedPasswords).isEmpty();
        });
    }

    /** The lookup finds the Vault only when the Vault plugin publishes its service to the Browser. */
    @Test void theLookupFindsTheVaultOnlyWhenItIsPresent() throws Exception {
        onUi(() -> {
            try (var host = new FakePluginHost()) {
                var withVault = new AtomicReference<Optional<VaultLogins>>();
                var without = new AtomicReference<Optional<VaultLogins>>();
                host.start(new PluginInfo("dev.example.alone", "Alone", "1.0.0", Set.of()), Set.of(), Set.of(FakeVault.INFO.id()),
                    context -> without.set(VaultLogins.find(context)));
                host.start(FakeVault.INFO, Set.of(), Set.of(), fake);
                host.start(new PluginInfo("dev.example.consumer", "Consumer", "1.0.0", Set.of()), Set.of(), Set.of(FakeVault.INFO.id()),
                    context -> withVault.set(VaultLogins.find(context)));
                assertThat(without.get()).isEmpty();
                assertThat(withVault.get()).isPresent();
                assertThat(fake.consumers).containsExactly("dev.example.consumer");
                assertThat(host.failures()).isEmpty();
            }
        });
    }

    /**
     * F20: without the Vault plugin its API's classes cannot load. {@code VaultLogins} still loads and links in a loader
     * that has the Browser and the SDK but not the Vault, and its lookup answers empty, so the Browser's start, which
     * names it, never needs the Vault.
     */
    @Test void withoutTheVaultsClassesVaultLoginsLinksAndTheLookupFindsNothing() throws Exception {
        URL browser = VaultLogins.class.getProtectionDomain().getCodeSource().getLocation();
        URL sdk = PluginContext.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[] {browser, sdk}, ClassLoader.getPlatformClassLoader())) {
            assertThatThrownBy(() -> loader.loadClass(VaultApi.class.getName())).as("the loader has no Vault").isInstanceOf(ClassNotFoundException.class);
            Class<?> isolated = Class.forName(VaultLogins.class.getName(), true, loader);
            assertThat(isolated.getClassLoader()).isSameAs(loader);
            Class<?> contextType = loader.loadClass(PluginContext.class.getName()), servicesType = loader.loadClass(Services.class.getName());
            var asked = new ArrayList<String>();
            Object services = Proxy.newProxyInstance(loader, new Class<?>[] {servicesType}, (proxy, method, arguments) -> {
                asked.add(method.getName());
                return Optional.empty();
            });
            Object context = Proxy.newProxyInstance(loader, new Class<?>[] {contextType}, (proxy, method, arguments) -> {
                if (method.getName().equals("services")) return services;
                throw new AssertionError("VaultLogins asked the context for " + method.getName());
            });
            assertThat(isolated.getMethod("find", contextType).invoke(null, context)).isEqualTo(Optional.empty());
            assertThat(asked).as("the Vault's class failed to load before the services were asked").isEmpty();
        }
    }

    /** Runs {@code check} on the UI thread, where the Vault is called, and rethrows what it threw. */
    private static void onUi(Runnable check) throws Exception {
        var thrown = new AtomicReference<Throwable>();
        EventQueue.invokeAndWait(() -> {
            try {
                check.run();
            } catch (Throwable failure) {
                thrown.set(failure);
            }
        });
        if (thrown.get() instanceof Error error) throw error;
        if (thrown.get() instanceof Exception exception) throw exception;
    }

    /** A Vault future that cannot be withdrawn: cancelling it is counted and does nothing. */
    private static final class Unwithdrawable<T> extends CompletableFuture<T> {
        int cancels;

        @Override public boolean cancel(boolean interrupt) {
            cancels++;
            return false;
        }
    }

    /** A Vault that forwards to another, for a test to override one member. */
    private static class ForwardingVault implements VaultApi {
        private final VaultApi to;

        ForwardingVault(VaultApi to) {
            this.to = to;
        }

        @Override public LockState lockState() { return to.lockState(); }
        @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) { return to.ensureUnlocked(owner); }
        @Override public List<CredentialDescriptor> credentials() { return to.credentials(); }
        @Override public CompletableFuture<Optional<Credential>> credential(UUID id) { return to.credential(id); }
        @Override public CompletableFuture<Optional<Credential>> credential(WindowHandle owner, UUID id) { return to.credential(owner, id); }
        @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) { return to.pick(owner); }
        @Override public CompletableFuture<CredentialPickResult> pick(WindowHandle owner, CredentialPickRequest request) { return to.pick(owner, request); }
    }

    /** An open window the requests come from. */
    private static final class Window implements WindowHandle {
        private final UUID id = UUID.randomUUID();

        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() { return List.of(); }
        @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
        @Override public boolean isActive() { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public void toFront() { }
    }

    /** A live anchor the Vault's picker would attach to. */
    private static final class Anchor implements UiAnchor {
        @Override public boolean isOpen() { return true; }
        @Override public Subscription onClosed(Runnable handler) { return () -> { }; }
        @Override public void close() { }
    }
}
