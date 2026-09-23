package dev.jasper.remote;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A stand-in Vault plugin: always unlocked, every credential granted, {@code pick} answers the first descriptor. */
public final class FakeVault implements Plugin {
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of());
    public final Map<UUID, CredentialDescriptor> descriptors = new LinkedHashMap<>();
    public final Map<UUID, java.util.function.Supplier<Credential>> secrets = new LinkedHashMap<>();
    public final List<String> consumers = new ArrayList<>();
    public List<dev.jasper.vault.api.SshKeySource> importedSources = List.of();
    public List<UUID> selectedIds = List.of();
    public CompletableFuture<Optional<dev.jasper.vault.api.SshKeyImportResult>> pendingImport;
    public LockState state = LockState.UNLOCKED;

    public UUID password(String name, String username, String password) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, username, Kind.ACCOUNT_PASSWORD));
        secrets.put(id, () -> new Credential(id, name, Kind.ACCOUNT_PASSWORD, username, password.toCharArray(), null, null));
        return id;
    }

    public UUID key(String name, String fingerprint, java.nio.file.Path privatePath) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, fingerprint, Kind.SSH_KEY));
        secrets.put(id, () -> new Credential(id, name, Kind.SSH_KEY, null, null, privatePath, null));
        return id;
    }

    @Override public void start(PluginContext context) {
        context.services().publishPerConsumer(VaultApi.class, consumer -> {
            consumers.add(consumer.id());
            return new VaultApi() {
                @Override public CompletableFuture<Optional<dev.jasper.vault.api.SshKeyImportResult>> importSshKeys(WindowHandle owner, List<dev.jasper.vault.api.SshKeySource> sources, List<UUID> selected) {
                    importedSources = List.copyOf(sources); selectedIds = List.copyOf(selected);
                    if (pendingImport != null) return pendingImport;
                    return CompletableFuture.completedFuture(Optional.of(new dev.jasper.vault.api.SshKeyImportResult(Map.of(), selected.stream().map(descriptors::get).toList())));
                }
                @Override public LockState lockState() { return state; }
                @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) { return CompletableFuture.completedFuture(state == LockState.UNLOCKED); }
                @Override public List<CredentialDescriptor> credentials() { return state == LockState.UNLOCKED ? List.copyOf(descriptors.values()) : List.of(); }
                @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
                    return CompletableFuture.completedFuture(state == LockState.UNLOCKED ? Optional.ofNullable(secrets.get(id)).map(java.util.function.Supplier::get) : Optional.empty());
                }
                @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) { return CompletableFuture.completedFuture(descriptors.values().stream().findFirst()); }
            };
        });
    }
}
