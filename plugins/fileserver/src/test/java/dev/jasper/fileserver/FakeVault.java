package dev.jasper.fileserver;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A stand-in Vault: every credential granted; {@code pick} answers the first descriptor. */
public final class FakeVault implements Plugin {
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.3.0", Set.of());
    public final Map<UUID, CredentialDescriptor> descriptors = new LinkedHashMap<>();
    public final Map<UUID, Supplier<Credential>> secrets = new LinkedHashMap<>();
    public LockState state = LockState.UNLOCKED;
    /** A locked Vault asked for a secret unlocks first, as the real one prompts. */
    public boolean unlockOnRequest;
    private PluginContext context;

    public UUID password(String name, String username, String password) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, username, Kind.ACCOUNT_PASSWORD));
        secrets.put(id, () -> new Credential(id, name, Kind.ACCOUNT_PASSWORD, username, password.toCharArray(), null, null));
        return id;
    }

    public UUID key(String name) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, "SHA256:x", Kind.SSH_KEY));
        secrets.put(id, () -> new Credential(id, name, Kind.SSH_KEY, null, null, null, null));
        return id;
    }

    public void unlock() {
        state = LockState.UNLOCKED;
        if (context != null) context.events().publish(VaultApi.LOCK_STATE_CHANGED, LockState.UNLOCKED);
    }

    public VaultApi api() {
        return new VaultApi() {
            @Override public LockState lockState() { return state; }
            @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) {
                if (state != LockState.UNLOCKED && unlockOnRequest) state = LockState.UNLOCKED;
                return CompletableFuture.completedFuture(state == LockState.UNLOCKED);
            }
            @Override public List<CredentialDescriptor> credentials() { return state == LockState.UNLOCKED ? List.copyOf(descriptors.values()) : List.of(); }
            @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
                if (state != LockState.UNLOCKED && unlockOnRequest) state = LockState.UNLOCKED;
                return CompletableFuture.completedFuture(state == LockState.UNLOCKED ? Optional.ofNullable(secrets.get(id)).map(Supplier::get) : Optional.empty());
            }
            @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) {
                return CompletableFuture.completedFuture(descriptors.values().stream().findFirst());
            }
        };
    }

    @Override public void start(PluginContext context) {
        this.context = context;
        context.services().publishPerConsumer(VaultApi.class, consumer -> api());
    }
}
