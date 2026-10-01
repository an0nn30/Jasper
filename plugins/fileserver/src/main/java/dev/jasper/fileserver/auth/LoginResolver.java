package dev.jasper.fileserver.auth;

import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Resolves a server's login just before it binds. UI thread; the future completes on the UI thread (Vault futures
 * do) or at once. A Vault request may prompt to unlock and to allow File Server, over {@code owner} when given.
 */
public final class LoginResolver {
    static final String NO_VAULT = "Install or enable the Credential Vault to use a saved account";
    static final String MISSING = "Vault account no longer exists — edit the server";
    static final String NOT_PROVIDED = "The Vault did not provide the account";

    private final Optional<VaultApi> vault;
    private final Supplier<String> user;
    private final Supplier<String> passwords;

    public LoginResolver(Optional<VaultApi> vault, Supplier<String> user, Supplier<String> passwords) {
        this.vault = vault; this.user = user; this.passwords = passwords;
    }

    public boolean vaultPresent() { return vault.isPresent(); }

    /** Whether starting this server now would have to unlock the Vault. */
    public boolean needsUnlock(ServerDefinition definition) {
        return definition.login() instanceof Login.Vault && vault.isPresent() && vault.get().lockState() != LockState.UNLOCKED;
    }

    public CompletableFuture<ResolvedLogin> resolve(ServerDefinition definition, WindowHandle owner) {
        return switch (definition.login()) {
            case Login.Anonymous anonymous -> CompletableFuture.completedFuture(ResolvedLogin.anonymous());
            case Login.Session session -> CompletableFuture.completedFuture(ResolvedLogin.password(user.get(), passwords.get().toCharArray()));
            case Login.Vault saved -> fromVault(saved.credentialId(), owner);
        };
    }

    private CompletableFuture<ResolvedLogin> fromVault(UUID id, WindowHandle owner) {
        if (vault.isEmpty()) return CompletableFuture.failedFuture(new LoginUnavailableException(NO_VAULT));
        VaultApi api = vault.get();
        CompletableFuture<Optional<Credential>> request;
        try { request = owner == null ? api.credential(id) : api.credential(owner, id); }
        catch (RuntimeException failure) { return CompletableFuture.failedFuture(new LoginUnavailableException(NOT_PROVIDED)); }
        return request.thenApply(found -> {
            if (found.isEmpty()) {
                boolean gone = api.lockState() == LockState.UNLOCKED && api.credentials().stream().noneMatch(d -> d.id().equals(id));
                throw new CompletionException(new LoginUnavailableException(gone ? MISSING : NOT_PROVIDED));
            }
            try (Credential credential = found.get()) {
                char[] password = credential.password();
                Optional<String> name = credential.username().filter(text -> !text.isBlank());
                if (password == null || name.isEmpty())
                    throw new CompletionException(new LoginUnavailableException("The Vault account '" + credential.name() + "' has no user name and password"));
                return ResolvedLogin.password(name.get(), password);
            }
        });
    }
}
