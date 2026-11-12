package dev.jasper.vault;
import dev.jasper.vault.store.*;
import java.io.*;
/** Test-only construction: no real OS keychain and no application data. */
public final class VaultTestAccess {
    private VaultTestAccess() {}
    public static VaultPlugin plugin() {
        var plugin = new VaultPlugin(context -> new DeviceSecrets(
            new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("unavailable in fixture")); }, context.dataDirectory().resolve("unused")),
            new FileStore(context.dataDirectory().resolve("device.secret"))), Runnable::run);
        // Browser plan 8's L20: the anchored picker ignores a choice for 500 ms after it shows; these tests choose at
        // once, so its clock steps a second each time it is read.
        var seconds = new java.util.concurrent.atomic.AtomicLong();
        plugin.pickerClock = () -> seconds.addAndGet(1_000_000_000L);
        return plugin;
    }
    public static int managedCount(VaultPlugin plugin) { return plugin.lockManager().vault().managedKeys().size(); }
    public static java.util.Set<String> grantedConsumers(VaultPlugin plugin) {
        return plugin.lockManager().vault().grants().stream().map(dev.jasper.vault.model.Grant::pluginId).collect(java.util.stream.Collectors.toSet());
    }
}
