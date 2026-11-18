package dev.jasper.browser.logins;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.CredentialPickRequest;
import dev.jasper.vault.api.CredentialPickResult;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * The Browser's one way to the Credential Vault, an optional plugin, and its only class that names the Vault's API
 * ({@code dev.jasper.vault.api}): the plugin {@linkplain #find finds} it once at its start, and without the Vault none of
 * the Browser's login parts appear. It loads and links without the Vault's classes, so naming it costs nothing then.
 * A Vault older than 0.9.0, which lacks members this calls, answers nothing chosen, nothing filled and not saved, and
 * its picker opens without the starting search. A login's username and password reach the caller's {@code use} while
 * its credential is open; the password is the credential's own array, zeroed as the credential closes when {@code use}
 * returns, so a caller that keeps it copies it. A failure is logged with the exception alone, never a name, a username
 * or a password. UI thread, where the Vault is called and completes its futures.
 */
public final class VaultLogins {
    private static final System.Logger LOG = System.getLogger(VaultLogins.class.getName());
    private final VaultApi vault;
    private final BiFunction<UiAnchor, String, CredentialPickRequest> requests;

    /** Logins from {@code vault}, whose picker gets a request with the starting search. */
    VaultLogins(VaultApi vault) {
        this(vault, (anchor, search) -> new CredentialPickRequest(anchor, Optional.empty(), false, search));
    }

    /**
     * Logins from {@code vault}, whose picker's request with the starting search {@code requests} makes: a test's
     * throws {@link NoSuchMethodError}, as a Vault older than 0.9.0 does.
     */
    VaultLogins(VaultApi vault, BiFunction<UiAnchor, String, CredentialPickRequest> requests) {
        this.vault = Objects.requireNonNull(vault, "vault");
        this.requests = Objects.requireNonNull(requests, "requests");
    }

    /**
     * The Vault's logins, when the Vault plugin is present and gives the Browser its service. Without the plugin its
     * API's classes cannot load, which reads as absent.
     *
     * @param context the plugin's context, while it starts
     * @return the logins; empty without the Vault
     */
    public static Optional<VaultLogins> find(PluginContext context) {
        try {
            return context.services().find(VaultApi.class).map(VaultLogins::new);
        } catch (NoClassDefFoundError absent) {
            return Optional.empty();
        }
    }

    /**
     * Lets the user choose a login in the Vault's picker on {@code anchor}, opening with {@code search} (the picker of a
     * Vault older than 0.9.0 opens without it), unlocking first when needed. A chosen credential is always fetched over
     * {@code owner}, so the one-time grant the choice gave is spent, and closed; only a password login, a site's username
     * and password, is used: {@code use} gets its username, or {@code ""}, and its password, or an empty array, before
     * it closes. The Vault's picker lists every kind of credential, so an SSH key or an account that signs in with a key
     * is closed unused. Cancelling the returned future withdraws the pick and the fetch; a login that arrives after
     * that is closed unused.
     *
     * @param owner the window the request comes from
     * @param anchor where the picker attaches, a live anchor Jasper issued
     * @param search the picker's starting search
     * @param use what fills the login in: the username and the credential's own password array, zeroed once it returns
     * @return true once a login was used; false when nothing was chosen (the picker was dismissed, None chosen, the
     *     anchor or the owner closed, there is no vault, unlocking was cancelled), the Vault gave no login, the user
     *     chose a credential that is not a password login, the Vault lacks what this asks, or the pick or the fetch
     *     failed, which is logged; fails as {@code use} fails
     */
    public CompletableFuture<Boolean> pick(WindowHandle owner, UiAnchor anchor, String search, BiConsumer<String, char[]> use) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(use, "use");
        CompletableFuture<CredentialPickResult> picking;
        try {
            picking = vault.pick(owner, request(anchor, search));
        } catch (NoSuchMethodError older) {
            return CompletableFuture.completedFuture(false);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The Credential Vault could not show its picker", failure);
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        var fetching = new AtomicReference<CompletableFuture<Optional<Credential>>>();
        result.whenComplete((used, failure) -> {
            if (!result.isCancelled()) return;
            picking.cancel(false);
            CompletableFuture<Optional<Credential>> fetch = fetching.get();
            if (fetch != null) fetch.cancel(false);
        });
        picking.whenComplete((answer, failure) -> {
            if (failure != null) failed(result, failure, "The Credential Vault's picker failed");
            else if (answer instanceof CredentialPickResult.Chosen chosen) fetch(owner, chosen.credential().id(), result, fetching, use);
            else result.complete(false);
        });
        return result;
    }

    /**
     * Fills a remembered login in without any prompt: while the vault is unlocked, the newest password login named
     * exactly {@code name} that the Vault lets the Browser read without asking, as the Browser's own saved logins are;
     * {@code use} gets its username, or {@code ""}, and its password, or an empty array, before it closes.
     *
     * @param name the login's name, as {@link LoginName} makes it
     * @param use what fills the login in: the username and the credential's own password array, zeroed once it returns
     * @return true once a login was used; false while locked, without a vault, without such a login, or when the Vault
     *     lacks what this asks
     * @throws IllegalStateException off the UI thread, as the Vault refuses it
     */
    public boolean useGranted(String name, BiConsumer<String, char[]> use) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(use, "use");
        if (vault.lockState() != LockState.UNLOCKED) return false;
        List<CredentialDescriptor> credentials = vault.credentials();
        for (int at = credentials.size() - 1; at >= 0; at--) {
            CredentialDescriptor login = credentials.get(at);
            if (login.kind() != Kind.ACCOUNT_PASSWORD || !login.name().equals(name)) continue;
            Optional<Credential> granted;
            try {
                granted = vault.credentialIfGranted(login.id());
            } catch (NoSuchMethodError older) {
                return false;
            }
            if (granted.isEmpty()) continue;
            Credential credential = granted.get();
            try {
                use(credential, use);
            } finally {
                credential.close();
            }
            return true;
        }
        return false;
    }

    /**
     * Saves a login in the Vault, unlocking over {@code owner} first when needed, with a saved grant for the Browser,
     * replacing the Browser's own login of that name; the Vault keeps its own copy of the password, so the caller still
     * zeroes {@code password}. Cancelling the returned future withdraws the save.
     *
     * @param owner the window the unlock prompt opens over
     * @param name the login's name, as {@link LoginName} makes it
     * @param username its username, which may be empty
     * @param password its password, which may be empty
     * @return true once saved; false when unlocking was cancelled, the owner closed, there is no vault or the Vault
     *     lacks what this asks; fails as the Vault's save fails ("Could not save the login")
     * @throws IllegalArgumentException for a blank name or a null username or password, as the Vault refuses them
     * @throws IllegalStateException off the UI thread, as the Vault refuses it
     */
    public CompletableFuture<Boolean> save(WindowHandle owner, String name, String username, char[] password) {
        CompletableFuture<Optional<CredentialDescriptor>> saving;
        try {
            saving = vault.saveLogin(owner, name, username, password);
        } catch (NoSuchMethodError older) {
            return CompletableFuture.completedFuture(false);
        }
        var result = new CompletableFuture<Boolean>();
        result.whenComplete((saved, failure) -> {
            if (result.isCancelled()) saving.cancel(false);
        });
        saving.whenComplete((saved, failure) -> {
            if (failure != null) result.completeExceptionally(unwrapped(failure));
            else result.complete(saved.isPresent());
        });
        return result;
    }

    /**
     * Whether the vault is unlocked now, so a remembered login may fill in without a prompt.
     *
     * @return true while unlocked
     */
    public boolean unlocked() {
        return vault.lockState() == LockState.UNLOCKED;
    }

    /**
     * Opens the Credential Vault manager over {@code owner}, creating a vault or unlocking first as Open Vault... does;
     * nothing for a Vault that lacks it, or a null or closed owner.
     *
     * @param owner the window the manager opens over
     * @throws IllegalStateException off the UI thread, as the Vault refuses it
     */
    public void openManager(WindowHandle owner) {
        try {
            vault.openManager(owner);
        } catch (NoSuchMethodError older) {
            // A Vault older than 0.9.0 opens no manager for another plugin.
        }
    }

    /** The picker's request with the starting search, or without it from a Vault older than 0.9.0. */
    private CredentialPickRequest request(UiAnchor anchor, String search) {
        try {
            return requests.apply(anchor, search);
        } catch (NoSuchMethodError older) {
            return new CredentialPickRequest(anchor, Optional.empty(), false);
        }
    }

    /**
     * Fetches the chosen credential over {@code owner} and, when it is a password login, hands it to {@code use} while
     * {@code result} waits; any other kind, and one that arrives once {@code result} is done, is closed unused.
     */
    private void fetch(WindowHandle owner, UUID id, CompletableFuture<Boolean> result,
                       AtomicReference<CompletableFuture<Optional<Credential>>> fetching, BiConsumer<String, char[]> use) {
        CompletableFuture<Optional<Credential>> fetch;
        try {
            fetch = vault.credential(owner, id);
        } catch (NoSuchMethodError older) {
            result.complete(false);
            return;
        } catch (RuntimeException failure) {
            failed(result, failure, "The Credential Vault could not give the chosen login");
            return;
        }
        fetching.set(fetch);
        fetch.whenComplete((credential, failure) -> {
            if (failure != null) {
                failed(result, failure, "The Credential Vault could not give the chosen login");
                return;
            }
            if (credential.isEmpty()) {
                result.complete(false);
                return;
            }
            Credential login = credential.get();
            try {
                if (result.isDone()) return;
                // L12, L14: a site's login is a username and password; an SSH key or an SSH account fills nothing.
                if (login.kind() != Kind.ACCOUNT_PASSWORD) {
                    result.complete(false);
                    return;
                }
                use(login, use);
                result.complete(true);
            } catch (Throwable thrown) {
                result.completeExceptionally(thrown);
            } finally {
                login.close();
            }
        });
    }

    /** Hands {@code credential}'s username, or {@code ""}, and its own password array, or an empty one, to {@code use}. */
    private static void use(Credential credential, BiConsumer<String, char[]> use) {
        char[] password = credential.password();
        use.accept(credential.username().orElse(""), password == null ? new char[0] : password);
    }

    /**
     * A pick or a fetch failed: nothing was chosen, and the failure is logged with the exception alone, unless the
     * caller has gone or the Vault withdrew the request.
     */
    private static void failed(CompletableFuture<Boolean> result, Throwable failure, String what) {
        Throwable cause = unwrapped(failure);
        if (!result.isDone() && !(cause instanceof CancellationException)) LOG.log(System.Logger.Level.WARNING, what, cause);
        result.complete(false);
    }

    private static Throwable unwrapped(Throwable failure) {
        return failure instanceof CompletionException wrapped && wrapped.getCause() != null ? wrapped.getCause() : failure;
    }
}
