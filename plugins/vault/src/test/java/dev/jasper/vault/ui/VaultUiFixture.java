package dev.jasper.vault.ui;

import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

final class VaultUiFixture implements AutoCloseable {
    final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    final Executor background = queue::add;
    final LockManager lock;
    final VaultManager manager;
    int changes;

    VaultUiFixture(Path directory) {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> {
            throw new UncheckedIOException(new IOException("No keychain in this test"));
        }, directory.resolve("unused")), new FileStore(directory.resolve("device.secret")));
        lock = new LockManager(new VaultFile(directory.resolve("vault.jv")), secrets,
            background, Runnable::run, state -> { });
        manager = new VaultManager(lock, background, Runnable::run, () -> changes++);
        var create = lock.create("test-password".toCharArray(), false);
        drain();
        create.join();
    }

    void drain() { while (!queue.isEmpty()) queue.removeFirst().run(); }
    @Override public void close() { manager.invalidate(); lock.lock(); drain(); }
}
