package dev.jasper.vault.service;

import dev.jasper.vault.api.*;
import dev.jasper.vault.keygen.*;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.ManagedSshKey;
import dev.jasper.vault.store.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Worker reads, UI delivery and durable writes can each be held independently. */
class KeyImportInterleavingTest {
    @TempDir Path directory;
    final ArrayDeque<Runnable> reads = new ArrayDeque<>(), writes = new ArrayDeque<>(), ui = new ArrayDeque<>();
    final List<KeyImportPrompt> prompts = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    LockManager lock;
    VaultService service;
    VaultApi api;
    final dev.jasper.sdk.terminal.WindowHandle owner = VaultServiceTest.window();
    void setup() {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> { throw new java.io.UncheckedIOException(new java.io.IOException("fixture")); }, directory.resolve("unused")), new FileStore(directory.resolve("device.secret")));
        lock = new LockManager(new VaultFile(directory.resolve("vault.jv")), secrets, writes::add, ui::add,
            state -> { if (service != null) service.lockStateChanged(state); });
        var created = lock.create("pw".toCharArray(), false); drain(writes); created.join();
        service = new VaultService(lock, () -> Optional.of(owner), p -> {}, p -> {}, p -> {}, notices::add,
            reads::add, ui::add, w -> CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED), prompts::add, () -> {});
        api = service.forConsumer(VaultServiceTest.SSH);
    }
    void flushUi() { while (!ui.isEmpty()) ui.removeFirst().run(); }
    void drain(ArrayDeque<Runnable> workers) {
        do { while (!workers.isEmpty()) workers.removeFirst().run(); flushUi(); } while (!workers.isEmpty());
    }
    @Test void cancellationAfterReadBeforeUiDeliveryWipesDiscardedBytesAndPhrase() throws Exception {
        setup();
        var key = new KeyGenerator(directory.resolve("keys")).generate(KeyAlgorithm.ED25519, "fixture", "test", "phrase".toCharArray());
        var result = api.importSshKeys(owner, List.of(new SshKeySource(UUID.randomUUID(), "fixture", key.privatePath())), List.of());
        flushUi(); drain(reads); assertThat(prompts.getFirst().phase()).isEqualTo(KeyImportPrompt.Phase.PASSPHRASE);
        prompts.getFirst().submitPassphrase("phrase".toCharArray());
        reads.removeFirst().run(); // parsed successfully; deliberately do not deliver the UI callback
        assertThat(ui).hasSize(1);
        // Test-only ownership observation. Production exposes no prepared secret accessor.
        PreparedKey pending = null;
        for (var field : ui.getFirst().getClass().getDeclaredFields()) if (field.getType() == PreparedKey.class) {
            field.setAccessible(true); pending = (PreparedKey) field.get(ui.getFirst());
        }
        assertThat(pending).isNotNull();
        var owned = PreparedKey.class.getDeclaredField("material"); owned.setAccessible(true);
        var material = (ManagedSshKey) owned.get(pending);
        byte[] bytes = material.privateKey(); char[] phrase = material.passphrase();
        assertThat(bytes).containsAnyOf((byte) '-'); assertThat(phrase).isEqualTo("phrase".toCharArray());
        prompts.getFirst().cancel(); flushUi();
        assertThat(bytes).containsOnly((byte) 0); assertThat(phrase).containsOnly((char) 0);
        assertThat(result.join()).isEmpty(); assertThat(writes).isEmpty();
        assertThat(lock.vault().managedKeys()).isEmpty(); assertThat(lock.vault().grants()).isEmpty(); lock.lock();
    }
    @Test void secondImportWaitsForFirstDurableWriteAndReusesItsIdentity() throws Exception {
        setup();
        var key = new KeyGenerator(directory.resolve("keys")).generate(KeyAlgorithm.ED25519, "fixture", "test");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var first = api.importSshKeys(owner, List.of(new SshKeySource(a, "one", key.privatePath())), List.of());
        var second = api.importSshKeys(owner, List.of(new SshKeySource(b, "two", key.privatePath())), List.of());
        flushUi(); drain(reads);
        prompts.get(0).commit(); prompts.get(1).commit();
        assertThat(first).isNotDone(); assertThat(second).isNotDone(); assertThat(writes).hasSize(1);
        assertThat(lock.vault().managedKeys()).isEmpty();
        writes.removeFirst().run(); flushUi();
        assertThat(first).isDone(); assertThat(second).isNotDone(); assertThat(writes).hasSize(1);
        assertThat(lock.vault().managedKeys()).hasSize(1);
        drain(writes);
        assertThat(first.join().orElseThrow().keys().get(a).id()).isEqualTo(second.join().orElseThrow().keys().get(b).id());
        assertThat(lock.vault().managedKeys()).hasSize(1); assertThat(lock.vault().grants()).hasSize(1);
        lock.lock(); var unlocked = lock.unlock("pw".toCharArray()); drain(writes); unlocked.join();
        assertThat(lock.vault().managedKeys()).hasSize(1); lock.lock();
    }
}
