package dev.jasper.vault.lock;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.crypto.VaultFileFormat;
import dev.jasper.vault.crypto.WrongPasswordException;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LockManagerTest {
    final Deque<Runnable> background = new ArrayDeque<>();
    final Executor queued = background::add;
    final List<LockState> states = new ArrayList<>();

    /** Device secrets with no keychain: the file store under {@code dir}. */
    static DeviceSecrets secrets(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("no secret-tool")); }, dir.resolve("x"));
        return new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret")));
    }

    LockManager manager(Path dir) { return new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets(dir), queued, Runnable::run, states::add); }

    void runBackground() { while (!background.isEmpty()) background.poll().run(); }

    static Throwable cause(CompletableFuture<?> future) {
        try { future.join(); return null; }
        catch (CompletionException failure) { return failure.getCause(); }
    }

    @Test void createsLocksAndUnlocksWithTheEdits(@TempDir Path dir) throws Exception {
        LockManager manager = manager(dir);
        assertThat(manager.state()).isEqualTo(LockState.NO_VAULT);
        assertThatThrownBy(manager::vault).isInstanceOf(IllegalStateException.class);
        char[] password = "hunter2".toCharArray();
        CompletableFuture<Void> created = manager.create(password, true);
        assertThat(manager.state()).as("nothing before the background work").isEqualTo(LockState.NO_VAULT);
        runBackground();
        assertThat(created).isCompleted();
        assertThat(password).as("the manager zeroes what it was given").containsOnly((char) 0);
        assertThat(manager.state()).isEqualTo(LockState.UNLOCKED);
        assertThat(manager.bound()).isTrue();
        assertThat(states).containsExactly(LockState.UNLOCKED);
        assertThat(manager.deviceSecretSource()).isEqualTo("file (no keychain found)");
        byte[] first = new VaultFile(dir.resolve("vault.jv")).read();
        assertThat(VaultFileFormat.parse(first).header().bound()).isTrue();

        UUID id = UUID.randomUUID();
        manager.vault().accounts().add(new Account(id, "prod", "deploy", new Auth.Password("pw".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        CompletableFuture<Void> saved = manager.save();
        runBackground();
        assertThat(saved).isCompleted();
        byte[] second = new VaultFile(dir.resolve("vault.jv")).read();
        assertThat(VaultFileFormat.parse(second).header().nonce()).as("a fresh nonce per write").isNotEqualTo(VaultFileFormat.parse(first).header().nonce());
        assertThat(VaultFileFormat.parse(second).header().salt()).as("the salt lasts the session").isEqualTo(VaultFileFormat.parse(first).header().salt());

        char[] secret = ((Auth.Password) manager.vault().accounts().getFirst().auth()).password();
        manager.lock();
        assertThat(manager.state()).isEqualTo(LockState.LOCKED);
        assertThat(secret).containsOnly((char) 0);
        assertThat(states).containsExactly(LockState.UNLOCKED, LockState.LOCKED);
        manager.lock();
        assertThat(states).as("locking twice publishes once").hasSize(2);

        CompletableFuture<Void> wrong = manager.unlock("nope".toCharArray());
        runBackground();
        assertThat(cause(wrong)).isInstanceOf(WrongPasswordException.class);
        assertThat(manager.state()).isEqualTo(LockState.LOCKED);
        CompletableFuture<Void> unlocked = manager.unlock("hunter2".toCharArray());
        runBackground();
        assertThat(unlocked).isCompleted();
        assertThat(manager.vault().account(id)).isPresent();
        assertThat(((Auth.Password) manager.vault().account(id).get().auth()).password()).isEqualTo("pw".toCharArray());
    }

    @Test void aBoundVaultWithoutItsDeviceSecretIsForeign(@TempDir Path dir) throws Exception {
        LockManager here = manager(dir);
        here.create("pw".toCharArray(), true);
        runBackground();
        var elsewhere = new LockManager(new VaultFile(dir.resolve("vault.jv")), secrets(dir.resolve("other")), queued, Runnable::run, states::add);
        CompletableFuture<Void> attempt = elsewhere.unlock("pw".toCharArray());
        runBackground();
        assertThat(cause(attempt)).isInstanceOf(ForeignDeviceException.class).hasMessageContaining("another machine");

        LockManager portable = manager(dir.resolve("portable"));
        portable.create("pw".toCharArray(), false);
        runBackground();
        assertThat(portable.bound()).isFalse();
        var anywhere = new LockManager(new VaultFile(dir.resolve("portable/vault.jv")), secrets(dir.resolve("elsewhere")), queued, Runnable::run, states::add);
        CompletableFuture<Void> opened = anywhere.unlock("pw".toCharArray());
        runBackground();
        assertThat(opened).isCompleted();
    }

    @Test void changePasswordChecksTheOldOneAndRekeys(@TempDir Path dir) throws Exception {
        LockManager manager = manager(dir);
        manager.create("old".toCharArray(), true);
        runBackground();
        byte[] before = new VaultFile(dir.resolve("vault.jv")).read();
        CompletableFuture<Void> refused = manager.changePassword("wrong".toCharArray(), "new".toCharArray());
        runBackground();
        assertThat(cause(refused)).isInstanceOf(WrongPasswordException.class);
        CompletableFuture<Void> changed = manager.changePassword("old".toCharArray(), "new".toCharArray());
        runBackground();
        assertThat(changed).isCompleted();
        assertThat(VaultFileFormat.parse(new VaultFile(dir.resolve("vault.jv")).read()).header().salt()).isNotEqualTo(VaultFileFormat.parse(before).header().salt());
        manager.lock();
        CompletableFuture<Void> old = manager.unlock("old".toCharArray());
        runBackground();
        assertThat(cause(old)).isInstanceOf(WrongPasswordException.class);
        CompletableFuture<Void> fresh = manager.unlock("new".toCharArray());
        runBackground();
        assertThat(fresh).isCompleted();
    }

    @Test void lockingWhileUnlockIsPendingDiscardsItsResult(@TempDir Path dir) {
        LockManager manager = manager(dir);
        manager.create("pw".toCharArray(), false);
        runBackground();
        manager.lock();
        CompletableFuture<Void> pending = manager.unlock("pw".toCharArray());
        manager.lock();
        runBackground();
        assertThat(manager.state()).isEqualTo(LockState.LOCKED);
        assertThat(pending).isCompletedExceptionally();
    }

    @Test void failedPasswordChangeDoesNotRekeyAnOrdinaryQueuedSave(@TempDir Path dir) throws Exception {
        LockManager manager = manager(dir);
        manager.create("old-password".toCharArray(), false); runBackground();
        Path obstruction = java.nio.file.Files.createDirectory(dir.resolve("vault.jv.tmp"));
        var changed = manager.changePassword("old-password".toCharArray(), "new-password".toCharArray());
        background.removeFirst().run(); // Derive and queue the rekey write.
        var saved = manager.save(); // Must use the final key, including rollback on failure.
        background.removeFirst().run(); // Fail the rekey write, leaving the original vault intact.
        assertThat(changed).isCompletedExceptionally();
        java.nio.file.Files.delete(obstruction);
        runBackground(); saved.join();
        manager.lock();
        var reopened = manager.unlock("old-password".toCharArray()); runBackground();
        assertThat(reopened).isCompletedWithValue(null);
    }

    @Test void lockingDuringCreationDoesNotInstallOrWriteTheVault(@TempDir Path dir) {
        LockManager manager = manager(dir);
        CompletableFuture<Void> pending = manager.create("pw".toCharArray(), false);
        manager.lock();
        runBackground();
        assertThat(manager.state()).isEqualTo(LockState.NO_VAULT);
        assertThat(pending).isCompletedExceptionally();
    }

    @Test void guardsTheStateMachine(@TempDir Path dir) {
        LockManager manager = manager(dir);
        assertThat(cause(manager.unlock("pw".toCharArray()))).isInstanceOf(IllegalStateException.class);
        assertThat(cause(manager.save())).isInstanceOf(IllegalStateException.class);
        assertThat(cause(manager.changePassword("a".toCharArray(), "b".toCharArray()))).isInstanceOf(IllegalStateException.class);
        manager.create("pw".toCharArray(), false);
        runBackground();
        assertThat(cause(manager.create("pw".toCharArray(), false))).isInstanceOf(IllegalStateException.class);
    }
}
