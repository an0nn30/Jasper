package dev.jasper.vault.service;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UnlockPromptCancellationTest {
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void cancellingDuringDerivationClosesThePromptAndKeepsTheVaultLocked(boolean cancelFuture, @TempDir Path dir) {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> {
            throw new UncheckedIOException(new IOException("No test keychain"));
        }, dir.resolve("unused")), new FileStore(dir.resolve("secret")));
        var file = new VaultFile(dir.resolve("vault.jv"));
        var created = new LockManager(file, secrets, Runnable::run, Runnable::run, state -> { });
        created.create("test-password".toCharArray(), false).join();
        created.lock();
        var queue = new ArrayDeque<Runnable>();
        var lock = new LockManager(file, secrets, queue::add, Runnable::run, state -> { });
        var prompt = new UnlockPrompt(null, lock, () -> { });
        var pending = prompt.attach();
        int[] dismissed = {0};
        prompt.onDismiss(() -> dismissed[0]++);
        prompt.submit("test-password".toCharArray());
        if (cancelFuture) pending.cancel(false); else prompt.cancel();
        assertThat(dismissed[0]).isEqualTo(1);
        while (!queue.isEmpty()) queue.removeFirst().run();
        assertThat(lock.state()).isEqualTo(LockState.LOCKED);
        if (cancelFuture) assertThat(pending).isCancelled();
        else assertThat(pending).isCompletedWithValue(false);
    }
}
