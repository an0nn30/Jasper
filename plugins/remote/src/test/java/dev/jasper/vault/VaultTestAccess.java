package dev.jasper.vault;
import dev.jasper.vault.store.*;
import java.io.*;
/** Test-only construction: no real OS keychain and no application data. */
public final class VaultTestAccess {
    private VaultTestAccess() {}
    public static VaultPlugin plugin() {
        return new VaultPlugin(context -> new DeviceSecrets(
            new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("unavailable in fixture")); }, context.dataDirectory().resolve("unused")),
            new FileStore(context.dataDirectory().resolve("device.secret"))), Runnable::run);
    }
    public static int managedCount(VaultPlugin plugin) { return plugin.lockManager().vault().managedKeys().size(); }
    public static java.util.Set<String> grantedConsumers(VaultPlugin plugin) {
        return plugin.lockManager().vault().grants().stream().map(dev.jasper.vault.model.Grant::pluginId).collect(java.util.stream.Collectors.toSet());
    }
}
