package dev.jasper.browser.logins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.CredentialPickRequest;
import dev.jasper.vault.api.CredentialPickResult;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A stand-in Credential Vault for the Browser's tests, which never start the Vault plugin or touch a vault file: a
 * {@link VaultApi} that records what it is asked, with switches for its lock state, the logins whose saved grant it
 * refuses, and the members an older Vault lacks, which throw {@link NoSuchMethodError}; a test may give the futures
 * its pick, fetch and save answer, or the failures its grant read and save throw. As a plugin it publishes that API
 * to each consumer, as the Vault does. Every {@link Credential} it hands out is kept, so a test can see it was closed.
 * Its {@code saveLogin}, {@code credentialIfGranted} and {@code openManager} require the UI thread, as the Vault's do
 * (L9).
 */
public final class FakeVault implements Plugin {
    /** The Vault's identity, which a consumer names in its optional {@code requires}. */
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.9.0", Set.of());
    /** Members an older Vault lacks: {@code pick}, {@code credential}, {@code saveLogin}, {@code credentialIfGranted}, {@code openManager}. */
    public final Set<String> missing = ConcurrentHashMap.newKeySet();
    /** Logins whose saved grant {@code credentialIfGranted} refuses. */
    public final Set<UUID> ungranted = ConcurrentHashMap.newKeySet();
    /** What it was asked, in order: {@code credential NAME}, {@code credentialIfGranted NAME}, {@code saveLogin NAME USER}. */
    public final List<String> calls = new CopyOnWriteArrayList<>();
    /** Each anchored pick's request and owner. */
    public final List<CredentialPickRequest> picks = new CopyOnWriteArrayList<>();
    public final List<WindowHandle> pickOwners = new CopyOnWriteArrayList<>(), fetchOwners = new CopyOnWriteArrayList<>(),
        saveOwners = new CopyOnWriteArrayList<>(), managerOwners = new CopyOnWriteArrayList<>();
    /** A copy of each password {@code saveLogin} was given, taken at the call. */
    public final List<String> savedPasswords = new CopyOnWriteArrayList<>();
    /** Every credential it handed out. */
    public final List<Credential> issued = new CopyOnWriteArrayList<>();
    /** Every consumer it was published to. */
    public final List<String> consumers = new CopyOnWriteArrayList<>();
    public volatile LockState state = LockState.UNLOCKED;
    /** What the next pick answers; null chooses the first login at once. */
    public volatile CompletableFuture<CredentialPickResult> pickAnswer;
    /** What the next fetch answers; null gives the credential at once. */
    public volatile CompletableFuture<Optional<Credential>> credentialAnswer;
    /** What the next save answers; null saves at once. */
    public volatile CompletableFuture<Optional<CredentialDescriptor>> saveAnswer;
    /** What {@code credentialIfGranted} throws, as a failing Vault would; null for none. */
    public volatile RuntimeException grantFailure;
    /** What {@code saveLogin} throws at the call, as a failing Vault would; null for none. */
    public volatile RuntimeException saveFailure;
    private final Map<UUID, CredentialDescriptor> descriptors = new ConcurrentHashMap<>();
    private final List<UUID> order = new CopyOnWriteArrayList<>();
    private final Map<UUID, Supplier<Credential>> secrets = new ConcurrentHashMap<>();

    /**
     * Adds a password login, after every other credential.
     *
     * @return its id
     */
    public UUID login(String name, String username, String password) {
        return add(name, username, Kind.ACCOUNT_PASSWORD, username, password);
    }

    /**
     * Adds an account that signs in with a key, so it has a username and no password.
     *
     * @return its id
     */
    public UUID keyAccount(String name, String username) {
        return add(name, username, Kind.ACCOUNT_KEY, username, null);
    }

    /**
     * Adds an account that signs in with a key and a password, an SSH account's, which has both a username and a
     * password.
     *
     * @return its id
     */
    public UUID keyAndPasswordAccount(String name, String username, String password) {
        return add(name, username, Kind.ACCOUNT_KEY_AND_PASSWORD, username, password);
    }

    /**
     * Adds an SSH key, with neither a username nor a password.
     *
     * @return its id
     */
    public UUID sshKey(String name) {
        return add(name, "SHA256:fake", Kind.SSH_KEY, null, null);
    }

    /** The descriptor of a credential added before. */
    public CredentialDescriptor descriptor(UUID id) {
        return descriptors.get(id);
    }

    /** A credential as the Vault hands one out, kept in {@link #issued}. */
    public Credential issue(UUID id) {
        Credential credential = secrets.get(id).get();
        issued.add(credential);
        return credential;
    }

    /** Whether {@code credential} was closed: a closed one's accessors throw. */
    public static boolean closed(Credential credential) {
        try {
            credential.id();
            return false;
        } catch (IllegalStateException closed) {
            return true;
        }
    }

    /**
     * The API as one consumer sees it.
     *
     * @return a new instance
     */
    public VaultApi api() {
        return new Api();
    }

    @Override public void start(PluginContext context) {
        context.services().publishPerConsumer(VaultApi.class, consumer -> {
            consumers.add(consumer.id());
            return api();
        });
    }

    private UUID add(String name, String subtitle, Kind kind, String username, String password) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, subtitle, kind));
        order.add(id);
        secrets.put(id, () -> new Credential(id, name, kind, username, password == null ? null : password.toCharArray(), null, null));
        return id;
    }

    private void lacks(String member) {
        if (missing.contains(member)) throw new NoSuchMethodError("dev.jasper.vault.api.VaultApi." + member);
    }

    private static void onUiThread() {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Call Credential Vault on the UI thread");
    }

    private final class Api implements VaultApi {
        @Override public LockState lockState() {
            return state;
        }

        @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) {
            return CompletableFuture.completedFuture(state == LockState.UNLOCKED);
        }

        @Override public List<CredentialDescriptor> credentials() {
            return state == LockState.UNLOCKED ? order.stream().map(descriptors::get).toList() : List.of();
        }

        @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
            throw new AssertionError("The Browser fetches with its owner window");
        }

        @Override public CompletableFuture<Optional<Credential>> credential(WindowHandle owner, UUID id) {
            lacks("credential");
            calls.add("credential " + descriptors.get(id).name());
            fetchOwners.add(owner);
            if (credentialAnswer != null) return credentialAnswer;
            return CompletableFuture.completedFuture(state == LockState.UNLOCKED ? Optional.of(issue(id)) : Optional.empty());
        }

        @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) {
            throw new AssertionError("The Browser picks on an anchor");
        }

        @Override public CompletableFuture<CredentialPickResult> pick(WindowHandle owner, CredentialPickRequest request) {
            lacks("pick");
            picks.add(request);
            pickOwners.add(owner);
            if (pickAnswer != null) return pickAnswer;
            return CompletableFuture.completedFuture(order.isEmpty() ? new CredentialPickResult.Cancelled()
                : new CredentialPickResult.Chosen(descriptors.get(order.getFirst())));
        }

        @Override public CompletableFuture<Optional<CredentialDescriptor>> saveLogin(WindowHandle owner, String name, String username,
                char[] password) {
            onUiThread();
            lacks("saveLogin");
            if (saveFailure != null) throw saveFailure;
            calls.add("saveLogin " + name + " " + username);
            saveOwners.add(owner);
            savedPasswords.add(new String(password));
            if (saveAnswer != null) return saveAnswer;
            UUID id = UUID.randomUUID();
            return CompletableFuture.completedFuture(Optional.of(new CredentialDescriptor(id, name, username, Kind.ACCOUNT_PASSWORD)));
        }

        @Override public Optional<Credential> credentialIfGranted(UUID id) {
            onUiThread();
            lacks("credentialIfGranted");
            if (grantFailure != null) throw grantFailure;
            calls.add("credentialIfGranted " + descriptors.get(id).name());
            if (state != LockState.UNLOCKED || ungranted.contains(id)) return Optional.empty();
            return Optional.of(issue(id));
        }

        @Override public void openManager(WindowHandle owner) {
            onUiThread();
            lacks("openManager");
            managerOwners.add(owner);
        }
    }
}
