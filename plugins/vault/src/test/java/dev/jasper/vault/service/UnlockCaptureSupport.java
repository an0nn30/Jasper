package dev.jasper.vault.service;

import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.nio.file.Path;

/** Initial unlock UI only; all files are synthetic and no password or keychain operation is run. */
public final class UnlockCaptureSupport {
    private UnlockCaptureSupport() { }
    public static UnlockPrompt prompt(Path directory) {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> { throw new IllegalStateException("No keychain in capture"); }, directory.resolve("unused")), new FileStore(directory.resolve("secret")));
        return new UnlockPrompt(null, new LockManager(new VaultFile(directory.resolve("vault.jv")), secrets,
            Runnable::run, Runnable::run, state -> { }), () -> { });
    }
}
